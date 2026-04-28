package com.support.server.supportrosterserver.service.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.support.server.supportrosterserver.auth.AuthenticatedAccount;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceLinuxPasswordAccessAuditListResponse;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceLinuxPasswordDto;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceLinuxPasswordListResponse;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceLinuxPasswordUpsertRequest;
import com.support.server.supportrosterserver.entity.workspace.LinuxPasswordServerBusinessUnitEntity;
import com.support.server.supportrosterserver.entity.workspace.LinuxPasswordAccessAuditEntity;
import com.support.server.supportrosterserver.entity.workspace.LinuxPasswordCredentialEntity;
import com.support.server.supportrosterserver.entity.workspace.LinuxPasswordDirectoryEntity;
import com.support.server.supportrosterserver.entity.workspace.LinuxPasswordServerEntity;
import com.support.server.supportrosterserver.exception.ForbiddenException;
import com.support.server.supportrosterserver.mapper.LinuxPasswordAccessAuditMapper;
import com.support.server.supportrosterserver.mapper.LinuxPasswordCredentialMapper;
import com.support.server.supportrosterserver.mapper.LinuxPasswordDirectoryMapper;
import com.support.server.supportrosterserver.mapper.LinuxPasswordServerBusinessUnitMapper;
import com.support.server.supportrosterserver.mapper.LinuxPasswordServerMapper;
import com.support.server.supportrosterserver.service.auth.AuthContextService;

class WorkspaceLinuxPasswordServiceTest {

    private LinuxPasswordServerMapper linuxPasswordServerMapper;
    private LinuxPasswordServerBusinessUnitMapper linuxPasswordServerBusinessUnitMapper;
    private LinuxPasswordDirectoryMapper linuxPasswordDirectoryMapper;
    private LinuxPasswordCredentialMapper linuxPasswordCredentialMapper;
    private LinuxPasswordAccessAuditMapper linuxPasswordAccessAuditMapper;
    private LinuxPasswordSecretService linuxPasswordSecretService;
    private AuthContextService authContextService;
    private WorkspaceOperationLogService workspaceOperationLogService;
    private WorkspaceLinuxPasswordService workspaceLinuxPasswordService;

    @BeforeEach
    void setUp() {
        linuxPasswordServerMapper = mock(LinuxPasswordServerMapper.class);
        linuxPasswordServerBusinessUnitMapper = mock(LinuxPasswordServerBusinessUnitMapper.class);
        linuxPasswordDirectoryMapper = mock(LinuxPasswordDirectoryMapper.class);
        linuxPasswordCredentialMapper = mock(LinuxPasswordCredentialMapper.class);
        linuxPasswordAccessAuditMapper = mock(LinuxPasswordAccessAuditMapper.class);
        linuxPasswordSecretService = new LinuxPasswordSecretService("test-linux-password-secret");
        authContextService = mock(AuthContextService.class);
        workspaceOperationLogService = mock(WorkspaceOperationLogService.class);
        when(linuxPasswordCredentialMapper.selectList(any())).thenReturn(List.of());
        workspaceLinuxPasswordService = new WorkspaceLinuxPasswordService(
            linuxPasswordServerMapper,
            linuxPasswordServerBusinessUnitMapper,
            linuxPasswordDirectoryMapper,
            linuxPasswordCredentialMapper,
            linuxPasswordAccessAuditMapper,
            linuxPasswordSecretService,
            authContextService,
            workspaceOperationLogService
        );
    }

