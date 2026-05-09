package com.support.server.supportrosterserver.service.workspace;

import static org.mockito.ArgumentMatchers.any;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.support.server.supportrosterserver.dto.ShiftCodeDto;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceShiftDefinitionDto;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceShiftDefinitionReorderRequest;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceShiftDefinitionUpsertRequest;
import com.support.server.supportrosterserver.entity.workspace.ShiftDefinitionEntity;
import com.support.server.supportrosterserver.entity.workspace.ShiftDefinitionTeamRelEntity;
import com.support.server.supportrosterserver.entity.workspace.TeamEntity;
import com.support.server.supportrosterserver.mapper.RosterAssignmentMapper;
import com.support.server.supportrosterserver.mapper.ShiftDefinitionMapper;
import com.support.server.supportrosterserver.mapper.ShiftDefinitionTeamRelMapper;
import com.support.server.supportrosterserver.service.auth.AuthContextService;

class WorkspaceShiftDefinitionServiceTest {

    private ShiftDefinitionMapper shiftDefinitionMapper;
    private ShiftDefinitionTeamRelMapper shiftDefinitionTeamRelMapper;
    private RosterAssignmentMapper rosterAssignmentMapper;
    private WorkspaceLookupService lookupService;
    private AuthContextService authContextService;
    private WorkspaceShiftDefinitionService workspaceShiftDefinitionService;

