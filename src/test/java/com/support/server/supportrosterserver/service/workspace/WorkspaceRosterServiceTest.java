package com.support.server.supportrosterserver.service.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.support.server.supportrosterserver.dto.workspace.WorkspaceMonthlyRosterResponse;
import com.support.server.supportrosterserver.entity.workspace.ShiftDefinitionEntity;
import com.support.server.supportrosterserver.entity.workspace.ShiftDefinitionTeamRelEntity;
import com.support.server.supportrosterserver.entity.workspace.StaffEntity;
import com.support.server.supportrosterserver.entity.workspace.TeamEntity;
import com.support.server.supportrosterserver.mapper.RosterAssignmentMapper;
import com.support.server.supportrosterserver.mapper.ShiftDefinitionMapper;
import com.support.server.supportrosterserver.mapper.ShiftDefinitionTeamRelMapper;
import com.support.server.supportrosterserver.mapper.StaffMapper;
import com.support.server.supportrosterserver.service.AvatarUrlResolver;

class WorkspaceRosterServiceTest {

    private StaffMapper staffMapper;
    private ShiftDefinitionMapper shiftDefinitionMapper;
    private ShiftDefinitionTeamRelMapper shiftDefinitionTeamRelMapper;
    private WorkspaceLookupService lookupService;
    private WorkspaceValidationService validationService;
    private WorkspaceRosterService workspaceRosterService;

    @BeforeEach
    void setUp() {
        staffMapper = mock(StaffMapper.class);
        shiftDefinitionMapper = mock(ShiftDefinitionMapper.class);
        shiftDefinitionTeamRelMapper = mock(ShiftDefinitionTeamRelMapper.class);
        lookupService = mock(WorkspaceLookupService.class);
        validationService = mock(WorkspaceValidationService.class);

        workspaceRosterService = new WorkspaceRosterService(
            staffMapper,
            shiftDefinitionMapper,
            shiftDefinitionTeamRelMapper,
            mock(RosterAssignmentMapper.class),
            lookupService,
            validationService,
            new AvatarUrlResolver("https://photos.global.image/casual/square")
        );
    }

    @Test
    void shouldReturnTeamScopedShiftOptionsForMonthlyRoster() {
        TeamEntity team = new TeamEntity();
        team.setId(301L);
        team.setTeamCode("china-support");
        team.setName("China Support");
        team.setColor("#0f766e");
        team.setVisible(true);

        StaffEntity alice = new StaffEntity();
        alice.setId(401L);
        alice.setStaffCode("401");
        alice.setName("Alice");
        alice.setRoleName("Analyst");
        alice.setTeamId(301L);

        StaffEntity bob = new StaffEntity();
        bob.setId(402L);
        bob.setStaffCode("402X9");
        bob.setName("Bob");
        bob.setRoleName("Escalation");
        bob.setTeamId(301L);

        when(lookupService.teamMap()).thenReturn(Map.of(301L, team));
        when(lookupService.listTeams()).thenReturn(List.of(team));
        when(staffMapper.selectList(any())).thenReturn(List.of(alice, bob));
        when(shiftDefinitionMapper.selectList(any())).thenReturn(List.of(
            buildShiftDefinition(501L, 301L, "AP-D", "#ef4444"),
            buildShiftDefinition(502L, 301L, "AP-N", "#f97316"),
            buildShiftDefinition(503L, 301L, "L2-D", "#22c55e")
        ));
        when(shiftDefinitionTeamRelMapper.selectList(any())).thenReturn(List.of(
            buildRelation(501L, 301L),
            buildRelation(502L, 301L),
            buildRelation(503L, 301L)
        ));
        when(validationService.validateLiveData(any())).thenReturn(List.of());

        WorkspaceMonthlyRosterResponse response = workspaceRosterService.getMonthlyRoster(2026, 3);

        assertEquals(1, response.getGroups().size());
        assertEquals("301", response.getGroups().get(0).getStaff().get(0).getTeamId().toString());
        assertEquals("301", response.getGroups().get(0).getStaff().get(1).getTeamId().toString());
        assertEquals("https://photos.global.image/casual/square/401/401.jpg", response.getGroups().get(0).getStaff().get(0).getAvatar());
        assertEquals("https://photos.global.image/casual/square/402X/402X9.jpg", response.getGroups().get(0).getStaff().get(1).getAvatar());
        assertIterableEquals(List.of("AP-D", "AP-N", "L2-D"), response.getShiftCodeOptions());
        assertIterableEquals(List.of("AP-D", "AP-N", "L2-D"), response.getShiftCodeOptionsByTeam().get(301L));
        assertEquals("#ef4444", response.getShiftCodeColorMap().get("AP-D"));
        assertEquals("AP-D", response.getShiftDetailsByTeam().get(301L).get("AP-D").getCode());
        assertTrue(response.getValidationWarning().isEmpty());
    }

    private ShiftDefinitionTeamRelEntity buildRelation(Long shiftDefinitionId, Long teamId) {
        ShiftDefinitionTeamRelEntity relation = new ShiftDefinitionTeamRelEntity();
        relation.setShiftDefinitionId(shiftDefinitionId);
        relation.setTeamId(teamId);
        return relation;
    }

    private ShiftDefinitionEntity buildShiftDefinition(Long id, Long teamId, String code, String colorHex) {
        ShiftDefinitionEntity entity = new ShiftDefinitionEntity();
        entity.setId(id);
        entity.setTeamId(teamId);
        entity.setCode(code);
        entity.setColorHex(colorHex);
        entity.setVisible(true);
        return entity;
    }
}