    @Test
    void shouldCreateLinuxPasswordServerForLoggedInUserAndDefaultStatusOnline() {
        when(authContextService.requireLogin()).thenReturn(loggedInAccount("editor", "Editor User"));

        WorkspaceLinuxPasswordUpsertRequest request = new WorkspaceLinuxPasswordUpsertRequest();
        request.setHostname("prod-web-01");
        request.setIp("10.0.0.9");
        request.setUsername("root");
        request.setPassword("TopSecret!9");
        request.setBusinessUnits(List.of("Infrastructure", "Web"));

        when(linuxPasswordServerMapper.selectById(anyLong())).thenReturn(buildServer(99L, "prod-web-01", "10.0.0.9", "root", "TopSecret!9", "online"));
        when(linuxPasswordServerBusinessUnitMapper.selectList(any())).thenReturn(List.of(
            buildBusinessUnit(99L, "Infrastructure"),
            buildBusinessUnit(99L, "Web")
        ));

        WorkspaceLinuxPasswordDto created = workspaceLinuxPasswordService.createServer(request);

        ArgumentCaptor<LinuxPasswordServerEntity> entityCaptor = ArgumentCaptor.forClass(LinuxPasswordServerEntity.class);
        verify(linuxPasswordServerMapper).insert(entityCaptor.capture());
        assertEquals("online", entityCaptor.getValue().getStatus());
        verify(authContextService).requireLogin();
        verify(authContextService, never()).requireAdmin();
        assertEquals(List.of("Infrastructure", "Web"), created.getBusinessUnits());
    }

    @Test
    void shouldNormalizeEmptyBusinessUnitsToUncategorized() {
        when(authContextService.requireLogin()).thenReturn(loggedInAccount("admin", "Admin User"));

        WorkspaceLinuxPasswordUpsertRequest request = new WorkspaceLinuxPasswordUpsertRequest();
        request.setHostname("db-01");
        request.setIp("10.0.2.10");
        request.setUsername("postgres");
        request.setPassword("Password!1");
        request.setBusinessUnits(List.of());

        when(linuxPasswordServerMapper.selectById(anyLong())).thenReturn(buildServer(101L, "db-01", "10.0.2.10", "postgres", "Password!1", "online"));
        when(linuxPasswordServerBusinessUnitMapper.selectList(any())).thenReturn(List.of(buildBusinessUnit(101L, "Uncategorized")));

        WorkspaceLinuxPasswordDto created = workspaceLinuxPasswordService.createServer(request);

        ArgumentCaptor<LinuxPasswordServerBusinessUnitEntity> relationCaptor = ArgumentCaptor.forClass(LinuxPasswordServerBusinessUnitEntity.class);
        verify(linuxPasswordServerBusinessUnitMapper).insert(relationCaptor.capture());
        assertEquals("Uncategorized", relationCaptor.getValue().getBusinessUnit());
        assertEquals(List.of("Uncategorized"), created.getBusinessUnits());
    }

    @Test
    void shouldFilterServersBySearchAndBusinessUnit() {
        when(authContextService.requireLogin()).thenReturn(loggedInAccount("readonly", "Readonly User"));
        when(linuxPasswordServerMapper.selectList(any())).thenReturn(List.of(
            buildServer(1L, "infra-proxy-01", "10.0.1.2", "admin", "Proxy@Infra99", "online"),
            buildServer(2L, "fin-db-01", "10.0.10.5", "postgres", "P@ssw0rdFin1!", "maintenance")
        ));
        when(linuxPasswordServerBusinessUnitMapper.selectList(any())).thenReturn(List.of(
            buildBusinessUnit(1L, "Infrastructure"),
            buildBusinessUnit(1L, "Web"),
            buildBusinessUnit(2L, "Finance"),
            buildBusinessUnit(2L, "Database")
        ));

        WorkspaceLinuxPasswordListResponse response = workspaceLinuxPasswordService.listServers("proxy", "Infrastructure");

        assertEquals(List.of("Infrastructure", "Web"), response.getBusinessUnits().stream().filter(unit -> unit.startsWith("I") || unit.startsWith("W")).toList());
        assertEquals(List.of(1L), response.getItems().stream().map(WorkspaceLinuxPasswordDto::getId).toList());
    }

