package com.support.server.supportrosterserver.service.workspace;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.support.server.supportrosterserver.entity.workspace.ShiftDefinitionEntity;
import com.support.server.supportrosterserver.entity.workspace.ShiftDefinitionTeamRelEntity;
import com.support.server.supportrosterserver.entity.workspace.TeamEntity;
import com.support.server.supportrosterserver.mapper.RosterAssignmentMapper;
import com.support.server.supportrosterserver.mapper.ShiftDefinitionMapper;
import com.support.server.supportrosterserver.mapper.ShiftDefinitionTeamRelMapper;
import com.support.server.supportrosterserver.mapper.TeamMapper;
import com.support.server.supportrosterserver.service.auth.AuthContextService;

class WorkspaceShiftDefinitionServiceTest {

    private ShiftDefinitionMapper shiftDefinitionMapper;
    private ShiftDefinitionTeamRelMapper shiftDefinitionTeamRelMapper;
    private RosterAssignmentMapper rosterAssignmentMapper;
    private WorkspaceLookupService lookupService;
    private AuthContextService authContextService;
    private TeamMapper teamMapper;
    private WorkspaceShiftDefinitionService workspaceShiftDefinitionService;

    @BeforeEach
    void setUp() {
        shiftDefinitionMapper = mock(ShiftDefinitionMapper.class);
        shiftDefinitionTeamRelMapper = mock(ShiftDefinitionTeamRelMapper.class);
        rosterAssignmentMapper = mock(RosterAssignmentMapper.class);
        lookupService = mock(WorkspaceLookupService.class);
        authContextService = mock(AuthContextService.class);
        teamMapper = mock(TeamMapper.class);
        workspaceShiftDefinitionService = new WorkspaceShiftDefinitionService(
            shiftDefinitionMapper,
            shiftDefinitionTeamRelMapper,
            rosterAssignmentMapper,
            lookupService,
            authContextService,
            new WorkspaceShiftTimeSupport(),
            teamMapper
        );
    }

    @Test
    void shouldDeleteAssignmentsForDeletedShiftDefinition() {
        ShiftDefinitionEntity shiftDefinition = new ShiftDefinitionEntity();
        shiftDefinition.setId(51L);
        shiftDefinition.setTeamId(301L);
        shiftDefinition.setCode("AP-D");

        when(shiftDefinitionMapper.selectById(51L)).thenReturn(shiftDefinition);
        when(shiftDefinitionTeamRelMapper.selectList(any())).thenReturn(List.of(
            buildRelation(51L, 301L),
            buildRelation(51L, 302L)
        ));

        workspaceShiftDefinitionService.deleteShiftDefinition(51L);

        verify(shiftDefinitionTeamRelMapper).selectList(any());
        verify(rosterAssignmentMapper).delete(any(Wrapper.class));
        verify(shiftDefinitionTeamRelMapper).delete(any());
        verify(shiftDefinitionMapper).deleteById(51L);
    }

    @Test
    void shouldReorderTeamShiftDefinitionsWhenRequestMatchesExistingRelations() {
        ShiftDefinitionTeamRelEntity relationOne = buildRelation(51L, 301L);
        relationOne.setId(1L);
        relationOne.setDisplayOrder(0);
        ShiftDefinitionTeamRelEntity relationTwo = buildRelation(52L, 301L);
        relationTwo.setId(2L);
        relationTwo.setDisplayOrder(1);
        List<ShiftDefinitionTeamRelEntity> teamRelations = new ArrayList<>(List.of(relationOne, relationTwo));

        when(shiftDefinitionTeamRelMapper.selectList(any())).thenReturn(teamRelations);

        workspaceShiftDefinitionService.reorderShiftDefinitions(301L, List.of(52L, 51L));

        verify(authContextService).requireWritableTeam(301L);
        ArgumentCaptor<ShiftDefinitionTeamRelEntity> relationCaptor = ArgumentCaptor.forClass(ShiftDefinitionTeamRelEntity.class);
        verify(shiftDefinitionTeamRelMapper, times(2)).updateById(relationCaptor.capture());

        List<ShiftDefinitionTeamRelEntity> updatedRelations = relationCaptor.getAllValues();
        org.junit.jupiter.api.Assertions.assertEquals(2L, updatedRelations.get(0).getId());
        org.junit.jupiter.api.Assertions.assertEquals(0, updatedRelations.get(0).getDisplayOrder());
        org.junit.jupiter.api.Assertions.assertEquals(1L, updatedRelations.get(1).getId());
        org.junit.jupiter.api.Assertions.assertEquals(1, updatedRelations.get(1).getDisplayOrder());
    }

    private ShiftDefinitionTeamRelEntity buildRelation(Long shiftDefinitionId, Long teamId) {
        ShiftDefinitionTeamRelEntity relation = new ShiftDefinitionTeamRelEntity();
        relation.setShiftDefinitionId(shiftDefinitionId);
        relation.setTeamId(teamId);
        return relation;
    }

    private TeamEntity buildTeam(Long id) {
        TeamEntity team = new TeamEntity();
        team.setId(id);
        return team;
    }
}
