package com.support.server.supportrosterserver.service.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.support.server.supportrosterserver.dto.workspace.WorkspaceTeamDto;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceTeamUpsertRequest;
import com.support.server.supportrosterserver.entity.workspace.TeamEntity;
import com.support.server.supportrosterserver.exception.BadRequestException;
import com.support.server.supportrosterserver.mapper.RosterAssignmentMapper;
import com.support.server.supportrosterserver.mapper.ShiftDefinitionTeamRelMapper;
import com.support.server.supportrosterserver.mapper.StaffMapper;
import com.support.server.supportrosterserver.mapper.TeamMapper;
import com.support.server.supportrosterserver.mapper.WorkspaceAccountTeamScopeMapper;
import com.support.server.supportrosterserver.service.auth.AuthContextService;

class WorkspaceTeamServiceTest {

    private TeamMapper teamMapper;
    private WorkspaceLookupService lookupService;
    private WorkspaceOperationLogService workspaceOperationLogService;
    private StaffMapper staffMapper;
    private WorkspaceAccountTeamScopeMapper workspaceAccountTeamScopeMapper;
    private ShiftDefinitionTeamRelMapper shiftDefinitionTeamRelMapper;
    private RosterAssignmentMapper rosterAssignmentMapper;
    private WorkspaceTeamService workspaceTeamService;

    @BeforeEach
    void setUp() {
        teamMapper = mock(TeamMapper.class);
        lookupService = mock(WorkspaceLookupService.class);
        workspaceOperationLogService = mock(WorkspaceOperationLogService.class);
        staffMapper = mock(StaffMapper.class);
        workspaceAccountTeamScopeMapper = mock(WorkspaceAccountTeamScopeMapper.class);
        shiftDefinitionTeamRelMapper = mock(ShiftDefinitionTeamRelMapper.class);
        rosterAssignmentMapper = mock(RosterAssignmentMapper.class);
        AuthContextService authContextService = mock(AuthContextService.class);
        when(authContextService.currentActor(any())).thenReturn("Admin");
        workspaceTeamService = new WorkspaceTeamService(
            teamMapper,
            lookupService,
            authContextService,
            workspaceOperationLogService,
            staffMapper,
            workspaceAccountTeamScopeMapper,
            shiftDefinitionTeamRelMapper,
            rosterAssignmentMapper
        );
    }

    @Test
    void shouldRenameTeamWithoutChangingIdentifier() {
        TeamEntity team = buildTeam(10L, "Legacy Team");
        when(lookupService.requireTeam(10L)).thenReturn(team);
        when(lookupService.listTeams()).thenReturn(List.of(team));

        WorkspaceTeamUpsertRequest request = new WorkspaceTeamUpsertRequest();
        request.setName("Renamed Team");
        request.setColor("#00AA88");
        request.setDisplayOrder(2);
        request.setVisible(Boolean.TRUE);
        request.setDescription("Updated team name");

        WorkspaceTeamDto updated = workspaceTeamService.updateTeam(10L, request);

        assertEquals(10L, updated.getId());
        assertEquals("Renamed Team", updated.getName());
        verify(teamMapper).updateById(team);
        verify(workspaceOperationLogService).log(
            "Admin",
            "Rename workspace team",
            "workspace_team",
            10L,
            "From=Legacy Team; To=Renamed Team"
        );
    }

    @Test
    void shouldBlockDeletingTeamWithDependencies() {
        TeamEntity team = buildTeam(10L, "Ops");
        when(lookupService.requireTeam(10L)).thenReturn(team);
        when(staffMapper.selectCount(any())).thenReturn(1L);

        assertThrows(BadRequestException.class, () -> workspaceTeamService.deleteTeam(10L));

        verify(teamMapper, never()).deleteById(anyLong());
    }

    private TeamEntity buildTeam(Long id, String name) {
        TeamEntity team = new TeamEntity();
        team.setId(id);
        team.setName(name);
        team.setColor("#111111");
        team.setDisplayOrder(1);
        team.setVisible(Boolean.TRUE);
        return team;
    }
}
