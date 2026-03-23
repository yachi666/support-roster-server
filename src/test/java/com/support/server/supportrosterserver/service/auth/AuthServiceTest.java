package com.support.server.supportrosterserver.service.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.support.server.supportrosterserver.auth.AccountStatus;
import com.support.server.supportrosterserver.dto.auth.AuthActivateRequest;
import com.support.server.supportrosterserver.dto.auth.AuthLoginRequest;
import com.support.server.supportrosterserver.entity.auth.WorkspaceAccountEntity;
import com.support.server.supportrosterserver.exception.BadRequestException;
import com.support.server.supportrosterserver.mapper.StaffMapper;
import com.support.server.supportrosterserver.mapper.WorkspaceAccountMapper;
import com.support.server.supportrosterserver.service.workspace.WorkspaceLookupService;
import com.support.server.supportrosterserver.service.workspace.WorkspaceOperationLogService;

class AuthServiceTest {

    private WorkspaceAccountMapper workspaceAccountMapper;
    private PasswordEncoder passwordEncoder;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        workspaceAccountMapper = mock(WorkspaceAccountMapper.class);
        passwordEncoder = mock(PasswordEncoder.class);
        authService = new AuthService(
            workspaceAccountMapper,
            mock(StaffMapper.class),
            mock(AuthContextService.class),
            mock(WorkspaceOperationLogService.class),
            mock(WorkspaceLookupService.class),
            passwordEncoder
        );
    }

    @Test
    void shouldRejectPasswordLoginWhenAccountIsPendingActivation() {
        WorkspaceAccountEntity account = new WorkspaceAccountEntity();
        account.setId(101L);
        account.setStaffCode("A001");
        account.setAccountStatus(AccountStatus.PENDING_ACTIVATION.getCode());
        when(workspaceAccountMapper.selectOne(any())).thenReturn(account);

        AuthLoginRequest request = new AuthLoginRequest();
        request.setStaffId("A001");
        request.setPassword("secret123");

        BadRequestException error = assertThrows(BadRequestException.class, () -> authService.login(request));

        assertEquals("Account password has not been initialized. Please use first-time activation.", error.getMessage());
        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    void shouldRejectActivationWhenPasswordWasAlreadyInitialized() {
        WorkspaceAccountEntity account = new WorkspaceAccountEntity();
        account.setId(102L);
        account.setStaffCode("A002");
        account.setAccountStatus(AccountStatus.ACTIVE.getCode());
        when(workspaceAccountMapper.selectOne(any())).thenReturn(account);

        AuthActivateRequest request = new AuthActivateRequest();
        request.setStaffId("A002");
        request.setNewPassword("secret123");

        BadRequestException error = assertThrows(BadRequestException.class, () -> authService.activate(request));

        assertEquals("Password has already been initialized. Please sign in.", error.getMessage());
        verify(workspaceAccountMapper, never()).updateById(any(WorkspaceAccountEntity.class));
    }
}