    @Test
    void shouldRequireAdminToUpdateServer() {
        doThrow(new ForbiddenException("Admin permission is required.")).when(authContextService).requireAdmin();

        WorkspaceLinuxPasswordUpsertRequest request = new WorkspaceLinuxPasswordUpsertRequest();
        request.setHostname("infra-proxy-01");
        request.setIp("10.0.1.2");
        request.setUsername("admin");
        request.setPassword("Proxy@Infra99");
        request.setBusinessUnits(List.of("Infrastructure"));
        request.setStatus("offline");

        assertThrows(ForbiddenException.class, () -> workspaceLinuxPasswordService.updateServer(1L, request));

        verify(linuxPasswordServerMapper, never()).updateById(any(LinuxPasswordServerEntity.class));
    }

    @Test
    void shouldRequireAdminToDeleteServer() {
        doThrow(new ForbiddenException("Admin permission is required.")).when(authContextService).requireAdmin();

        assertThrows(ForbiddenException.class, () -> workspaceLinuxPasswordService.deleteServer(1L));

        verify(linuxPasswordServerMapper, never()).deleteById(anyLong());
    }

    @Test
    void shouldListDirectoriesFromDedicatedDirectoryTable() {
        when(authContextService.requireLogin()).thenReturn(loggedInAccount("readonly", "Readonly User"));
        when(linuxPasswordDirectoryMapper.selectList(any())).thenReturn(List.of(
            buildDirectory(11L, "Database"),
            buildDirectory(12L, "Web")
        ));

        List<String> directories = workspaceLinuxPasswordService.listDirectories();

        assertEquals(List.of("Database", "Web"), directories);
        verify(authContextService).requireLogin();
    }

    @Test
    void shouldBackfillDirectoryTableFromExistingMachineRelationsWhenEmpty() {
        when(authContextService.requireLogin()).thenReturn(loggedInAccount("readonly", "Readonly User"));
        when(linuxPasswordDirectoryMapper.selectList(any())).thenReturn(
            List.of(),
            List.of(),
            List.of(buildDirectory(31L, "Finance"))
        );
        when(linuxPasswordServerBusinessUnitMapper.selectList(any())).thenReturn(List.of(
            buildBusinessUnit(1L, "Finance")
        ));

        List<String> directories = workspaceLinuxPasswordService.listDirectories();

        ArgumentCaptor<LinuxPasswordDirectoryEntity> directoryCaptor = ArgumentCaptor.forClass(LinuxPasswordDirectoryEntity.class);
        verify(linuxPasswordDirectoryMapper).insert(directoryCaptor.capture());
        assertEquals("Finance", directoryCaptor.getValue().getName());
        assertEquals(List.of("Finance"), directories);
    }

    @Test
    void shouldBackfillDirectoryTableOnceForCaseInsensitiveDuplicateRelations() {
        when(authContextService.requireLogin()).thenReturn(loggedInAccount("readonly", "Readonly User"));
        when(linuxPasswordDirectoryMapper.selectList(any())).thenReturn(
            List.of(),
            List.of(),
            List.of(buildDirectory(31L, "Finance"))
        );
        when(linuxPasswordServerBusinessUnitMapper.selectList(any())).thenReturn(List.of(
            buildBusinessUnit(1L, "Finance"),
            buildBusinessUnit(2L, " finance ")
        ));

        List<String> directories = workspaceLinuxPasswordService.listDirectories();

        ArgumentCaptor<LinuxPasswordDirectoryEntity> directoryCaptor = ArgumentCaptor.forClass(LinuxPasswordDirectoryEntity.class);
        verify(linuxPasswordDirectoryMapper, times(1)).insert(directoryCaptor.capture());
        assertEquals("Finance", directoryCaptor.getValue().getName());
        assertEquals(List.of("Finance"), directories);
    }

