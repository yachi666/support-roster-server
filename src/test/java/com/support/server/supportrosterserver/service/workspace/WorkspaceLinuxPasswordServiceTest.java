package com.support.server.supportrosterserver.service.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.support.server.supportrosterserver.auth.AuthenticatedAccount;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceLinuxPasswordDto;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceLinuxPasswordListResponse;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceLinuxPasswordUpsertRequest;
import com.support.server.supportrosterserver.entity.workspace.LinuxPasswordServerBusinessUnitEntity;
import com.support.server.supportrosterserver.entity.workspace.LinuxPasswordDirectoryEntity;
import com.support.server.supportrosterserver.entity.workspace.LinuxPasswordServerEntity;
import com.support.server.supportrosterserver.exception.ForbiddenException;
import com.support.server.supportrosterserver.mapper.LinuxPasswordDirectoryMapper;
import com.support.server.supportrosterserver.mapper.LinuxPasswordServerBusinessUnitMapper;
import com.support.server.supportrosterserver.mapper.LinuxPasswordServerMapper;
import com.support.server.supportrosterserver.service.auth.AuthContextService;

class WorkspaceLinuxPasswordServiceTest {

    private LinuxPasswordServerMapper linuxPasswordServerMapper;
    private LinuxPasswordServerBusinessUnitMapper linuxPasswordServerBusinessUnitMapper;
    private LinuxPasswordDirectoryMapper linuxPasswordDirectoryMapper;
    private AuthContextService authContextService;
    private WorkspaceOperationLogService workspaceOperationLogService;
    private WorkspaceLinuxPasswordService workspaceLinuxPasswordService;

    @BeforeEach
    void setUp() {
        linuxPasswordServerMapper = mock(LinuxPasswordServerMapper.class);
        linuxPasswordServerBusinessUnitMapper = mock(LinuxPasswordServerBusinessUnitMapper.class);
        linuxPasswordDirectoryMapper = mock(LinuxPasswordDirectoryMapper.class);
        authContextService = mock(AuthContextService.class);
        workspaceOperationLogService = mock(WorkspaceOperationLogService.class);
        workspaceLinuxPasswordService = new WorkspaceLinuxPasswordService(
            linuxPasswordServerMapper,
            linuxPasswordServerBusinessUnitMapper,
            linuxPasswordDirectoryMapper,
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
    void shouldDeleteOrphanDirectoriesWhenDeletingServer() {
        when(linuxPasswordServerMapper.selectById(1L)).thenReturn(buildServer(1L, "fin-db-01", "10.0.10.5", "postgres", "P@ssw0rdFin1!", "online"));
        when(linuxPasswordServerBusinessUnitMapper.selectList(any())).thenReturn(
            List.of(buildBusinessUnit(1L, "Finance")),
            List.of()
        );

        workspaceLinuxPasswordService.deleteServer(1L);

        verify(linuxPasswordDirectoryMapper).delete(any());
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
}
