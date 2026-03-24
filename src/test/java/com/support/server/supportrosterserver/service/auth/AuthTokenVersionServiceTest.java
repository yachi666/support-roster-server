package com.support.server.supportrosterserver.service.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.support.server.supportrosterserver.entity.auth.WorkspaceAccountEntity;

import cn.dev33.satoken.stp.SaLoginModel;

class AuthTokenVersionServiceTest {

    private final AuthTokenVersionService authTokenVersionService = new AuthTokenVersionService();

    @Test
    void shouldCreateLoginModelWithCurrentTokenVersion() {
        WorkspaceAccountEntity account = new WorkspaceAccountEntity();
        account.setTokenVersion(7L);

        SaLoginModel loginModel = authTokenVersionService.createLoginModel(account);

        assertEquals(7L, ((Number) loginModel.getExtra(AuthTokenVersionService.TOKEN_VERSION_EXTRA_KEY)).longValue());
    }

    @Test
    void shouldNormalizeMissingTokenVersionToInitialValue() {
        WorkspaceAccountEntity account = new WorkspaceAccountEntity();

        long tokenVersion = authTokenVersionService.currentTokenVersion(account);

        assertEquals(AuthTokenVersionService.INITIAL_TOKEN_VERSION, tokenVersion);
    }

    @Test
    void shouldDetectTokenVersionMismatch() {
        WorkspaceAccountEntity account = new WorkspaceAccountEntity();
        account.setTokenVersion(5L);

        boolean matches = authTokenVersionService.isCurrentTokenVersion(account, 4L);

        assertFalse(matches);
    }

    @Test
    void shouldRejectInvalidTokenVersionPayload() {
        IllegalArgumentException error = assertThrows(
            IllegalArgumentException.class,
            () -> authTokenVersionService.parseTokenVersion("not-a-number")
        );

        assertEquals("Token payload is invalid.", error.getMessage());
    }
}
