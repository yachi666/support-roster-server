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
import com.support.server.supportrosterserver.entity.workspace.RoleGroupEntity;
import com.support.server.supportrosterserver.entity.workspace.ShiftDefinitionEntity;
import com.support.server.supportrosterserver.entity.workspace.StaffEntity;
import com.support.server.supportrosterserver.entity.workspace.TeamEntity;
import com.support.server.supportrosterserver.mapper.RosterAssignmentMapper;
import com.support.server.supportrosterserver.mapper.ShiftDefinitionMapper;
import com.support.server.supportrosterserver.mapper.StaffMapper;

class WorkspaceRosterServiceTest {

    private StaffMapper staffMapper;
    private ShiftDefinitionMapper shiftDefinitionMapper;
    private WorkspaceLookupService lookupService;
    private WorkspaceValidationService validationService;
    private WorkspaceRosterService workspaceRosterService;

    @BeforeEach
    void setUp() {
        staffMapper = mock(StaffMapper.class);
        shiftDefinitionMapper = mock(ShiftDefinitionMapper.class);
        lookupService = mock(WorkspaceLookupService.class);
        validationService = mock(WorkspaceValidationService.class);

        workspaceRosterService = new WorkspaceRosterService(
            staffMapper,
            shiftDefinitionMapper,
            mock(RosterAssignmentMapper.class),
            lookupService,
            validationService
        );
    }

    @Test
    void shouldReturnRoleScopedShiftOptionsForMonthlyRoster() {
        RoleGroupEntity l1RoleGroup = new RoleGroupEntity();
        l1RoleGroup.setId(101L);
        l1RoleGroup.setName("L1 China");

        RoleGroupEntity l2RoleGroup = new RoleGroupEntity();
        l2RoleGroup.setId(202L);
        l2RoleGroup.setName("L2 China");

        TeamEntity team = new TeamEntity();
        team.setId(301L);
        team.setName("China Support");
        team.setColor("#0f766e");
        team.setVisible(true);

        StaffEntity alice = new StaffEntity();
        alice.setId(401L);
        alice.setName("Alice");
        alice.setRoleName("Analyst");
        alice.setRoleGroupId(101L);

        StaffEntity bob = new StaffEntity();
        bob.setId(402L);
        bob.setName("Bob");
        bob.setRoleName("Escalation");
        bob.setRoleGroupId(202L);

        when(lookupService.roleGroupMap()).thenReturn(Map.of(
            101L, l1RoleGroup,
            202L, l2RoleGroup
        ));
        when(lookupService.teamByRoleGroupId()).thenReturn(Map.of(
            101L, team,
            202L, team
        ));
        when(lookupService.listTeams()).thenReturn(List.of(team));
        when(staffMapper.selectList(any())).thenReturn(List.of(alice, bob));
        when(shiftDefinitionMapper.selectList(any())).thenReturn(List.of(
            buildShiftDefinition(501L, 101L, "AP-D", "#ef4444"),
            buildShiftDefinition(502L, 101L, "AP-N", "#f97316"),
            buildShiftDefinition(503L, 202L, "L2-D", "#22c55e")
        ));
        when(validationService.validateLiveData(any())).thenReturn(List.of());

        WorkspaceMonthlyRosterResponse response = workspaceRosterService.getMonthlyRoster(2026, 3);

        assertEquals(1, response.getGroups().size());
        assertEquals("101", response.getGroups().get(0).getStaff().get(0).getRoleGroupId().toString());
        assertEquals("202", response.getGroups().get(0).getStaff().get(1).getRoleGroupId().toString());
        assertIterableEquals(List.of("AP-D", "AP-N", "L2-D"), response.getShiftCodeOptions());
        assertIterableEquals(List.of("AP-D", "AP-N"), response.getShiftCodeOptionsByRoleGroup().get(101L));
        assertIterableEquals(List.of("L2-D"), response.getShiftCodeOptionsByRoleGroup().get(202L));
        assertEquals("#ef4444", response.getShiftCodeColorMap().get("AP-D"));
        assertTrue(response.getValidationWarning().isEmpty());
    }

    private ShiftDefinitionEntity buildShiftDefinition(Long id, Long roleGroupId, String code, String colorHex) {
        ShiftDefinitionEntity entity = new ShiftDefinitionEntity();
        entity.setId(id);
        entity.setRoleGroupId(roleGroupId);
        entity.setCode(code);
        entity.setColorHex(colorHex);
        entity.setVisible(true);
        return entity;
    }
}