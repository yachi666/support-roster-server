package com.support.server.supportrosterserver.service.workspace;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.support.server.supportrosterserver.dto.employee.EmployeeDirectoryLookupResponse;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceStaffDto;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceStaffBatchCreateRequest;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceStaffUpsertRequest;
import com.support.server.supportrosterserver.entity.auth.WorkspaceAccountEntity;
import com.support.server.supportrosterserver.entity.workspace.StaffEntity;
import com.support.server.supportrosterserver.entity.workspace.TeamEntity;
import com.support.server.supportrosterserver.exception.BadRequestException;
import com.support.server.supportrosterserver.mapper.RosterAssignmentMapper;
import com.support.server.supportrosterserver.mapper.StaffMapper;
import com.support.server.supportrosterserver.mapper.WorkspaceAccountMapper;
import com.support.server.supportrosterserver.mapper.WorkspaceAccountTeamScopeMapper;
import com.support.server.supportrosterserver.service.AvatarUrlResolver;
import com.support.server.supportrosterserver.service.EmployeeDirectoryClient;
import com.support.server.supportrosterserver.service.auth.AuthContextService;
import com.support.server.supportrosterserver.service.auth.AuthTokenVersionService;

class WorkspaceStaffServiceTest {

    private StaffMapper staffMapper;
    private WorkspaceAccountMapper workspaceAccountMapper;
    private WorkspaceAccountTeamScopeMapper workspaceAccountTeamScopeMapper;
    private EmployeeDirectoryClient employeeDirectoryClient;
    private WorkspaceOperationLogService workspaceOperationLogService;
    private AuthTokenVersionService authTokenVersionService;
    private WorkspaceStaffService workspaceStaffService;

