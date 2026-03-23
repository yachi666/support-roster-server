package com.support.server.supportrosterserver.auth;

import java.util.List;
import java.util.Set;

import com.support.server.supportrosterserver.entity.workspace.TeamEntity;

public record AuthenticatedAccount(
    Long accountId,
    Long staffRecordId,
    String staffCode,
    String staffName,
    String roleCode,
    String accountStatus,
    String authSource,
    Set<Long> teamScopeIds,
    List<TeamEntity> teamScopes
) {
    public boolean isAdmin() {
        return AccountRole.ADMIN.getCode().equalsIgnoreCase(roleCode);
    }

    public boolean isEditor() {
        return AccountRole.EDITOR.getCode().equalsIgnoreCase(roleCode);
    }

    public boolean isReadonly() {
        return AccountRole.READONLY.getCode().equalsIgnoreCase(roleCode);
    }
}