    @Test
    void shouldCreateMissingDirectoryRowsWhenCreatingServer() {
        when(authContextService.requireLogin()).thenReturn(loggedInAccount("editor", "Editor User"));

        WorkspaceLinuxPasswordUpsertRequest request = new WorkspaceLinuxPasswordUpsertRequest();
        request.setHostname("prod-web-01");
        request.setIp("10.0.0.9");
        request.setUsername("root");
        request.setPassword("TopSecret!9");
        request.setBusinessUnits(List.of("Infrastructure", "Web"));

        when(linuxPasswordDirectoryMapper.selectList(any())).thenReturn(List.of(
            buildDirectory(21L, "Infrastructure")
        ));
        when(linuxPasswordServerMapper.selectById(anyLong())).thenReturn(buildServer(99L, "prod-web-01", "10.0.0.9", "root", "TopSecret!9", "online"));
        when(linuxPasswordServerBusinessUnitMapper.selectList(any())).thenReturn(List.of(
            buildBusinessUnit(99L, "Infrastructure"),
            buildBusinessUnit(99L, "Web")
        ));

        workspaceLinuxPasswordService.createServer(request);

        ArgumentCaptor<LinuxPasswordDirectoryEntity> directoryCaptor = ArgumentCaptor.forClass(LinuxPasswordDirectoryEntity.class);
        verify(linuxPasswordDirectoryMapper).insert(directoryCaptor.capture());
        assertEquals("Web", directoryCaptor.getValue().getName());
    }

    @Test
    void shouldDeduplicateBusinessUnitsCaseInsensitivelyOnCreate() {
        when(authContextService.requireLogin()).thenReturn(loggedInAccount("editor", "Editor User"));

        WorkspaceLinuxPasswordUpsertRequest request = new WorkspaceLinuxPasswordUpsertRequest();
        request.setHostname("prod-web-02");
        request.setIp("10.0.0.10");
        request.setUsername("root");
        request.setPassword("TopSecret!10");
        request.setBusinessUnits(List.of("Web", " web ", "WEB"));

        when(linuxPasswordDirectoryMapper.selectList(any())).thenReturn(List.of());
        when(linuxPasswordServerMapper.selectById(anyLong())).thenReturn(buildServer(88L, "prod-web-02", "10.0.0.10", "root", "TopSecret!10", "online"));
        when(linuxPasswordServerBusinessUnitMapper.selectList(any())).thenReturn(List.of(
            buildBusinessUnit(88L, "Web")
        ));

        WorkspaceLinuxPasswordDto created = workspaceLinuxPasswordService.createServer(request);

        ArgumentCaptor<LinuxPasswordServerBusinessUnitEntity> relationCaptor = ArgumentCaptor.forClass(LinuxPasswordServerBusinessUnitEntity.class);
        ArgumentCaptor<LinuxPasswordDirectoryEntity> directoryCaptor = ArgumentCaptor.forClass(LinuxPasswordDirectoryEntity.class);
        verify(linuxPasswordServerBusinessUnitMapper, times(1)).insert(relationCaptor.capture());
        verify(linuxPasswordDirectoryMapper, times(1)).insert(directoryCaptor.capture());
        assertEquals("Web", relationCaptor.getValue().getBusinessUnit());
        assertEquals("Web", directoryCaptor.getValue().getName());
        assertEquals(List.of("Web"), created.getBusinessUnits());
    }

