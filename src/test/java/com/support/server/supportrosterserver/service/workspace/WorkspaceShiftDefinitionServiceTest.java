package com.support.server.supportrosterserver.service.workspace;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceShiftDefinitionUpsertRequest;
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

    @Test
    void shouldKeepPrimaryTeamWhenUpdatingWithoutRemovingIt() {
        ShiftDefinitionEntity existing = buildDefinition(51L, "A");
        existing.setTeamId(201L);
        WorkspaceShiftDefinitionUpsertRequest request = buildUpsertRequest(List.of(202L, 201L), "A");
        TeamEntity team201 = buildTeam(201L, "Tier 1");
        TeamEntity team202 = buildTeam(202L, "Tier 2");

        when(shiftDefinitionMapper.selectById(51L)).thenReturn(existing);
        when(shiftDefinitionMapper.selectList(any())).thenReturn(List.of());
        when(lookupService.teamMap()).thenReturn(Map.of(201L, team201, 202L, team202));
        when(lookupService.requireTeam(201L)).thenReturn(team201);
        when(lookupService.requireTeam(202L)).thenReturn(team202);
        when(lookupService.normalizeWorkspaceTimezone("Asia/Shanghai")).thenReturn("Asia/Shanghai");
        when(teamMapper.selectOne(any())).thenReturn(team201, team202);
        when(shiftDefinitionTeamRelMapper.selectList(any())).thenReturn(
            List.of(buildRelation(51L, 201L, 3), buildRelation(51L, 202L, 1)),
            List.of(buildRelation(51L, 201L, 3), buildRelation(51L, 202L, 1)),
            List.of(buildRelation(51L, 201L, 3), buildRelation(51L, 202L, 1))
        );

        workspaceShiftDefinitionService.updateShiftDefinition(51L, request);

        ArgumentCaptor<ShiftDefinitionEntity> definitionCaptor = ArgumentCaptor.forClass(ShiftDefinitionEntity.class);
        verify(shiftDefinitionMapper).updateById(definitionCaptor.capture());
        org.junit.jupiter.api.Assertions.assertEquals(201L, definitionCaptor.getValue().getTeamId());
    }

    private ShiftDefinitionTeamRelEntity buildRelation(Long shiftDefinitionId, Long teamId) {
        return buildRelation(shiftDefinitionId, teamId, 0);
    }

    private ShiftDefinitionTeamRelEntity buildRelation(Long shiftDefinitionId, Long teamId, Integer displayOrder) {
        ShiftDefinitionTeamRelEntity relation = new ShiftDefinitionTeamRelEntity();
        relation.setShiftDefinitionId(shiftDefinitionId);
        relation.setTeamId(teamId);
        relation.setDisplayOrder(displayOrder);
        return relation;
    }

    private ShiftDefinitionEntity buildDefinition(Long id, String code) {
        ShiftDefinitionEntity definition = new ShiftDefinitionEntity();
        definition.setId(id);
        definition.setCode(code);
        definition.setMeaning(code + "-meaning");
        definition.setVisible(true);
        return definition;
    }

    private WorkspaceShiftDefinitionUpsertRequest buildUpsertRequest(List<Long> teamIds, String code) {
        WorkspaceShiftDefinitionUpsertRequest request = new WorkspaceShiftDefinitionUpsertRequest();
        request.setTeamIds(teamIds);
        request.setCode(code);
        request.setMeaning(code + "-meaning");
        request.setStartTime(LocalTime.of(9, 0));
        request.setDurationMinutes(480);
        request.setTimezone("Asia/Shanghai");
        request.setPrimaryShift(true);
        request.setVisible(true);
        return request;
    }

    private TeamEntity buildTeam(Long id, String name) {
        TeamEntity team = new TeamEntity();
        team.setId(id);
        team.setName(name);
        return team;
    }
}
