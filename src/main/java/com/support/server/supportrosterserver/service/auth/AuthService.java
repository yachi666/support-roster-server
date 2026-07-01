package com.support.server.supportrosterserver.service.auth;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.extern.slf4j.Slf4j;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.support.server.supportrosterserver.auth.AccountRole;
import com.support.server.supportrosterserver.auth.AccountStatus;
import com.support.server.supportrosterserver.auth.AuthenticatedAccount;
import com.support.server.supportrosterserver.dto.auth.AuthActivateRequest;
import com.support.server.supportrosterserver.dto.auth.AuthChangePasswordRequest;
import com.support.server.supportrosterserver.dto.auth.AuthCurrentTeamDto;
import com.support.server.supportrosterserver.dto.auth.AuthCurrentUserDto;
import com.support.server.supportrosterserver.dto.auth.AuthLoginRequest;
import com.support.server.supportrosterserver.dto.auth.AuthLoginResponse;
import com.support.server.supportrosterserver.entity.auth.WorkspaceAccountEntity;
import com.support.server.supportrosterserver.entity.auth.WorkspaceAccountTeamScopeEntity;
import com.support.server.supportrosterserver.entity.workspace.StaffEntity;
import com.support.server.supportrosterserver.entity.workspace.TeamEntity;
import com.support.server.supportrosterserver.exception.BadRequestException;
import com.support.server.supportrosterserver.exception.ForbiddenException;
import com.support.server.supportrosterserver.exception.ResourceNotFoundException;
import com.support.server.supportrosterserver.mapper.StaffMapper;
import com.support.server.supportrosterserver.mapper.WorkspaceAccountMapper;
import com.support.server.supportrosterserver.mapper.WorkspaceAccountTeamScopeMapper;
import com.support.server.supportrosterserver.service.workspace.WorkspaceLookupService;
import com.support.server.supportrosterserver.service.workspace.WorkspaceOperationLogService;