    @Test
    void shouldDecryptCredentialSecretAndWriteStaffAuditRecord() {
        when(authContextService.requireLogin()).thenReturn(loggedInAccount("readonly", "Readonly User"));
        LinuxPasswordSecretService.EncryptedSecret encryptedSecret = linuxPasswordSecretService.encrypt("Proxy@Infra99");
        LinuxPasswordCredentialEntity credential = new LinuxPasswordCredentialEntity();
        credential.setId(501L);
        credential.setServerId(1L);
        credential.setUsername("admin");
        credential.setPasswordCiphertext(encryptedSecret.ciphertext());
        credential.setPasswordIv(encryptedSecret.iv());
        credential.setKeyVersion(encryptedSecret.keyVersion());

        when(linuxPasswordCredentialMapper.selectById(501L)).thenReturn(credential);
        when(linuxPasswordServerMapper.selectById(1L)).thenReturn(buildServer(1L, "infra-proxy-01", "10.0.1.2", null, null, "online"));

        assertEquals(
            "Proxy@Infra99",
            workspaceLinuxPasswordService.revealCredentialSecret(501L, "copy", "127.0.0.1", "JUnit").getPassword()
        );

        ArgumentCaptor<LinuxPasswordAccessAuditEntity> auditCaptor = ArgumentCaptor.forClass(LinuxPasswordAccessAuditEntity.class);
        verify(linuxPasswordAccessAuditMapper).insert(auditCaptor.capture());
        assertEquals("U001", auditCaptor.getValue().getStaffId());
        assertEquals(11L, auditCaptor.getValue().getStaffRecordId());
        assertEquals("COPY", auditCaptor.getValue().getAction());
        assertEquals("SUCCESS", auditCaptor.getValue().getResult());
        assertEquals(501L, auditCaptor.getValue().getCredentialId());
    }

    @Test
    void shouldListAccessAuditsForAdminWithJoinedFilters() {
        LinuxPasswordAccessAuditEntity audit = buildAudit(901L, 1L, 501L, "U001", "Readonly User", "COPY", "SUCCESS");
        LinuxPasswordCredentialEntity credential = new LinuxPasswordCredentialEntity();
        credential.setId(501L);
        credential.setServerId(1L);
        credential.setUsername("admin");

        IPage<LinuxPasswordAccessAuditEntity> page = buildPage(List.of(audit), 1L);
        when(linuxPasswordAccessAuditMapper.selectPage(any(), any())).thenReturn(page);
        when(linuxPasswordCredentialMapper.selectList(any())).thenReturn(List.of(credential));
        when(linuxPasswordServerMapper.selectList(any())).thenReturn(List.of(
            buildServer(1L, "infra-proxy-01", "10.0.1.2", null, null, "online")
        ));

        WorkspaceLinuxPasswordAccessAuditListResponse response = workspaceLinuxPasswordService.listAccessAudits(
            "proxy",
            "U001",
            "readonly",
            "infra",
            "10.0.1",
            "admin",
            "copy",
            "success",
            "2026-04-01",
            "2026-04-30",
            1,
            20
        );

        verify(authContextService).requireAdmin();
        assertEquals(1, response.getTotal());
        assertEquals("infra-proxy-01", response.getItems().get(0).getHostname());
        assertEquals("admin", response.getItems().get(0).getUsername());
    }

    @Test
    void shouldRequireAdminToListAccessAudits() {
        doThrow(new ForbiddenException("Admin permission is required.")).when(authContextService).requireAdmin();

        assertThrows(ForbiddenException.class, () -> workspaceLinuxPasswordService.listAccessAudits(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            1,
            20
        ));

        verify(linuxPasswordAccessAuditMapper, never()).selectPage(any(), any());
    }

    @Test
    void shouldWriteFailedAuditAndRethrowWhenDecryptThrows() {
        when(authContextService.requireLogin()).thenReturn(loggedInAccount("readonly", "Readonly User"));
        LinuxPasswordCredentialEntity credential = new LinuxPasswordCredentialEntity();
        credential.setId(502L);
        credential.setServerId(1L);
        credential.setUsername("admin");
        credential.setPasswordCiphertext("invalid-ciphertext");
        credential.setPasswordIv("invalid-iv");
        credential.setKeyVersion("v1");

        when(linuxPasswordCredentialMapper.selectById(502L)).thenReturn(credential);
        when(linuxPasswordServerMapper.selectById(1L)).thenReturn(buildServer(1L, "infra-proxy-01", "10.0.1.2", null, null, "online"));

        assertThrows(RuntimeException.class,
            () -> workspaceLinuxPasswordService.revealCredentialSecret(502L, "view", "127.0.0.1", "JUnit"));

        ArgumentCaptor<LinuxPasswordAccessAuditEntity> auditCaptor = ArgumentCaptor.forClass(LinuxPasswordAccessAuditEntity.class);
        verify(linuxPasswordAccessAuditMapper).insert(auditCaptor.capture());
        assertEquals("FAILED", auditCaptor.getValue().getResult());
        assertEquals("VIEW", auditCaptor.getValue().getAction());
        assertEquals(502L, auditCaptor.getValue().getCredentialId());
    }

