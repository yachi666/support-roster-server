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

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthContextService {

    private final WorkspaceAccountMapper workspaceAccountMapper;
    private final WorkspaceAccountTeamScopeMapper workspaceAccountTeamScopeMapper;
    private final StaffMapper staffMapper;
    private final WorkspaceLookupService workspaceLookupService;
    private final AuthTokenVersionService authTokenVersionService;

    public boolean isLoggedIn() {
        return StpUtil.isLogin();
    }

    public AuthenticatedAccount requireLogin() {
        StpUtil.checkLogin();
        return getCurrentAccount();
    }

    public AuthenticatedAccount getCurrentAccount() {
        Long accountId = StpUtil.getLoginIdAsLong();
        WorkspaceAccountEntity account = workspaceAccountMapper.selectById(accountId);
        if (account == null) {
            throw expiredLogin();
        }
        if (AccountStatus.DISABLED.getCode().equalsIgnoreCase(account.getAccountStatus())) {
            throw new ForbiddenException("Account is disabled.");
        }
        authTokenVersionService.validateCurrentTokenVersion(account);
        StaffEntity staff = staffMapper.selectById(account.getStaffRecordId());
        if (staff == null) {
            throw expiredLogin();
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
            account.getStaffId(),
            staff.getName(),
            account.getRoleCode(),
            account.getAccountStatus(),
            account.getAuthSource(),
            teamScopeIds,
            teams
        );
    }

    private NotLoginException expiredLogin() {
        String tokenValue = StpUtil.getTokenValue();
        StpUtil.logout();
        return NotLoginException.newInstance(
            StpUtil.getLoginType(),
            NotLoginException.TOKEN_TIMEOUT,
            "Login state has expired.",
            tokenValue
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
        if (!isLoggedIn()) {
            return;
        }
        // Validate account status and token version; team-scope not enforced — all active users can read any team
        requireLogin();
    }

    public void requireReadableAnyTeam(Collection<Long> teamIds) {
        if (teamIds == null || teamIds.isEmpty()) {
            return;
        }
        if (!isLoggedIn()) {
            return;
        }
        // Validate account status and token version; team-scope not enforced — all active users can read any team
        requireLogin();
    }

    public List<Long> readableTeamIds() {
        if (!isLoggedIn()) {
            return workspaceLookupService.listTeams().stream().map(TeamEntity::getId).toList();
        }
        AuthenticatedAccount current = requireLogin();
        // All authenticated users (admin, editor, readonly) can see all teams
        return workspaceLookupService.listTeams().stream().map(TeamEntity::getId).toList();
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
        if (!isLoggedIn()) {
            return true;
        }
        // All authenticated users can read any team
        AuthenticatedAccount current = requireLogin();
        return true;
    }

    public String currentActor(String fallback) {
        if (StpUtil.isLogin()) {
            AuthenticatedAccount current = requireLogin();
            if (current.staffName() != null && !current.staffName().isBlank()) {
                return current.staffName();
            }
            if (current.staffId() != null && !current.staffId().isBlank()) {
                return current.staffId();
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