    @BeforeEach
    void setUp() {
        staffMapper = mock(StaffMapper.class);
        workspaceAccountMapper = mock(WorkspaceAccountMapper.class);
        workspaceAccountTeamScopeMapper = mock(WorkspaceAccountTeamScopeMapper.class);
        employeeDirectoryClient = mock(EmployeeDirectoryClient.class);
        workspaceOperationLogService = mock(WorkspaceOperationLogService.class);
        authTokenVersionService = mock(AuthTokenVersionService.class);
        WorkspaceLookupService lookupService = mock(WorkspaceLookupService.class);
        RosterAssignmentMapper rosterAssignmentMapper = mock(RosterAssignmentMapper.class);
        AuthContextService authContextService = mock(AuthContextService.class);
        when(staffMapper.selectList(any())).thenReturn(List.of());
        when(lookupService.teamMap()).thenReturn(Map.of(10L, buildTeam(10L, "Ops")));
        when(lookupService.requireTeam(10L)).thenReturn(buildTeam(10L, "Ops"));
        when(lookupService.normalizeWorkspaceTimezone(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(rosterAssignmentMapper.selectList(any())).thenReturn(List.of());
        when(authContextService.currentActor(any())).thenReturn("Admin");
        workspaceStaffService = new WorkspaceStaffService(
            mock(AvatarUrlResolver.class),
            employeeDirectoryClient,
            staffMapper,
            rosterAssignmentMapper,
            lookupService,
            workspaceAccountMapper,
            workspaceAccountTeamScopeMapper,
            authContextService,
            authTokenVersionService,
            workspaceOperationLogService
        );
    }

    @Test
    void shouldCreateStaffWithLookupDataAndManualPhone() {
        Map<Long, StaffEntity> insertedStaff = new LinkedHashMap<>();
        when(staffMapper.insert(any(StaffEntity.class))).thenAnswer(invocation -> {
            StaffEntity entity = invocation.getArgument(0);
            long id = insertedStaff.size() + 1L;
            StaffEntity stored = new StaffEntity();
            stored.setId(id);
            stored.setStaffCode(entity.getStaffCode());
            stored.setName(entity.getName());
            stored.setEmail(entity.getEmail());
            stored.setPhone(entity.getPhone());
            stored.setRegion(entity.getRegion());
            stored.setTimezone(entity.getTimezone());
            stored.setRoleName(entity.getRoleName());
            stored.setTeamId(entity.getTeamId());
            stored.setStatus(entity.getStatus());
            stored.setNotes(entity.getNotes());
            entity.setId(id);
            insertedStaff.put(id, stored);
            return 1;
        });
        when(staffMapper.selectById(anyLong())).thenAnswer(invocation -> insertedStaff.get(invocation.getArgument(0)));
        when(employeeDirectoryClient.getEmployee("A003")).thenReturn(
            new EmployeeDirectoryLookupResponse("xian", "China", "Zhang San", "zhang.san@example.com", "scheduler")
        );

        WorkspaceStaffUpsertRequest request = new WorkspaceStaffUpsertRequest();
        request.setStaffCode("A003");
        request.setPhone("+86-13800000000");
        request.setTeamId(10L);
        request.setTimezone("UTC");
        request.setStatus("ACTIVE");
        request.setNotes("Manual phone");

        WorkspaceStaffDto created = workspaceStaffService.createStaff(request);

        Assertions.assertEquals("A003", created.getStaffCode());
        Assertions.assertEquals("Zhang San", created.getName());
        Assertions.assertEquals("zhang.san@example.com", created.getEmail());
        Assertions.assertEquals("+86-13800000000", created.getPhone());
        Assertions.assertEquals("xian, China", created.getRegion());
        Assertions.assertEquals("scheduler", created.getRoleName());
        verify(workspaceOperationLogService).log("Admin", "Create workspace staff", "workspace_staff", created.getId(), "Staff ID=A003");
    }

    @Test
    void shouldSyncLinkedAccountStaffCodeWhenStaffCodeChanges() {
        StaffEntity staff = new StaffEntity();
        staff.setId(1L);
        staff.setStaffCode("A001");
        staff.setTeamId(10L);
        when(staffMapper.selectById(1L)).thenReturn(staff);

        WorkspaceAccountEntity account = new WorkspaceAccountEntity();
        account.setId(2L);
        account.setStaffId(1L);
        account.setStaffCode("A001");
        when(workspaceAccountMapper.selectOne(any())).thenReturn(account);

        WorkspaceStaffUpsertRequest request = new WorkspaceStaffUpsertRequest();
        request.setStaffCode("A009");
        request.setName("Alice");
        request.setTeamId(10L);
        request.setTimezone("UTC");
        request.setStatus("ACTIVE");

        workspaceStaffService.updateStaff(1L, request);

        verify(workspaceAccountMapper).updateById(account);
    }

    @Test
    void shouldCreateBatchStaffFromEmployeeLookup() {
        Map<Long, StaffEntity> insertedStaff = new LinkedHashMap<>();
        when(staffMapper.insert(any(StaffEntity.class))).thenAnswer(invocation -> {
            StaffEntity entity = invocation.getArgument(0);
            long id = insertedStaff.size() + 1L;
            StaffEntity stored = new StaffEntity();
            stored.setId(id);
            stored.setStaffCode(entity.getStaffCode());
            stored.setName(entity.getName());
            stored.setEmail(entity.getEmail());
            stored.setRegion(entity.getRegion());
            stored.setTimezone(entity.getTimezone());
            stored.setRoleName(entity.getRoleName());
            stored.setTeamId(entity.getTeamId());
            stored.setStatus(entity.getStatus());
            stored.setNotes(entity.getNotes());
            entity.setId(id);
            insertedStaff.put(id, stored);
            return 1;
        });
        when(staffMapper.selectById(anyLong())).thenAnswer(invocation -> insertedStaff.get(invocation.getArgument(0)));
        when(employeeDirectoryClient.getEmployee("A001")).thenReturn(
            new EmployeeDirectoryLookupResponse("xian", "China", "Li Lei", "li.lei@example.com", "engineer")
        );
        when(employeeDirectoryClient.getEmployee("A002")).thenReturn(
            new EmployeeDirectoryLookupResponse("chengdu", "China", "Han Meimei", "han.meimei@example.com", "lead")
        );

        WorkspaceStaffBatchCreateRequest request = new WorkspaceStaffBatchCreateRequest();
        request.setStaffCodes(List.of("A001", "A002"));
        request.setTeamId(10L);
        request.setTimezone("UTC");
        request.setStatus("ACTIVE");
        request.setNotes("Synced from employee directory");

        var createdStaff = workspaceStaffService.createStaffBatch(request);

        Assertions.assertEquals(2, createdStaff.size());
        Assertions.assertEquals("Li Lei", createdStaff.get(0).getName());
        Assertions.assertEquals("li.lei@example.com", createdStaff.get(0).getEmail());
        Assertions.assertEquals("xian, China", createdStaff.get(0).getRegion());
        Assertions.assertEquals("engineer", createdStaff.get(0).getRoleName());
        Assertions.assertEquals("ACTIVE", createdStaff.get(0).getStatus());
        Assertions.assertEquals("Han Meimei", createdStaff.get(1).getName());
    }

    @Test
    void shouldRejectDuplicateStaffCodesInBatchRequest() {
        WorkspaceStaffBatchCreateRequest request = new WorkspaceStaffBatchCreateRequest();
        request.setStaffCodes(List.of("A001", "A001"));
        request.setTeamId(10L);
        request.setTimezone("UTC");

        BadRequestException exception = Assertions.assertThrows(
            BadRequestException.class,
            () -> workspaceStaffService.createStaffBatch(request)
        );

        Assertions.assertEquals("Duplicate staff IDs in request: A001", exception.getMessage());
    }

    @Test
    void shouldFallbackToStaffIdWhenEmployeeLookupFails() {
        Map<Long, StaffEntity> insertedStaff = new LinkedHashMap<>();
        when(staffMapper.insert(any(StaffEntity.class))).thenAnswer(invocation -> {
            StaffEntity entity = invocation.getArgument(0);
            long id = insertedStaff.size() + 1L;
            StaffEntity stored = new StaffEntity();
            stored.setId(id);
            stored.setStaffCode(entity.getStaffCode());
            stored.setName(entity.getName());
            stored.setEmail(entity.getEmail());
            stored.setRegion(entity.getRegion());
            stored.setTimezone(entity.getTimezone());
            stored.setRoleName(entity.getRoleName());
            stored.setTeamId(entity.getTeamId());
            stored.setStatus(entity.getStatus());
            stored.setNotes(entity.getNotes());
            entity.setId(id);
            insertedStaff.put(id, stored);
            return 1;
        });
        when(staffMapper.selectById(anyLong())).thenAnswer(invocation -> insertedStaff.get(invocation.getArgument(0)));
        when(employeeDirectoryClient.getEmployee("A404")).thenThrow(new IllegalStateException("lookup failed"));

        WorkspaceStaffBatchCreateRequest request = new WorkspaceStaffBatchCreateRequest();
        request.setStaffCodes(List.of("A404"));
        request.setTeamId(10L);
        request.setTimezone("UTC");
        request.setStatus("ACTIVE");

        var createdStaff = workspaceStaffService.createStaffBatch(request);

        Assertions.assertEquals(1, createdStaff.size());
        Assertions.assertEquals("A404", createdStaff.get(0).getStaffCode());
        Assertions.assertEquals("A404", createdStaff.get(0).getName());
        Assertions.assertNull(createdStaff.get(0).getEmail());
        Assertions.assertNull(createdStaff.get(0).getRegion());
        Assertions.assertNull(createdStaff.get(0).getRoleName());
        Assertions.assertEquals("ACTIVE", createdStaff.get(0).getStatus());
    }

    @Test
    void shouldDeleteLinkedAccountAndScopesWhenDeletingStaff() {
        StaffEntity staff = new StaffEntity();
        staff.setId(1L);
        staff.setStaffCode("A001");
        staff.setTeamId(10L);
        when(staffMapper.selectById(1L)).thenReturn(staff);

        WorkspaceAccountEntity account = new WorkspaceAccountEntity();
        account.setId(2L);
        account.setStaffId(1L);
        when(workspaceAccountMapper.selectOne(any())).thenReturn(account);

        workspaceStaffService.deleteStaff(1L);

        verify(authTokenVersionService).bumpTokenVersion(account);
        verify(workspaceAccountTeamScopeMapper).delete(any());
        verify(workspaceAccountMapper).deleteById(2L);
        verify(staffMapper).deleteById(1L);
        verify(workspaceOperationLogService).log("Admin", "Delete workspace staff", "workspace_staff", 1L, "Staff ID=A001; removed linked workspace account");
    }

    @Test
    void shouldDeleteStaffWithoutTouchingAccountsWhenNoLinkedAccountExists() {
        StaffEntity staff = new StaffEntity();
        staff.setId(1L);
        staff.setStaffCode("A001");
        staff.setTeamId(10L);
        when(staffMapper.selectById(1L)).thenReturn(staff);
        when(workspaceAccountMapper.selectOne(any())).thenReturn(null);

        workspaceStaffService.deleteStaff(1L);

        verify(workspaceAccountTeamScopeMapper, never()).delete(any());
        verify(workspaceAccountMapper, never()).deleteById(anyLong());
        verify(staffMapper).deleteById(1L);
    }

    private TeamEntity buildTeam(Long id, String name) {
        TeamEntity team = new TeamEntity();
        team.setId(id);
        team.setName(name);
        return team;
    }
}