    @Test
    void shouldWriteFailedAuditWhenCredentialNotFound() {
        when(authContextService.requireLogin()).thenReturn(loggedInAccount("readonly", "Readonly User"));
        when(linuxPasswordCredentialMapper.selectById(999L)).thenReturn(null);

        assertThrows(RuntimeException.class,
            () -> workspaceLinuxPasswordService.revealCredentialSecret(999L, "copy", "127.0.0.1", "JUnit"));

        ArgumentCaptor<LinuxPasswordAccessAuditEntity> auditCaptor = ArgumentCaptor.forClass(LinuxPasswordAccessAuditEntity.class);
        verify(linuxPasswordAccessAuditMapper).insert(auditCaptor.capture());
        assertEquals("FAILED", auditCaptor.getValue().getResult());
        assertEquals("COPY", auditCaptor.getValue().getAction());
        assertEquals(999L, auditCaptor.getValue().getCredentialId());
    }

    @Test
    void shouldWriteFailedAuditWhenServerMissingForCredential() {
        when(authContextService.requireLogin()).thenReturn(loggedInAccount("readonly", "Readonly User"));
        LinuxPasswordCredentialEntity credential = new LinuxPasswordCredentialEntity();
        credential.setId(503L);
        credential.setServerId(99L);
        credential.setUsername("admin");

        when(linuxPasswordCredentialMapper.selectById(503L)).thenReturn(credential);
        when(linuxPasswordServerMapper.selectById(99L)).thenReturn(null);

        assertThrows(RuntimeException.class,
            () -> workspaceLinuxPasswordService.revealCredentialSecret(503L, "view", "127.0.0.1", "JUnit"));

        ArgumentCaptor<LinuxPasswordAccessAuditEntity> auditCaptor = ArgumentCaptor.forClass(LinuxPasswordAccessAuditEntity.class);
        verify(linuxPasswordAccessAuditMapper).insert(auditCaptor.capture());
        assertEquals("FAILED", auditCaptor.getValue().getResult());
        assertEquals("VIEW", auditCaptor.getValue().getAction());
        assertEquals(503L, auditCaptor.getValue().getCredentialId());
    }

    @Test
    void shouldDeleteOrphanDirectoriesWhenDeletingServer() {
        when(linuxPasswordServerMapper.selectById(1L)).thenReturn(buildServer(1L, "fin-db-01", "10.0.10.5", "postgres", "P@ssw0rdFin1!", "online"));
        when(linuxPasswordServerBusinessUnitMapper.selectList(any())).thenReturn(
            List.of(buildBusinessUnit(1L, "Finance")),
            List.of()
        );

        workspaceLinuxPasswordService.deleteServer(1L);

        verify(linuxPasswordDirectoryMapper).delete(any());
    }

    @Test
    void shouldPreferAuditSnapshotFieldsOverLiveJoin() {
        // Audit entity has snapshot fields populated - server/credential may be deleted
        LinuxPasswordAccessAuditEntity audit = buildAudit(902L, 99L, 599L, "U002", "Alice Wang", "VIEW", "SUCCESS");
        audit.setHostnameSnapshot("deleted-host-01");
        audit.setIpSnapshot("10.99.0.1");
        audit.setUsernameSnapshot("root");

        IPage<LinuxPasswordAccessAuditEntity> page = buildPage(List.of(audit), 1L);
        when(linuxPasswordAccessAuditMapper.selectPage(any(), any())).thenReturn(page);
        // No server or credential returned from mapper (both deleted)
        when(linuxPasswordServerMapper.selectList(any())).thenReturn(List.of());

        WorkspaceLinuxPasswordAccessAuditListResponse response = workspaceLinuxPasswordService.listAccessAudits(
            null, null, null, null, null, null, null, null, null, null, 1, 20);

        assertEquals(1, response.getTotal());
        assertEquals("deleted-host-01", response.getItems().get(0).getHostname());
        assertEquals("10.99.0.1", response.getItems().get(0).getIp());
        assertEquals("root", response.getItems().get(0).getUsername());
    }