import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final WorkspaceAccountMapper workspaceAccountMapper;
    private final StaffMapper staffMapper;
    private final WorkspaceAccountTeamScopeMapper workspaceAccountTeamScopeMapper;
    private final AuthContextService authContextService;
    private final AuthTokenVersionService authTokenVersionService;
    private final WorkspaceOperationLogService workspaceOperationLogService;
    private final WorkspaceLookupService workspaceLookupService;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public AuthLoginResponse login(AuthLoginRequest request) {
        String normalizedStaffId = normalizeStaffId(request.getStaffId());
        WorkspaceAccountEntity account = requireAccountByStaffId(normalizedStaffId);

        if (AccountStatus.PENDING_ACTIVATION.getCode().equalsIgnoreCase(account.getAccountStatus())) {
            throw new BadRequestException("Account password has not been initialized. Please use first-time activation.");
        }

        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new BadRequestException("Password is required.");
        }
        if (account.getPasswordHash() == null || account.getPasswordHash().isBlank() || !passwordEncoder.matches(request.getPassword(), account.getPasswordHash())) {
            throw new BadRequestException("Staff ID or password is incorrect.");
        }

        return establishSession(account, "Login succeeded via local password");
    }

    @Transactional
    public AuthLoginResponse activate(AuthActivateRequest request) {
        String normalizedStaffId = normalizeStaffId(request.getStaffId());
        validateNewPassword(request.getNewPassword());

        // Query ALL accounts including soft-deleted ones (bypass @TableLogic)
        WorkspaceAccountEntity existingAccount = workspaceAccountMapper.selectAnyByStaffId(normalizedStaffId);

        if (existingAccount != null) {
            // Fix 1: reject soft-deleted accounts (offboarding bypass)
            if (existingAccount.getDeleted() != null && existingAccount.getDeleted() == 1) {
                throw new BadRequestException("Account was previously deactivated. Please contact an administrator.");
            }

            // Existing account flow
            if (AccountStatus.DISABLED.getCode().equalsIgnoreCase(existingAccount.getAccountStatus())) {
                throw new ForbiddenException("Account is disabled.");
            }
            if (!AccountStatus.PENDING_ACTIVATION.getCode().equalsIgnoreCase(existingAccount.getAccountStatus())) {
                throw new BadRequestException("Password has already been initialized. Please sign in.");
            }

            // Activate existing pending account
            existingAccount.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
            existingAccount.setPasswordSetAt(LocalDateTime.now());
            existingAccount.setAccountStatus(AccountStatus.ACTIVE.getCode());
            workspaceAccountMapper.updateById(existingAccount);
            workspaceOperationLogService.log(normalizedStaffId, "Activate workspace account",
                "workspace_account", existingAccount.getId(), "First-login password setup completed");

            return establishSession(existingAccount, "Login succeeded via first-time activation");
        }

        // Self-registration: no account exists — auto-create from staff record
        StaffEntity staff = staffMapper.selectOne(
            Wrappers.<StaffEntity>lambdaQuery()
                .eq(StaffEntity::getStaffId, normalizedStaffId)
                .last("limit 1"));
        if (staff == null) {
            throw new BadRequestException("No staff record found for the provided staff ID.");
        }

        // Fix 3: reject inactive staff
        if (staff.getStatus() != null && !"Active".equalsIgnoreCase(staff.getStatus())) {
            throw new BadRequestException("Staff member is not active. Please contact an administrator.");
        }

        WorkspaceAccountEntity newAccount = new WorkspaceAccountEntity();
        newAccount.setStaffRecordId(staff.getId());
        newAccount.setStaffId(normalizedStaffId);
        newAccount.setRoleCode(AccountRole.EDITOR.getCode());
        newAccount.setAccountStatus(AccountStatus.ACTIVE.getCode());
        newAccount.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        newAccount.setPasswordSetAt(LocalDateTime.now());
        newAccount.setAuthSource("self-registered");
        newAccount.setTokenVersion(1L);

        // Fix 2: handle race condition — concurrent activation for same staffId
        try {
            workspaceAccountMapper.insert(newAccount);
        } catch (DataIntegrityViolationException e) {
            log.warn("Data integrity violation during self-registration for staffId: {}", normalizedStaffId, e);
            throw new BadRequestException("Account was already created. Please sign in.");
        }

        // Auto-grant editor access to the staff's own team
        if (staff.getTeamId() != null) {
            WorkspaceAccountTeamScopeEntity teamScope = new WorkspaceAccountTeamScopeEntity();
            teamScope.setAccountId(newAccount.getId());
            teamScope.setTeamId(staff.getTeamId());
            workspaceAccountTeamScopeMapper.insert(teamScope);
        }

        workspaceOperationLogService.log(normalizedStaffId, "Self-register workspace account",
            "workspace_account", newAccount.getId(),
            "Account auto-created with editor role scoped to own team");

        return establishSession(newAccount, "Login succeeded via self-registration");
    }

    public AuthCurrentUserDto getCurrentUser() {
        return toCurrentUserDto(authContextService.requireLogin());
    }

    @Transactional
    public void logout() {
        AuthenticatedAccount current = authContextService.requireLogin();
        WorkspaceAccountEntity account = workspaceAccountMapper.selectById(current.accountId());
        if (account == null) {
            throw new ResourceNotFoundException("WorkspaceAccount", "id", current.accountId());
        }
        authTokenVersionService.bumpTokenVersion(account);
        workspaceAccountMapper.updateById(account);
        workspaceOperationLogService.log(current.staffName(), "Logout workspace", "workspace_account", current.accountId(), "Manual logout");
        StpUtil.logout();
    }

    @Transactional
    public void changePassword(AuthChangePasswordRequest request) {
        AuthenticatedAccount current = authContextService.requireLogin();
        WorkspaceAccountEntity account = workspaceAccountMapper.selectById(current.accountId());
        if (account == null) {
            throw new ResourceNotFoundException("WorkspaceAccount", "id", current.accountId());
        }
        if (account.getPasswordHash() == null || !passwordEncoder.matches(request.getCurrentPassword(), account.getPasswordHash())) {
            throw new BadRequestException("Current password is incorrect.");
        }
        validateNewPassword(request.getNewPassword());
        account.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        account.setPasswordSetAt(LocalDateTime.now());
        account.setAccountStatus(AccountStatus.ACTIVE.getCode());
        authTokenVersionService.bumpTokenVersion(account);
        workspaceAccountMapper.updateById(account);
        workspaceOperationLogService.log(current.staffName(), "Change password", "workspace_account", current.accountId(), "Password updated by current user");
        StpUtil.logout();
    }

    private AuthCurrentUserDto toCurrentUserDto(AuthenticatedAccount current) {
        StaffEntity staff = staffMapper.selectById(current.staffRecordId());
        if (staff == null) {
            throw new ResourceNotFoundException("Staff", "id", current.staffRecordId());
        }
        List<Long> editableTeamIds = authContextService.editableTeamIds();
        List<AuthCurrentTeamDto> editableTeams = (current.isAdmin()
            ? workspaceLookupService.listTeams().stream()
            : current.teamScopes().stream())
            .map(this::toTeamDto)
            .toList();

        return new AuthCurrentUserDto(
            current.accountId(),
            current.staffRecordId(),
            current.staffId(),
            current.staffName(),
            current.roleCode(),
            current.accountStatus(),
            current.authSource(),
            authContextService.permissionCodes(current),
            editableTeamIds.stream().map(String::valueOf).toList(),
            editableTeams
        );
    }

    private AuthCurrentTeamDto toTeamDto(TeamEntity team) {
        return new AuthCurrentTeamDto(team.getId(), team.getName(), team.getColor());
    }

    private void validateNewPassword(String password) {
        if (password == null || password.isBlank()) {
            throw new BadRequestException("New password is required.");
        }
        if (password.length() < 4) {
            throw new BadRequestException("Password must be at least 4 characters.");
        }
        if (password.length() > 64) {
            throw new BadRequestException("Password must be 64 characters or fewer.");
        }
    }

    private String normalizeStaffId(String staffId) {
        if (staffId == null || staffId.isBlank()) {
            throw new BadRequestException("Staff ID is required.");
        }
        return staffId.trim();
    }

    private WorkspaceAccountEntity requireAccountByStaffId(String normalizedStaffId) {
        WorkspaceAccountEntity account = workspaceAccountMapper.selectOne(Wrappers.<WorkspaceAccountEntity>lambdaQuery()
            .eq(WorkspaceAccountEntity::getStaffId, normalizedStaffId)
            .last("limit 1"));
        if (account == null) {
            throw new BadRequestException("Account does not exist for the provided staff ID.");
        }
        if (AccountStatus.DISABLED.getCode().equalsIgnoreCase(account.getAccountStatus())) {
            throw new ForbiddenException("Account is disabled.");
        }
        return account;
    }

    private AuthLoginResponse establishSession(WorkspaceAccountEntity account, String loginDetail) {
        StpUtil.login(account.getId(), authTokenVersionService.createLoginModel(account));
        account.setLastLoginAt(LocalDateTime.now());
        workspaceAccountMapper.updateById(account);

        AuthCurrentUserDto currentUser = getCurrentUser();
        workspaceOperationLogService.log(currentUser.getStaffName(), "Login workspace", "workspace_account", account.getId(), loginDetail);
        return new AuthLoginResponse("Bearer " + StpUtil.getTokenValue(), currentUser);
    }
}
