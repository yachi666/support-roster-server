package com.support.server.supportrosterserver.service.workspace;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.support.server.supportrosterserver.entity.workspace.ShiftDefinitionEntity;
import com.support.server.supportrosterserver.entity.workspace.ShiftDefinitionTeamRelEntity;
import com.support.server.supportrosterserver.mapper.RosterAssignmentMapper;
import com.support.server.supportrosterserver.mapper.ShiftDefinitionMapper;
import com.support.server.supportrosterserver.mapper.ShiftDefinitionTeamRelMapper;

class WorkspaceShiftDefinitionServiceTest {

    private ShiftDefinitionMapper shiftDefinitionMapper;
    private ShiftDefinitionTeamRelMapper shiftDefinitionTeamRelMapper;
    private RosterAssignmentMapper rosterAssignmentMapper;
    private WorkspaceLookupService lookupService;
    private WorkspaceShiftDefinitionService workspaceShiftDefinitionService;

    @BeforeEach
    void setUp() {
        shiftDefinitionMapper = mock(ShiftDefinitionMapper.class);
        shiftDefinitionTeamRelMapper = mock(ShiftDefinitionTeamRelMapper.class);
        rosterAssignmentMapper = mock(RosterAssignmentMapper.class);
        lookupService = mock(WorkspaceLookupService.class);
        workspaceShiftDefinitionService = new WorkspaceShiftDefinitionService(
            shiftDefinitionMapper,
            shiftDefinitionTeamRelMapper,
            rosterAssignmentMapper,
            lookupService
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

    private ShiftDefinitionTeamRelEntity buildRelation(Long shiftDefinitionId, Long teamId) {
        ShiftDefinitionTeamRelEntity relation = new ShiftDefinitionTeamRelEntity();
        relation.setShiftDefinitionId(shiftDefinitionId);
        relation.setTeamId(teamId);
        return relation;
    }
}