    @Test
    void shouldFallbackToLiveJoinWhenSnapshotsAbsent() {
        // Audit entity without snapshots (legacy record) - falls back to live join
        LinuxPasswordAccessAuditEntity audit = buildAudit(903L, 1L, 501L, "U003", "Bob Lee", "COPY", "SUCCESS");
        assertNull(audit.getHostnameSnapshot());

        LinuxPasswordCredentialEntity credential = new LinuxPasswordCredentialEntity();
        credential.setId(501L);
        credential.setServerId(1L);
        credential.setUsername("deploy");

        IPage<LinuxPasswordAccessAuditEntity> page = buildPage(List.of(audit), 1L);
        when(linuxPasswordAccessAuditMapper.selectPage(any(), any())).thenReturn(page);
        when(linuxPasswordCredentialMapper.selectList(any())).thenReturn(List.of(credential));
        when(linuxPasswordServerMapper.selectList(any())).thenReturn(List.of(
            buildServer(1L, "app-server-01", "10.0.2.5", null, null, "online")
        ));

        WorkspaceLinuxPasswordAccessAuditListResponse response = workspaceLinuxPasswordService.listAccessAudits(
            null, null, null, null, null, null, null, null, null, null, 1, 20);

        assertEquals(1, response.getTotal());
        assertEquals("app-server-01", response.getItems().get(0).getHostname());
        assertEquals("10.0.2.5", response.getItems().get(0).getIp());
        assertEquals("deploy", response.getItems().get(0).getUsername());
    }

    @Test
    void shouldWriteAuditWithSnapshotFieldsAtCreationTime() {
        when(authContextService.requireLogin()).thenReturn(loggedInAccount("readonly", "Readonly User"));
        LinuxPasswordSecretService.EncryptedSecret encryptedSecret = linuxPasswordSecretService.encrypt("Pass@Word1");
        LinuxPasswordCredentialEntity credential = new LinuxPasswordCredentialEntity();
        credential.setId(601L);
        credential.setServerId(10L);
        credential.setUsername("ubuntu");
        credential.setPasswordCiphertext(encryptedSecret.ciphertext());
        credential.setPasswordIv(encryptedSecret.iv());
        credential.setKeyVersion(encryptedSecret.keyVersion());

        when(linuxPasswordCredentialMapper.selectById(601L)).thenReturn(credential);
        when(linuxPasswordServerMapper.selectById(10L)).thenReturn(
            buildServer(10L, "snapshot-host-01", "192.168.1.10", null, null, "online"));

        workspaceLinuxPasswordService.revealCredentialSecret(601L, "view", "10.1.1.1", "TestAgent");

        ArgumentCaptor<LinuxPasswordAccessAuditEntity> auditCaptor = ArgumentCaptor.forClass(LinuxPasswordAccessAuditEntity.class);
        verify(linuxPasswordAccessAuditMapper).insert(auditCaptor.capture());
        assertEquals("snapshot-host-01", auditCaptor.getValue().getHostnameSnapshot());
        assertEquals("192.168.1.10", auditCaptor.getValue().getIpSnapshot());
        assertEquals("ubuntu", auditCaptor.getValue().getUsernameSnapshot());
    }

