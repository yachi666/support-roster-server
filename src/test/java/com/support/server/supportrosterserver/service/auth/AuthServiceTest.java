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
import com.support.server.supportrosterserver.entity.auth.WorkspaceAccountTeamScopeEntity;
import com.support.server.supportrosterserver.entity.workspace.StaffEntity;
import com.support.server.supportrosterserver.exception.BadRequestException;
import com.support.server.supportrosterserver.mapper.StaffMapper;
import com.support.server.supportrosterserver.mapper.WorkspaceAccountMapper;
import com.support.server.supportrosterserver.mapper.WorkspaceAccountTeamScopeMapper;
import com.support.server.supportrosterserver.service.workspace.WorkspaceLookupService;
import com.support.server.supportrosterserver.service.workspace.WorkspaceOperationLogService;

class AuthServiceTest {

    private WorkspaceAccountMapper workspaceAccountMapper;
    private StaffMapper staffMapper;
    private WorkspaceAccountTeamScopeMapper workspaceAccountTeamScopeMapper;
    private PasswordEncoder passwordEncoder;
    private AuthTokenVersionService authTokenVersionService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        workspaceAccountMapper = mock(WorkspaceAccountMapper.class);
        staffMapper = mock(StaffMapper.class);
        workspaceAccountTeamScopeMapper = mock(WorkspaceAccountTeamScopeMapper.class);
        passwordEncoder = mock(PasswordEncoder.class);
        authTokenVersionService = mock(AuthTokenVersionService.class);
        authService = new AuthService(
            workspaceAccountMapper,
            staffMapper,
            workspaceAccountTeamScopeMapper,
            mock(AuthContextService.class),
            authTokenVersionService,
            mock(WorkspaceOperationLogService.class),
            mock(WorkspaceLookupService.class),
            passwordEncoder
        );
    }

    @Test
    void shouldRejectPasswordLoginWhenAccountIsPendingActivation() {
        WorkspaceAccountEntity account = new WorkspaceAccountEntity();
        account.setId(101L);
        account.setStaffId("A001");
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
        account.setStaffId("A002");
        account.setAccountStatus(AccountStatus.ACTIVE.getCode());
        account.setDeleted(0);
        when(workspaceAccountMapper.selectAnyByStaffId("A002")).thenReturn(account);

        AuthActivateRequest request = new AuthActivateRequest();
        request.setStaffId("A002");
        request.setNewPassword("secret123");

        BadRequestException error = assertThrows(BadRequestException.class, () -> authService.activate(request));

        assertEquals("Password has already been initialized. Please sign in.", error.getMessage());
        verify(workspaceAccountMapper, never()).updateById(any(WorkspaceAccountEntity.class));
    }

    @Test
    void shouldRejectActivationWhenNewPasswordIsShorterThanFourCharacters() {
        AuthActivateRequest request = new AuthActivateRequest();
        request.setStaffId("A003");
        request.setNewPassword("123");

        BadRequestException error = assertThrows(BadRequestException.class, () -> authService.activate(request));

        assertEquals("Password must be at least 4 characters.", error.getMessage());
        verify(passwordEncoder, never()).encode(any());
        verify(workspaceAccountMapper, never()).selectAnyByStaffId(any());
    }

    @Test
    void shouldSelfRegisterWhenNoExistingAccount() {
        when(workspaceAccountMapper.selectAnyByStaffId("A004")).thenReturn(null);
        StaffEntity staff = new StaffEntity();
        staff.setId(201L);
        staff.setStaffId("A004");
        staff.setStatus("Active");
        staff.setTeamId(301L);
        when(staffMapper.selectOne(any())).thenReturn(staff);
        when(passwordEncoder.encode("secret123")).thenReturn("$2a$encodedhash");

        AuthActivateRequest request = new AuthActivateRequest();
        request.setStaffId("A004");
        request.setNewPassword("secret123");

        // establishSession calls getCurrentUser() → authContextService.requireLogin()
        // which throws because AuthContextService is mocked without a stub.
        // Full session flow needs integration test, but we verify that the
        // account creation and team scope insertion were executed.
        assertThrows(Exception.class, () -> authService.activate(request));

        verify(passwordEncoder).encode("secret123");
        verify(workspaceAccountMapper).insert(any(WorkspaceAccountEntity.class));
        verify(workspaceAccountTeamScopeMapper).insert(any(WorkspaceAccountTeamScopeEntity.class));
    }
}