    @BeforeEach
    void setUp() {
        shiftDefinitionMapper = mock(ShiftDefinitionMapper.class);
        shiftDefinitionTeamRelMapper = mock(ShiftDefinitionTeamRelMapper.class);
        rosterAssignmentMapper = mock(RosterAssignmentMapper.class);
        lookupService = mock(WorkspaceLookupService.class);
        authContextService = mock(AuthContextService.class);
        workspaceShiftDefinitionService = new WorkspaceShiftDefinitionService(
            shiftDefinitionMapper,
            shiftDefinitionTeamRelMapper,
            rosterAssignmentMapper,
            lookupService,
            authContextService,
            new WorkspaceShiftTimeSupport()
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
    void shouldPersistTeamSpecificShiftOrder() {
        when(shiftDefinitionTeamRelMapper.selectList(any())).thenReturn(List.of(
            buildRelation(11L, 201L, 2),
            buildRelation(12L, 201L, 1)
        ));

        workspaceShiftDefinitionService.reorderShiftDefinitionsForTeam(
            new WorkspaceShiftDefinitionReorderRequest(201L, List.of(11L, 12L))
        );

        verify(shiftDefinitionTeamRelMapper).updateById(org.mockito.ArgumentMatchers.<ShiftDefinitionTeamRelEntity>argThat(rel ->
            rel.getTeamId().equals(201L) && rel.getShiftDefinitionId().equals(11L) && rel.getDisplayOrder().equals(0)
        ));
    }

    @Test
    void shouldUseTeamSpecificOrderForViewerShiftCodes() {
        when(shiftDefinitionMapper.selectList(any())).thenReturn(List.of(
            buildDefinition(11L, "B"),
            buildDefinition(12L, "A")
        ));
        when(shiftDefinitionTeamRelMapper.selectList(any())).thenReturn(List.of(
            buildRelation(11L, 201L, 0),
            buildRelation(12L, 201L, 1)
        ));

        List<ShiftCodeDto> result = workspaceShiftDefinitionService.listViewerShiftCodes();

        assertEquals(List.of("B", "A"), result.stream().map(ShiftCodeDto::getCode).toList());
    }

    @Test
    void shouldUsePrimaryTeamRelationForSharedShiftOrdering() {
        ShiftDefinitionEntity sharedLater = buildDefinition(11L, "B");
        sharedLater.setTeamId(202L);
        ShiftDefinitionEntity sharedFirst = buildDefinition(12L, "A");
        sharedFirst.setTeamId(202L);

        when(shiftDefinitionMapper.selectList(any())).thenReturn(List.of(sharedLater, sharedFirst));
        when(shiftDefinitionTeamRelMapper.selectList(any())).thenReturn(List.of(
            buildRelation(11L, 201L, 0),
            buildRelation(11L, 202L, 1),
            buildRelation(12L, 202L, 0)
        ));

        List<ShiftCodeDto> result = workspaceShiftDefinitionService.listViewerShiftCodes();

        assertEquals(List.of("A", "B"), result.stream().map(ShiftCodeDto::getCode).toList());
    }

    @Test
    void shouldPreserveExistingRelationsWhenUpdatingShiftDefinitionWithSameTeams() {
        ShiftDefinitionEntity existing = buildDefinition(51L, "A");
        existing.setTeamId(201L);
        WorkspaceShiftDefinitionUpsertRequest request = buildUpsertRequest(List.of(201L), "A");
        TeamEntity team = buildTeam(201L, "Tier 1");

        when(shiftDefinitionMapper.selectById(51L)).thenReturn(existing);
        when(shiftDefinitionMapper.selectList(any())).thenReturn(List.of());
        when(lookupService.teamMap()).thenReturn(Map.of(201L, team));
        when(lookupService.requireTeam(201L)).thenReturn(team);
        when(lookupService.normalizeWorkspaceTimezone("Asia/Shanghai")).thenReturn("Asia/Shanghai");
        when(shiftDefinitionTeamRelMapper.selectList(any())).thenReturn(
            List.of(buildRelation(51L, 201L, 3)),
            List.of(buildRelation(51L, 201L, 3)),
            List.of(buildRelation(51L, 201L, 3))
        );

        workspaceShiftDefinitionService.updateShiftDefinition(51L, request);

        verify(shiftDefinitionTeamRelMapper, never()).delete(any());
        verify(shiftDefinitionTeamRelMapper, never()).insert(org.mockito.ArgumentMatchers.any(ShiftDefinitionTeamRelEntity.class));
    }

    @Test
    void shouldAppendNewTeamRelationsAfterExistingDisplayOrder() {
        ShiftDefinitionEntity existing = buildDefinition(51L, "A");
        existing.setTeamId(201L);
        WorkspaceShiftDefinitionUpsertRequest request = buildUpsertRequest(List.of(201L, 202L), "A");
        TeamEntity team201 = buildTeam(201L, "Tier 1");
        TeamEntity team202 = buildTeam(202L, "Tier 2");

        when(shiftDefinitionMapper.selectById(51L)).thenReturn(existing);
        when(shiftDefinitionMapper.selectList(any())).thenReturn(List.of());
        when(lookupService.teamMap()).thenReturn(Map.of(201L, team201, 202L, team202));
        when(lookupService.requireTeam(201L)).thenReturn(team201);
        when(lookupService.requireTeam(202L)).thenReturn(team202);
        when(lookupService.normalizeWorkspaceTimezone("Asia/Shanghai")).thenReturn("Asia/Shanghai");
        when(shiftDefinitionTeamRelMapper.selectList(any())).thenReturn(
            List.of(buildRelation(51L, 201L, 3)),
            List.of(buildRelation(51L, 201L, 3)),
            List.of(buildRelation(88L, 202L, 4)),
            List.of(buildRelation(51L, 201L, 3), buildRelation(51L, 202L, 5))
        );

        workspaceShiftDefinitionService.updateShiftDefinition(51L, request);

        verify(shiftDefinitionTeamRelMapper).insert(org.mockito.ArgumentMatchers.argThat((ShiftDefinitionTeamRelEntity rel) ->
            rel.getShiftDefinitionId().equals(51L) && rel.getTeamId().equals(202L) && rel.getDisplayOrder().equals(5)
        ));
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
        when(shiftDefinitionTeamRelMapper.selectList(any())).thenReturn(
            List.of(buildRelation(51L, 201L, 3), buildRelation(51L, 202L, 1)),
            List.of(buildRelation(51L, 201L, 3), buildRelation(51L, 202L, 1)),
            List.of(buildRelation(51L, 201L, 3), buildRelation(51L, 202L, 1))
        );

        workspaceShiftDefinitionService.updateShiftDefinition(51L, request);

        verify(shiftDefinitionMapper).updateById(org.mockito.ArgumentMatchers.argThat((ShiftDefinitionEntity entity) ->
            entity.getId().equals(51L) && entity.getTeamId().equals(201L)
        ));
    }

    @Test
    void shouldReturnPrimaryTeamFromEntityInsteadOfFirstRelation() {
        ShiftDefinitionEntity existing = buildDefinition(51L, "A");
        existing.setTeamId(202L);
        TeamEntity team201 = buildTeam(201L, "Tier 1");
        TeamEntity team202 = buildTeam(202L, "Tier 2");

        when(shiftDefinitionMapper.selectById(51L)).thenReturn(existing);
        when(lookupService.teamMap()).thenReturn(Map.of(201L, team201, 202L, team202));
        when(shiftDefinitionTeamRelMapper.selectList(any())).thenReturn(List.of(
            buildRelation(51L, 201L, 0),
            buildRelation(51L, 202L, 1)
        ));

        WorkspaceShiftDefinitionDto result = workspaceShiftDefinitionService.getShiftDefinition(51L);

        assertEquals(202L, result.getTeamId());
        assertEquals("Tier 2", result.getTeamName());
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