    @Test
    void shouldPaginateAuditsUsingSelectPageNotInMemory() {
        // Verify that pagination is pushed to DB via selectPage, not done in memory
        LinuxPasswordAccessAuditEntity audit = buildAudit(904L, 1L, 501L, "U004", "Carol", "VIEW", "SUCCESS");
        IPage<LinuxPasswordAccessAuditEntity> page = buildPage(List.of(audit), 42L);
        when(linuxPasswordAccessAuditMapper.selectPage(any(), any())).thenReturn(page);
        when(linuxPasswordServerMapper.selectList(any())).thenReturn(List.of());

        WorkspaceLinuxPasswordAccessAuditListResponse response = workspaceLinuxPasswordService.listAccessAudits(
            null, null, null, null, null, null, null, null, null, null, 3, 10);

        // Total comes from DB page result (42), not from in-memory list size
        assertEquals(42L, response.getTotal());
        assertEquals(3L, response.getPage());
        assertEquals(10L, response.getPageSize());
        verify(linuxPasswordAccessAuditMapper).selectPage(any(), any());
        verify(linuxPasswordAccessAuditMapper, never()).selectList(any());
    }

    private IPage<LinuxPasswordAccessAuditEntity> buildPage(List<LinuxPasswordAccessAuditEntity> records, long total) {
        Page<LinuxPasswordAccessAuditEntity> page = new Page<>();
        page.setRecords(records);
        page.setTotal(total);
        return page;
    }

    private LinuxPasswordAccessAuditEntity buildAuditWithSnapshots(
            Long id, Long serverId, Long credentialId,
            String staffId, String staffName, String action, String result,
            String hostnameSnapshot, String ipSnapshot, String usernameSnapshot) {
        LinuxPasswordAccessAuditEntity entity = buildAudit(id, serverId, credentialId, staffId, staffName, action, result);
        entity.setHostnameSnapshot(hostnameSnapshot);
        entity.setIpSnapshot(ipSnapshot);
        entity.setUsernameSnapshot(usernameSnapshot);
        return entity;
    }

    private AuthenticatedAccount loggedInAccount(String role, String staffName) {
        return new AuthenticatedAccount(
            1L,
            11L,
            "U001",
            staffName,
            role,
            "ACTIVE",
            "LOCAL_PASSWORD",
            Set.of(),
            List.of()
        );
    }

    private LinuxPasswordServerEntity buildServer(Long id, String hostname, String ip, String username, String password, String status) {
        LinuxPasswordServerEntity entity = new LinuxPasswordServerEntity();
        entity.setId(id);
        entity.setHostname(hostname);
        entity.setIp(ip);
        entity.setUsername(username);
        entity.setPassword(password);
        entity.setStatus(status);
        return entity;
    }

    private LinuxPasswordServerBusinessUnitEntity buildBusinessUnit(Long serverId, String businessUnit) {
        LinuxPasswordServerBusinessUnitEntity entity = new LinuxPasswordServerBusinessUnitEntity();
        entity.setServerId(serverId);
        entity.setBusinessUnit(businessUnit);
        return entity;
    }

    private LinuxPasswordDirectoryEntity buildDirectory(Long id, String name) {
        LinuxPasswordDirectoryEntity entity = new LinuxPasswordDirectoryEntity();
        entity.setId(id);
        entity.setName(name);
        return entity;
    }

    private LinuxPasswordAccessAuditEntity buildAudit(
            Long id,
            Long serverId,
            Long credentialId,
            String staffId,
            String staffName,
            String action,
            String result) {
        LinuxPasswordAccessAuditEntity entity = new LinuxPasswordAccessAuditEntity();
        entity.setId(id);
        entity.setAccountId(1L);
        entity.setStaffRecordId(11L);
        entity.setStaffId(staffId);
        entity.setStaffName(staffName);
        entity.setServerId(serverId);
        entity.setCredentialId(credentialId);
        entity.setAction(action);
        entity.setResult(result);
        entity.setClientIp("127.0.0.1");
        entity.setUserAgent("JUnit");
        entity.setCreateTime(LocalDateTime.of(2026, 4, 27, 10, 0));
        return entity;
    }
}
