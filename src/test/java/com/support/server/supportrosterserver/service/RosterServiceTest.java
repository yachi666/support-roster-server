package com.support.server.supportrosterserver.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.support.server.supportrosterserver.entity.workspace.RosterAssignmentEntity;
import com.support.server.supportrosterserver.entity.workspace.ShiftDefinitionEntity;
import com.support.server.supportrosterserver.entity.workspace.StaffEntity;
import com.support.server.supportrosterserver.entity.workspace.TeamEntity;
import com.support.server.supportrosterserver.mapper.RosterAssignmentMapper;
import com.support.server.supportrosterserver.mapper.ShiftDefinitionMapper;
import com.support.server.supportrosterserver.mapper.StaffMapper;
import com.support.server.supportrosterserver.service.workspace.WorkspaceLookupService;
import com.support.server.supportrosterserver.service.workspace.WorkspaceShiftTimeSupport;
import com.support.server.supportrosterserver.service.workspace.WorkspaceTeamService;

class RosterServiceTest {

    private WorkspaceLookupService lookupService;
    private RosterAssignmentMapper rosterAssignmentMapper;
    private ShiftDefinitionMapper shiftDefinitionMapper;
    private StaffMapper staffMapper;
    private RosterService rosterService;

    @BeforeEach
    void setUp() {
        lookupService = mock(WorkspaceLookupService.class);
        rosterAssignmentMapper = mock(RosterAssignmentMapper.class);
        shiftDefinitionMapper = mock(ShiftDefinitionMapper.class);
        staffMapper = mock(StaffMapper.class);
        rosterService = new RosterService(
            new AvatarUrlResolver("https://photos.global.image/casual/square"),
            mock(WorkspaceTeamService.class),
            lookupService,
            rosterAssignmentMapper,
            shiftDefinitionMapper,
            staffMapper,
            new WorkspaceShiftTimeSupport()
        );
    }

    @Test
    void shouldReturnViewerShiftWhenDefinitionIsVisibleEvenIfItIsNotPrimary() {
        TeamEntity team = new TeamEntity();
        team.setId(301L);
        team.setName("China Support");
        team.setVisible(true);

        StaffEntity staff = new StaffEntity();
        staff.setId(401L);
        staff.setStaffId("A401");
        staff.setName("Alice");

        ShiftDefinitionEntity shiftDefinition = new ShiftDefinitionEntity();
        shiftDefinition.setId(501L);
        shiftDefinition.setCode("AUX");
        shiftDefinition.setMeaning("Auxiliary");
        shiftDefinition.setVisible(true);
        shiftDefinition.setPrimaryShift(false);
        shiftDefinition.setStartTime(LocalTime.of(9, 0));
        shiftDefinition.setDurationMinutes(8 * 60);
        shiftDefinition.setTimezone("UTC");
        shiftDefinition.setColorHex("#22c55e");

        RosterAssignmentEntity assignment = new RosterAssignmentEntity();
        assignment.setId(601L);
        assignment.setTeamId(301L);
        assignment.setStaffId(401L);
        assignment.setShiftDefinitionId(501L);
        assignment.setAssignmentDate(LocalDate.of(2026, 3, 1));

        when(lookupService.teamMap()).thenReturn(Map.of(301L, team));
        when(rosterAssignmentMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(assignment));
        when(shiftDefinitionMapper.selectById(501L)).thenReturn(shiftDefinition);
        when(staffMapper.selectById(401L)).thenReturn(staff);
        when(lookupService.normalizeWorkspaceTimezone("UTC")).thenReturn("UTC");

        var shifts = rosterService.getShiftsByDate(LocalDate.of(2026, 3, 1), null, "UTC");

        assertEquals(1, shifts.size());
        assertEquals("AUX", shifts.get(0).getCode());
        assertEquals(Boolean.FALSE, shifts.get(0).getIsPrimary());
        assertEquals(Boolean.TRUE, shifts.get(0).getShowOnRoster());
    }
}
