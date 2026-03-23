package com.support.server.supportrosterserver.service.workspace;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.support.server.supportrosterserver.auth.AccountRole;
import com.support.server.supportrosterserver.auth.AccountStatus;
import com.support.server.supportrosterserver.auth.AuthenticatedAccount;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceAccountDto;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceAccountScopeDto;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceAccountUpsertRequest;
import com.support.server.supportrosterserver.entity.auth.WorkspaceAccountEntity;
import com.support.server.supportrosterserver.entity.auth.WorkspaceAccountTeamScopeEntity;
import com.support.server.supportrosterserver.entity.workspace.StaffEntity;
import com.support.server.supportrosterserver.entity.workspace.TeamEntity;
import com.support.server.supportrosterserver.exception.BadRequestException;
import com.support.server.supportrosterserver.exception.ResourceNotFoundException;
import com.support.server.supportrosterserver.mapper.StaffMapper;
import com.support.server.supportrosterserver.mapper.WorkspaceAccountMapper;
import com.support.server.supportrosterserver.mapper.WorkspaceAccountTeamScopeMapper;
import com.support.server.supportrosterserver.service.auth.AuthContextService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WorkspaceAccountService {

    private static final String AUTH_SOURCE_LOCAL_PASSWORD = "LOCAL_PASSWORD";

    private final WorkspaceAccountMapper workspaceAccountMapper;
    private final WorkspaceAccountTeamScopeMapper workspaceAccountTeamScopeMapper;
    private final StaffMapper staffMapper;
    private final WorkspaceLookupService workspaceLookupService;
    private final AuthContextService authContextService;
    private final WorkspaceOperationLogService workspaceOperationLogService;

    public List<WorkspaceAccountDto> listAccounts(String keyword) {
        authContextService.requireAdmin();
        List<WorkspaceAccountEntity> accounts = workspaceAccountMapper.selectList(Wrappers.<WorkspaceAccountEntity>lambdaQuery()
            .orderByAsc(WorkspaceAccountEntity::getStaffCode));
        if (accounts.isEmpty()) {
            return List.of();
        }
        Map<Long, StaffEntity> staffById = staffMapper.selectBatchIds(accounts.stream().map(WorkspaceAccountEntity::getStaffId).toList())
            .stream()
            .collect(Collectors.toMap(StaffEntity::getId, staff -> staff));
        Map<Long, List<TeamEntity>> teamsByAccountId = loadTeamsByAccountId(accounts.stream().map(WorkspaceAccountEntity::getId).toList());

        return accounts.stream()
            .filter(account -> matchesKeyword(account, staffById.get(account.getStaffId()), keyword))
            .map(account -> toDto(account, staffById.get(account.getStaffId()), teamsByAccountId.getOrDefault(account.getId(), List.of())))
            .toList();
    }

    public WorkspaceAccountDto getAccount(Long id) {
        authContextService.requireAdmin();
        WorkspaceAccountEntity account = requireAccount(id);
        StaffEntity staff = requireStaff(account.getStaffId());
        List<TeamEntity> teams = loadTeamsByAccountId(List.of(id)).getOrDefault(id, List.of());
        return toDto(account, staff, teams);
    }

    @Transactional
    public WorkspaceAccountDto createAccount(WorkspaceAccountUpsertRequest request) {
        authContextService.requireAdmin();
        if (request.getStaffRecordId() == null) {
            throw new BadRequestException("Staff selection is required.");
        }
        StaffEntity staff = requireStaff(request.getStaffRecordId());
        WorkspaceAccountEntity existing = workspaceAccountMapper.selectOne(Wrappers.<WorkspaceAccountEntity>lambdaQuery()
            .eq(WorkspaceAccountEntity::getStaffId, request.getStaffRecordId())
            .last("limit 1"));
        if (existing != null) {
            throw new BadRequestException("The selected staff already has an account.");
        }

        AccountRole role = resolveRole(request.getRoleCode());
        List<Long> editableTeamIds = normalizeEditableTeamIds(role, request.getEditableTeamIds());

        WorkspaceAccountEntity account = new WorkspaceAccountEntity();
        account.setStaffId(staff.getId());
        account.setStaffCode(staff.getStaffCode());
        account.setRoleCode(role.getCode());
        account.setAccountStatus(AccountStatus.PENDING_ACTIVATION.getCode());
        account.setPasswordHash(null);
        account.setPasswordSetAt(null);
        account.setAuthSource(AUTH_SOURCE_LOCAL_PASSWORD);
        account.setExternalSubject(null);
        account.setNotes(normalizeNotes(request.getNotes()));
        account.setLastLoginAt(null);
        workspaceAccountMapper.insert(account);
        replaceTeamScopes(account.getId(), editableTeamIds);

        AuthenticatedAccount current = authContextService.requireLogin();
        workspaceOperationLogService.log(current.staffName(), "Create workspace account", "workspace_account", account.getId(), "Role=" + role.getCode());
        return getAccount(account.getId());
    }

    @Transactional
    public WorkspaceAccountDto updateAccount(Long id, WorkspaceAccountUpsertRequest request) {
        authContextService.requireAdmin();
        WorkspaceAccountEntity account = requireAccount(id);
        if (request.getStaffRecordId() != null && !request.getStaffRecordId().equals(account.getStaffId())) {
            throw new BadRequestException("Changing the linked staff record is not supported.");
        }

        AccountRole role = resolveRole(request.getRoleCode());
        List<Long> editableTeamIds = normalizeEditableTeamIds(role, request.getEditableTeamIds());
        account.setRoleCode(role.getCode());
        account.setNotes(normalizeNotes(request.getNotes()));
        workspaceAccountMapper.updateById(account);
        replaceTeamScopes(account.getId(), editableTeamIds);

        AuthenticatedAccount current = authContextService.requireLogin();
        workspaceOperationLogService.log(current.staffName(), "Update workspace account", "workspace_account", account.getId(), "Role=" + role.getCode());
        return getAccount(id);
    }

    @Transactional
    public void resetPassword(Long id) {
        authContextService.requireAdmin();
        WorkspaceAccountEntity account = requireAccount(id);
        account.setPasswordHash(null);
        account.setPasswordSetAt(null);
        account.setAccountStatus(AccountStatus.PENDING_ACTIVATION.getCode());
        account.setLastLoginAt(null);
        workspaceAccountMapper.updateById(account);
        AuthenticatedAccount current = authContextService.requireLogin();
        workspaceOperationLogService.log(current.staffName(), "Reset workspace password", "workspace_account", account.getId(), "Account moved to pending activation");
    }

    @Transactional
    public void enableAccount(Long id) {
        authContextService.requireAdmin();
        WorkspaceAccountEntity account = requireAccount(id);
        account.setAccountStatus(account.getPasswordHash() == null || account.getPasswordHash().isBlank()
            ? AccountStatus.PENDING_ACTIVATION.getCode()
            : AccountStatus.ACTIVE.getCode());
        workspaceAccountMapper.updateById(account);
        AuthenticatedAccount current = authContextService.requireLogin();
        workspaceOperationLogService.log(current.staffName(), "Enable workspace account", "workspace_account", account.getId(), "Account enabled");
    }

    @Transactional
    public void disableAccount(Long id) {
        authContextService.requireAdmin();
        WorkspaceAccountEntity account = requireAccount(id);
        account.setAccountStatus(AccountStatus.DISABLED.getCode());
        workspaceAccountMapper.updateById(account);
        AuthenticatedAccount current = authContextService.requireLogin();
        workspaceOperationLogService.log(current.staffName(), "Disable workspace account", "workspace_account", account.getId(), "Account disabled");
    }

    private WorkspaceAccountEntity requireAccount(Long id) {
        WorkspaceAccountEntity entity = workspaceAccountMapper.selectById(id);
        if (entity == null) {
            throw new ResourceNotFoundException("WorkspaceAccount", "id", id);
        }
        return entity;
    }

    private StaffEntity requireStaff(Long id) {
        StaffEntity entity = staffMapper.selectById(id);
        if (entity == null) {
            throw new ResourceNotFoundException("Staff", "id", id);
        }
        return entity;
    }

    private AccountRole resolveRole(String roleCode) {
        if (roleCode == null || roleCode.isBlank()) {
            throw new BadRequestException("Role is required.");
        }
        return AccountRole.fromCode(roleCode);
    }

    private List<Long> normalizeEditableTeamIds(AccountRole role, List<Long> teamIds) {
        if (role != AccountRole.EDITOR) {
            return List.of();
        }
        List<Long> normalized = teamIds == null ? List.of() : teamIds.stream()
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        if (normalized.isEmpty()) {
            throw new BadRequestException("Editors must have at least one editable team.");
        }
        normalized.forEach(workspaceLookupService::requireTeam);
        return normalized;
    }

    private void replaceTeamScopes(Long accountId, List<Long> editableTeamIds) {
        workspaceAccountTeamScopeMapper.delete(Wrappers.<WorkspaceAccountTeamScopeEntity>lambdaQuery()
            .eq(WorkspaceAccountTeamScopeEntity::getAccountId, accountId));
        for (Long teamId : editableTeamIds) {
            WorkspaceAccountTeamScopeEntity scope = new WorkspaceAccountTeamScopeEntity();
            scope.setAccountId(accountId);
            scope.setTeamId(teamId);
            workspaceAccountTeamScopeMapper.insert(scope);
        }
    }

    private Map<Long, List<TeamEntity>> loadTeamsByAccountId(List<Long> accountIds) {
        if (accountIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, TeamEntity> teamMap = workspaceLookupService.teamMap();
        Map<Long, List<TeamEntity>> result = new LinkedHashMap<>();
        for (WorkspaceAccountTeamScopeEntity scope : workspaceAccountTeamScopeMapper.selectList(Wrappers.<WorkspaceAccountTeamScopeEntity>lambdaQuery()
                .in(WorkspaceAccountTeamScopeEntity::getAccountId, accountIds)
                .orderByAsc(WorkspaceAccountTeamScopeEntity::getTeamId))) {
            TeamEntity team = teamMap.get(scope.getTeamId());
            if (team == null) {
                continue;
            }
            result.computeIfAbsent(scope.getAccountId(), ignored -> new ArrayList<>()).add(team);
        }
        return result;
    }

    private boolean matchesKeyword(WorkspaceAccountEntity account, StaffEntity staff, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String normalized = keyword.trim().toLowerCase(Locale.ROOT);
        String haystack = String.join(" ",
            safe(account.getStaffCode()),
            safe(account.getRoleCode()),
            safe(account.getAccountStatus()),
            safe(staff == null ? null : staff.getName())
        ).toLowerCase(Locale.ROOT);
        return haystack.contains(normalized);
    }

    private WorkspaceAccountDto toDto(WorkspaceAccountEntity account, StaffEntity staff, List<TeamEntity> teams) {
        return new WorkspaceAccountDto(
            account.getId(),
            staff == null ? null : staff.getId(),
            account.getStaffCode(),
            staff == null ? null : staff.getName(),
            account.getRoleCode(),
            account.getAccountStatus(),
            account.getAuthSource(),
            account.getNotes(),
            account.getLastLoginAt(),
            teams.stream().map(TeamEntity::getId).map(String::valueOf).toList(),
            teams.stream().map(team -> new WorkspaceAccountScopeDto(team.getId(), team.getName(), team.getColor())).toList()
        );
    }

    private String normalizeNotes(String notes) {
        if (notes == null || notes.isBlank()) {
            return null;
        }
        if (notes.length() > 500) {
            throw new BadRequestException("Notes must be 500 characters or fewer.");
        }
        return notes.trim();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
