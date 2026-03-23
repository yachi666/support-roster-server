package com.support.server.supportrosterserver.service.auth;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.support.server.supportrosterserver.auth.AccountStatus;
import com.support.server.supportrosterserver.auth.AuthenticatedAccount;
import com.support.server.supportrosterserver.dto.auth.AuthActivateRequest;
import com.support.server.supportrosterserver.dto.auth.AuthChangePasswordRequest;
import com.support.server.supportrosterserver.dto.auth.AuthCurrentTeamDto;
import com.support.server.supportrosterserver.dto.auth.AuthCurrentUserDto;
import com.support.server.supportrosterserver.dto.auth.AuthLoginRequest;
import com.support.server.supportrosterserver.dto.auth.AuthLoginResponse;
import com.support.server.supportrosterserver.entity.auth.WorkspaceAccountEntity;
import com.support.server.supportrosterserver.entity.workspace.StaffEntity;
import com.support.server.supportrosterserver.entity.workspace.TeamEntity;
import com.support.server.supportrosterserver.exception.BadRequestException;
import com.support.server.supportrosterserver.exception.ForbiddenException;
import com.support.server.supportrosterserver.exception.ResourceNotFoundException;
import com.support.server.supportrosterserver.mapper.StaffMapper;
import com.support.server.supportrosterserver.mapper.WorkspaceAccountMapper;
import com.support.server.supportrosterserver.service.workspace.WorkspaceLookupService;
import com.support.server.supportrosterserver.service.workspace.WorkspaceOperationLogService;

import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String AUTH_SOURCE_LOCAL_PASSWORD = "LOCAL_PASSWORD";

    private final WorkspaceAccountMapper workspaceAccountMapper;
    private final StaffMapper staffMapper;
    private final AuthContextService authContextService;
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
        WorkspaceAccountEntity account = requireAccountByStaffId(normalizedStaffId);

        if (!AccountStatus.PENDING_ACTIVATION.getCode().equalsIgnoreCase(account.getAccountStatus())) {
            throw new BadRequestException("Password has already been initialized. Please sign in.");
        }

        validateNewPassword(request.getNewPassword());
        account.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        account.setPasswordSetAt(LocalDateTime.now());
        account.setAccountStatus(AccountStatus.ACTIVE.getCode());
        workspaceAccountMapper.updateById(account);
        workspaceOperationLogService.log(normalizedStaffId, "Activate workspace account", "workspace_account", account.getId(), "First-login password setup completed");

        return establishSession(account, "Login succeeded via first-time activation");
    }

    public AuthCurrentUserDto getCurrentUser() {
        return toCurrentUserDto(authContextService.requireLogin());
    }

    @Transactional
    public void logout() {
        AuthenticatedAccount current = authContextService.requireLogin();
        workspaceOperationLogService.log(current.staffName(), "Logout workspace", "workspace_account", current.accountId(), "Manual logout");
        StpUtil.logout(current.accountId());
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
        workspaceAccountMapper.updateById(account);
        workspaceOperationLogService.log(current.staffName(), "Change password", "workspace_account", current.accountId(), "Password updated by current user");
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
            current.staffCode(),
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
        if (password.length() < 8) {
            throw new BadRequestException("Password must be at least 8 characters.");
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
            .eq(WorkspaceAccountEntity::getStaffCode, normalizedStaffId)
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
        StpUtil.login(account.getId());
        account.setLastLoginAt(LocalDateTime.now());
        workspaceAccountMapper.updateById(account);

        AuthCurrentUserDto currentUser = getCurrentUser();
        workspaceOperationLogService.log(currentUser.getStaffName(), "Login workspace", "workspace_account", account.getId(), loginDetail);
        return new AuthLoginResponse("Bearer " + StpUtil.getTokenValue(), currentUser);
    }
}
