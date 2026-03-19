package com.support.server.supportrosterserver.service.workspace;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.support.server.supportrosterserver.entity.workspace.ImportIssueEntity;
import com.support.server.supportrosterserver.entity.workspace.RosterAssignmentEntity;
import com.support.server.supportrosterserver.entity.workspace.ShiftDefinitionEntity;
import com.support.server.supportrosterserver.entity.workspace.StaffEntity;
import com.support.server.supportrosterserver.entity.workspace.TeamEntity;
import com.support.server.supportrosterserver.mapper.ImportBatchMapper;
import com.support.server.supportrosterserver.mapper.ImportIssueMapper;
import com.support.server.supportrosterserver.mapper.RosterAssignmentMapper;
import com.support.server.supportrosterserver.mapper.ShiftDefinitionMapper;
import com.support.server.supportrosterserver.mapper.StaffMapper;

class WorkspaceValidationServiceTest {

    private StaffMapper staffMapper;
    private ShiftDefinitionMapper shiftDefinitionMapper;
    private RosterAssignmentMapper rosterAssignmentMapper;
    private ImportBatchMapper importBatchMapper;
    private ImportIssueMapper importIssueMapper;
    private WorkspaceLookupService lookupService;
    private WorkspaceValidationService validationService;

    @BeforeEach
    void setUp() {
        staffMapper = mock(StaffMapper.class);
        shiftDefinitionMapper = mock(ShiftDefinitionMapper.class);
        rosterAssignmentMapper = mock(RosterAssignmentMapper.class);
        importBatchMapper = mock(ImportBatchMapper.class);
        importIssueMapper = mock(ImportIssueMapper.class);
        lookupService = mock(WorkspaceLookupService.class);
        validationService = new WorkspaceValidationService(staffMapper, shiftDefinitionMapper, rosterAssignmentMapper, importBatchMapper, importIssueMapper, lookupService);
    }

    @Test
    void shouldReportMissingTimezoneAndCoverage() {
        StaffEntity staff = new StaffEntity();
        staff.setId(1L);
        staff.setName("Alex");
        staff.setTeamId(100L);

        TeamEntity team = new TeamEntity();
        team.setId(100L);
        team.setTeamCode("ap-l2");
        team.setName("AP L2");
        team.setVisible(true);

        ShiftDefinitionEntity shiftDefinition = new ShiftDefinitionEntity();
        shiftDefinition.setId(1000L);
        shiftDefinition.setTeamId(100L);
        shiftDefinition.setCode("DS");
        shiftDefinition.setPrimaryShift(true);
        shiftDefinition.setVisible(true);
        shiftDefinition.setStartTime(LocalTime.of(9, 0));
        shiftDefinition.setEndTime(LocalTime.of(18, 0));

        RosterAssignmentEntity assignment = new RosterAssignmentEntity();
        assignment.setStaffId(1L);
        assignment.setTeamId(100L);
        assignment.setShiftCode("DS");
        assignment.setAssignmentDate(LocalDate.of(2026, 3, 1));

        when(importIssueMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        when(staffMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(staff));
        when(shiftDefinitionMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(shiftDefinition));
        when(rosterAssignmentMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(assignment));
        when(lookupService.teamMap()).thenReturn(java.util.Map.of(100L, team));
        when(lookupService.listTeams()).thenReturn(List.of(team));

        var issues = validationService.validateLiveData(YearMonth.of(2026, 3));

        assertTrue(issues.stream().anyMatch(issue -> issue.getType().equals("Time Zone Ambiguity")));
        assertTrue(issues.stream().anyMatch(issue -> issue.getType().equals("Missing Primary Coverage")));
        assertFalse(issues.stream().anyMatch(issue -> issue.getType().equals("Invalid Shift Code")));
    }
}