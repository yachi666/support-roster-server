package com.support.server.supportrosterserver.service.auth;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.support.server.supportrosterserver.auth.AccountRole;
import com.support.server.supportrosterserver.auth.AccountStatus;
import com.support.server.supportrosterserver.auth.AuthenticatedAccount;
import com.support.server.supportrosterserver.entity.auth.WorkspaceAccountEntity;
import com.support.server.supportrosterserver.entity.auth.WorkspaceAccountTeamScopeEntity;
import com.support.server.supportrosterserver.entity.workspace.StaffEntity;
import com.support.server.supportrosterserver.entity.workspace.TeamEntity;
import com.support.server.supportrosterserver.exception.ForbiddenException;
import com.support.server.supportrosterserver.exception.ResourceNotFoundException;
import com.support.server.supportrosterserver.mapper.StaffMapper;
import com.support.server.supportrosterserver.mapper.WorkspaceAccountMapper;
import com.support.server.supportrosterserver.mapper.WorkspaceAccountTeamScopeMapper;
import com.support.server.supportrosterserver.service.workspace.WorkspaceLookupService;

import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthContextService {

    private final WorkspaceAccountMapper workspaceAccountMapper;
    private final WorkspaceAccountTeamScopeMapper workspaceAccountTeamScopeMapper;
    private final StaffMapper staffMapper;
    private final WorkspaceLookupService workspaceLookupService;

    public AuthenticatedAccount requireLogin() {
        StpUtil.checkLogin();
        return getCurrentAccount();
    }

    public AuthenticatedAccount getCurrentAccount() {
        Long accountId = StpUtil.getLoginIdAsLong();
        WorkspaceAccountEntity account = workspaceAccountMapper.selectById(accountId);
        if (account == null) {
            throw new ResourceNotFoundException("WorkspaceAccount", "id", accountId);
        }
        if (AccountStatus.DISABLED.getCode().equalsIgnoreCase(account.getAccountStatus())) {
            throw new ForbiddenException("Account is disabled.");
        }
        StaffEntity staff = staffMapper.selectById(account.getStaffId());
        if (staff == null) {
            throw new ResourceNotFoundException("Staff", "id", account.getStaffId());
        }
        List<WorkspaceAccountTeamScopeEntity> scopes = workspaceAccountTeamScopeMapper.selectList(Wrappers.<WorkspaceAccountTeamScopeEntity>lambdaQuery()
            .eq(WorkspaceAccountTeamScopeEntity::getAccountId, accountId)
            .orderByAsc(WorkspaceAccountTeamScopeEntity::getTeamId));
        List<TeamEntity> teams = scopes.stream()
            .map(scope -> workspaceLookupService.teamMap().get(scope.getTeamId()))
            .filter(java.util.Objects::nonNull)
            .toList();
        Set<Long> teamScopeIds = scopes.stream().map(WorkspaceAccountTeamScopeEntity::getTeamId).collect(Collectors.toCollection(LinkedHashSet::new));
        return new AuthenticatedAccount(
            account.getId(),
            staff.getId(),
            account.getStaffCode(),
            staff.getName(),
            account.getRoleCode(),
            account.getAccountStatus(),
            account.getAuthSource(),
            teamScopeIds,
            teams
        );
    }

    public void requireAdmin() {
        if (!requireLogin().isAdmin()) {
            throw new ForbiddenException("Admin permission is required.");
        }
    }

    public void requireWritableTeam(Long teamId) {
        if (teamId == null) {
            throw new ForbiddenException("Target team is required.");
        }
        AuthenticatedAccount current = requireLogin();
        if (current.isReadonly()) {
            throw new ForbiddenException("Readonly users cannot modify workspace data.");
        }
        if (current.isAdmin()) {
            return;
        }
        if (!current.teamScopeIds().contains(teamId)) {
            throw new ForbiddenException("You do not have edit access to the selected team.");
        }
    }

    public void requireWritableTeams(Collection<Long> teamIds) {
        AuthenticatedAccount current = requireLogin();
        if (current.isReadonly()) {
            throw new ForbiddenException("Readonly users cannot modify workspace data.");
        }
        if (current.isAdmin()) {
            return;
        }
        if (teamIds == null || teamIds.isEmpty()) {
            throw new ForbiddenException("At least one target team is required.");
        }
        for (Long teamId : teamIds) {
            requireWritableTeam(teamId);
        }
    }

    public void requireReadableTeam(Long teamId) {
        if (teamId == null) {
            throw new ForbiddenException("Target team is required.");
        }
        AuthenticatedAccount current = requireLogin();
        if (current.isAdmin() || current.isReadonly()) {
            return;
        }
        if (!current.teamScopeIds().contains(teamId)) {
            throw new ForbiddenException("You do not have access to the selected team.");
        }
    }

    public void requireReadableAnyTeam(Collection<Long> teamIds) {
        if (teamIds == null || teamIds.isEmpty()) {
            return;
        }
        AuthenticatedAccount current = requireLogin();
        if (current.isAdmin() || current.isReadonly()) {
            return;
        }
        boolean hasReadable = teamIds.stream().anyMatch(current.teamScopeIds()::contains);
        if (!hasReadable) {
            throw new ForbiddenException("You do not have access to the selected resource.");
        }
    }

    public List<Long> readableTeamIds() {
        AuthenticatedAccount current = requireLogin();
        if (current.isAdmin() || current.isReadonly()) {
            return workspaceLookupService.listTeams().stream().map(TeamEntity::getId).toList();
        }
        return List.copyOf(current.teamScopeIds());
    }

    public List<Long> editableTeamIds() {
        AuthenticatedAccount current = requireLogin();
        if (current.isAdmin()) {
            return workspaceLookupService.listTeams().stream().map(TeamEntity::getId).toList();
        }
        if (current.isReadonly()) {
            return List.of();
        }
        return List.copyOf(current.teamScopeIds());
    }

    public boolean canReadTeam(Long teamId) {
        if (teamId == null) {
            return false;
        }
        AuthenticatedAccount current = requireLogin();
        return current.isAdmin() || current.isReadonly() || current.teamScopeIds().contains(teamId);
    }

    public String currentActor(String fallback) {
        if (StpUtil.isLogin()) {
            AuthenticatedAccount current = requireLogin();
            if (current.staffName() != null && !current.staffName().isBlank()) {
                return current.staffName();
            }
            if (current.staffCode() != null && !current.staffCode().isBlank()) {
                return current.staffCode();
            }
        }
        return fallback == null || fallback.isBlank() ? "system" : fallback;
    }

    public List<String> permissionCodes(AuthenticatedAccount current) {
        if (current.isAdmin()) {
            return List.of("workspace.read", "workspace.write", "accounts.manage", "teams.manage");
        }
        if (current.isEditor()) {
            return List.of("workspace.read", "workspace.write");
        }
        return List.of("workspace.read");
    }

    public AccountRole requireRole(String roleCode) {
        return AccountRole.fromCode(roleCode);
    }
}
