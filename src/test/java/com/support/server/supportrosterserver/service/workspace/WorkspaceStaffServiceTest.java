package com.support.server.supportrosterserver.service.workspace;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.support.server.supportrosterserver.dto.workspace.WorkspaceStaffUpsertRequest;
import com.support.server.supportrosterserver.entity.auth.WorkspaceAccountEntity;
import com.support.server.supportrosterserver.entity.workspace.StaffEntity;
import com.support.server.supportrosterserver.entity.workspace.TeamEntity;
import com.support.server.supportrosterserver.mapper.RosterAssignmentMapper;
import com.support.server.supportrosterserver.mapper.StaffMapper;
import com.support.server.supportrosterserver.mapper.WorkspaceAccountMapper;
import com.support.server.supportrosterserver.service.AvatarUrlResolver;
import com.support.server.supportrosterserver.service.auth.AuthContextService;

class WorkspaceStaffServiceTest {

    private StaffMapper staffMapper;
    private WorkspaceAccountMapper workspaceAccountMapper;
    private WorkspaceStaffService workspaceStaffService;

    @BeforeEach
    void setUp() {
        staffMapper = mock(StaffMapper.class);
        workspaceAccountMapper = mock(WorkspaceAccountMapper.class);
        WorkspaceLookupService lookupService = mock(WorkspaceLookupService.class);
        RosterAssignmentMapper rosterAssignmentMapper = mock(RosterAssignmentMapper.class);
        when(lookupService.teamMap()).thenReturn(Map.of(10L, buildTeam(10L, "Ops")));
        when(lookupService.requireTeam(10L)).thenReturn(buildTeam(10L, "Ops"));
        when(lookupService.normalizeWorkspaceTimezone(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(rosterAssignmentMapper.selectList(any())).thenReturn(List.of());
        workspaceStaffService = new WorkspaceStaffService(
            mock(AvatarUrlResolver.class),
            staffMapper,
            rosterAssignmentMapper,
            lookupService,
            workspaceAccountMapper,
            mock(AuthContextService.class)
        );
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

    private TeamEntity buildTeam(Long id, String name) {
        TeamEntity team = new TeamEntity();
        team.setId(id);
        team.setName(name);
        return team;
    }
}
