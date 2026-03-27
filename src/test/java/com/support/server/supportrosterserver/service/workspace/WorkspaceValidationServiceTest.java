package com.support.server.supportrosterserver.service.workspace;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.support.server.supportrosterserver.auth.AccountRole;
import com.support.server.supportrosterserver.auth.AuthenticatedAccount;
import com.support.server.supportrosterserver.entity.auth.WorkspaceAccountEntity;
import com.support.server.supportrosterserver.entity.auth.WorkspaceAccountTeamScopeEntity;
import com.support.server.supportrosterserver.entity.workspace.ImportIssueEntity;
import com.support.server.supportrosterserver.entity.workspace.RosterAssignmentEntity;
import com.support.server.supportrosterserver.entity.workspace.ShiftDefinitionEntity;
import com.support.server.supportrosterserver.entity.workspace.ShiftDefinitionTeamRelEntity;
import com.support.server.supportrosterserver.entity.workspace.StaffEntity;
import com.support.server.supportrosterserver.entity.workspace.TeamEntity;
import com.support.server.supportrosterserver.entity.workspace.ImportBatchEntity;
import com.support.server.supportrosterserver.mapper.ImportBatchMapper;
import com.support.server.supportrosterserver.mapper.ImportIssueMapper;
import com.support.server.supportrosterserver.mapper.RosterAssignmentMapper;
import com.support.server.supportrosterserver.mapper.ShiftDefinitionMapper;
import com.support.server.supportrosterserver.mapper.ShiftDefinitionTeamRelMapper;
import com.support.server.supportrosterserver.mapper.StaffMapper;
import com.support.server.supportrosterserver.mapper.WorkspaceAccountMapper;
import com.support.server.supportrosterserver.mapper.WorkspaceAccountTeamScopeMapper;
import com.support.server.supportrosterserver.service.auth.AuthContextService;

class WorkspaceValidationServiceTest {

    private StaffMapper staffMapper;
    private ShiftDefinitionMapper shiftDefinitionMapper;
    private RosterAssignmentMapper rosterAssignmentMapper;
    private ImportBatchMapper importBatchMapper;
    private ImportIssueMapper importIssueMapper;
    private WorkspaceLookupService lookupService;
    private ShiftDefinitionTeamRelMapper shiftDefinitionTeamRelMapper;
    private WorkspaceAccountMapper workspaceAccountMapper;
    private WorkspaceAccountTeamScopeMapper workspaceAccountTeamScopeMapper;
    private AuthContextService authContextService;
    private WorkspaceOperationLogService workspaceOperationLogService;
    private WorkspaceValidationService validationService;

    @BeforeEach
    void setUp() {
        staffMapper = mock(StaffMapper.class);
        shiftDefinitionMapper = mock(ShiftDefinitionMapper.class);
        rosterAssignmentMapper = mock(RosterAssignmentMapper.class);
        importBatchMapper = mock(ImportBatchMapper.class);
        importIssueMapper = mock(ImportIssueMapper.class);
        lookupService = mock(WorkspaceLookupService.class);
        shiftDefinitionTeamRelMapper = mock(ShiftDefinitionTeamRelMapper.class);
        workspaceAccountMapper = mock(WorkspaceAccountMapper.class);
        workspaceAccountTeamScopeMapper = mock(WorkspaceAccountTeamScopeMapper.class);
        authContextService = mock(AuthContextService.class);
        workspaceOperationLogService = mock(WorkspaceOperationLogService.class);
        validationService = new WorkspaceValidationService(
            staffMapper,
            shiftDefinitionMapper,
            rosterAssignmentMapper,
            importBatchMapper,
            importIssueMapper,
            lookupService,
            shiftDefinitionTeamRelMapper,
            workspaceAccountMapper,
            workspaceAccountTeamScopeMapper,
            authContextService,
            new WorkspaceShiftTimeSupport(),
            workspaceOperationLogService
        );

        when(authContextService.canReadTeam(org.mockito.ArgumentMatchers.anyLong())).thenReturn(true);
        when(authContextService.readableTeamIds()).thenReturn(List.of(100L));
        when(authContextService.currentActor(org.mockito.ArgumentMatchers.anyString())).thenReturn("Admin");
        when(workspaceAccountTeamScopeMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        when(workspaceAccountMapper.selectBatchIds(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
    }

    @Test
    void shouldReportMissingTimezoneWithoutCoverageIssue() {
        StaffEntity staff = new StaffEntity();
        staff.setId(1L);
        staff.setName("Alex");
        staff.setTeamId(100L);
        staff.setStatus("Active");

        TeamEntity team = new TeamEntity();
        team.setId(100L);
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
        shiftDefinition.setDurationMinutes(9 * 60);

        RosterAssignmentEntity assignment = new RosterAssignmentEntity();
        assignment.setStaffId(1L);
        assignment.setTeamId(100L);
        assignment.setShiftDefinitionId(1000L);
        assignment.setShiftCode("DS");
        assignment.setAssignmentDate(LocalDate.of(2026, 3, 1));

        when(importIssueMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        when(staffMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(staff));
        when(shiftDefinitionMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(shiftDefinition));
        when(shiftDefinitionTeamRelMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(buildRelation(1000L, 100L)));
        when(rosterAssignmentMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(assignment));
        when(lookupService.teamMap()).thenReturn(java.util.Map.of(100L, team));
        when(lookupService.listTeams()).thenReturn(List.of(team));

        var issues = validationService.validateLiveData(YearMonth.of(2026, 3));

        assertTrue(issues.stream().anyMatch(issue -> issue.getType().equals("Time Zone Ambiguity")));
        assertFalse(issues.stream().anyMatch(issue -> issue.getType().equals("Missing Primary Coverage")));
        assertFalse(issues.stream().anyMatch(issue -> issue.getType().equals("Invalid Shift Code")));
    }

    @Test
    void shouldReturnSummaryOnlyPayloadForRosterConsumers() {
        StaffEntity staff = new StaffEntity();
        staff.setId(1L);
        staff.setName("Alex");
        staff.setTeamId(100L);
        staff.setStatus("Active");

        TeamEntity team = new TeamEntity();
        team.setId(100L);
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
        shiftDefinition.setDurationMinutes(9 * 60);

        when(importBatchMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        when(importIssueMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        when(staffMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(staff));
        when(shiftDefinitionMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(shiftDefinition));
        when(shiftDefinitionTeamRelMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(buildRelation(1000L, 100L)));
        when(rosterAssignmentMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        when(lookupService.teamMap()).thenReturn(java.util.Map.of(100L, team));
        when(lookupService.listTeams()).thenReturn(List.of(team));
        when(authContextService.readableTeamIds()).thenReturn(List.of(100L));

        var response = validationService.getValidation(2026, 3, true);

        assertTrue(response.getIssues().isEmpty());
        assertEquals(0, response.getSummary().getHigh());
        assertEquals(0, response.getSummary().getMedium());
        assertEquals(1, response.getSummary().getLow());
        assertEquals(1, response.getSummary().getTotal());
        assertEquals(0, response.getSummary().getBlocking());
        assertNull(response.getTopIssue());
    }

    @Test
    void shouldOnlyExposeHighSeverityIssueAsTopIssueForRosterWarning() {
        StaffEntity staff = new StaffEntity();
        staff.setId(1L);
        staff.setName("Alex");
        staff.setTeamId(100L);
        staff.setStatus("Active");

        TeamEntity team = new TeamEntity();
        team.setId(100L);
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
        shiftDefinition.setDurationMinutes(9 * 60);

        RosterAssignmentEntity firstAssignment = new RosterAssignmentEntity();
        firstAssignment.setStaffId(1L);
        firstAssignment.setTeamId(100L);
        firstAssignment.setShiftDefinitionId(1000L);
        firstAssignment.setShiftCode("DS");
        firstAssignment.setAssignmentDate(LocalDate.of(2026, 3, 1));

        RosterAssignmentEntity secondAssignment = new RosterAssignmentEntity();
        secondAssignment.setStaffId(1L);
        secondAssignment.setTeamId(100L);
        secondAssignment.setShiftDefinitionId(1000L);
        secondAssignment.setShiftCode("DS");
        secondAssignment.setAssignmentDate(LocalDate.of(2026, 3, 1));

        when(importBatchMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        when(importIssueMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        when(staffMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(staff));
        when(shiftDefinitionMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(shiftDefinition));
        when(shiftDefinitionTeamRelMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(buildRelation(1000L, 100L)));
        when(rosterAssignmentMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(firstAssignment, secondAssignment));
        when(lookupService.teamMap()).thenReturn(java.util.Map.of(100L, team));
        when(lookupService.listTeams()).thenReturn(List.of(team));
        when(authContextService.readableTeamIds()).thenReturn(List.of(100L));

        var response = validationService.getValidation(2026, 3, true);

        assertEquals(1, response.getSummary().getHigh());
        assertEquals(1, response.getSummary().getBlocking());
        assertNotNull(response.getTopIssue());
        assertEquals("Overlapping Assignment", response.getTopIssue().getType());
        assertEquals("roster", response.getTopIssue().getDomain());
        assertTrue(response.getTopIssue().getBlocking());
    }

    @Test
    void shouldPromoteMissingPrimaryCoverageAsBlockingRosterIssue() {
        StaffEntity staff = new StaffEntity();
        staff.setId(1L);
        staff.setName("Alex");
        staff.setTeamId(100L);
        staff.setStatus("Active");
        staff.setTimezone("UTC");

        TeamEntity team = new TeamEntity();
        team.setId(100L);
        team.setName("AP L2");
        team.setVisible(true);

        ShiftDefinitionEntity primaryShift = new ShiftDefinitionEntity();
        primaryShift.setId(1000L);
        primaryShift.setTeamId(100L);
        primaryShift.setCode("DS");
        primaryShift.setPrimaryShift(true);
        primaryShift.setVisible(true);
        primaryShift.setStartTime(LocalTime.of(9, 0));
        primaryShift.setEndTime(LocalTime.of(18, 0));
        primaryShift.setDurationMinutes(9 * 60);

        ShiftDefinitionEntity secondaryShift = new ShiftDefinitionEntity();
        secondaryShift.setId(1001L);
        secondaryShift.setTeamId(100L);
        secondaryShift.setCode("AUX");
        secondaryShift.setPrimaryShift(false);
        secondaryShift.setVisible(true);
        secondaryShift.setStartTime(LocalTime.of(9, 0));
        secondaryShift.setEndTime(LocalTime.of(18, 0));
        secondaryShift.setDurationMinutes(9 * 60);

        RosterAssignmentEntity assignment = new RosterAssignmentEntity();
        assignment.setStaffId(1L);
        assignment.setTeamId(100L);
        assignment.setShiftDefinitionId(1001L);
        assignment.setShiftCode("AUX");
        assignment.setAssignmentDate(LocalDate.of(2026, 3, 1));

        when(importBatchMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        when(importIssueMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        when(staffMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(staff));
        when(shiftDefinitionMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(primaryShift, secondaryShift));
        when(shiftDefinitionTeamRelMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(
            buildRelation(1000L, 100L),
            buildRelation(1001L, 100L)
        ));
        when(rosterAssignmentMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(assignment));
        when(lookupService.teamMap()).thenReturn(java.util.Map.of(100L, team));
        when(lookupService.listTeams()).thenReturn(List.of(team));
        when(authContextService.readableTeamIds()).thenReturn(List.of(100L));

        var response = validationService.getValidation(2026, 3, true);

        assertEquals(1, response.getSummary().getHigh());
        assertEquals(1, response.getSummary().getBlocking());
        assertNotNull(response.getTopIssue());
        assertEquals("Missing Primary Coverage", response.getTopIssue().getType());
        assertEquals("/workspace/roster", response.getTopIssue().getTargetPage());
    }

    @Test
    void shouldKeepImportIssuesOutOfRosterTopIssue() {
        StaffEntity staff = new StaffEntity();
        staff.setId(1L);
        staff.setName("Alex");
        staff.setTeamId(100L);
        staff.setStatus("Active");
        staff.setTimezone("UTC");

        TeamEntity team = new TeamEntity();
        team.setId(100L);
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
        shiftDefinition.setDurationMinutes(9 * 60);

        ImportBatchEntity batch = new ImportBatchEntity();
        batch.setId(900L);
        batch.setRosterYear(2026);
        batch.setRosterMonth(3);
        batch.setStatus("PREVIEWED");

        ImportIssueEntity importIssue = new ImportIssueEntity();
        importIssue.setId(901L);
        importIssue.setSeverity("high");
        importIssue.setIssueType("Duplicate Staff ID");
        importIssue.setDescription("Staff ID 'A001' appears more than once.");
        importIssue.setTeamName("AP L2");
        importIssue.setResolved(false);

        when(importBatchMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(batch));
        when(importIssueMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(importIssue));
        when(staffMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(staff));
        when(shiftDefinitionMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(shiftDefinition));
        when(shiftDefinitionTeamRelMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(buildRelation(1000L, 100L)));
        when(rosterAssignmentMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        when(lookupService.teamMap()).thenReturn(java.util.Map.of(100L, team));
        when(lookupService.listTeams()).thenReturn(List.of(team));
        when(authContextService.readableTeamIds()).thenReturn(List.of(100L));

        var response = validationService.getValidation(2026, 3, true);

        assertEquals(1, response.getSummary().getHigh());
        assertEquals(1, response.getSummary().getTotal());
        assertEquals(0, response.getSummary().getBlocking());
        assertNull(response.getTopIssue());
    }

    @Test
    void shouldExposeInvalidTeamScopeAsAdminCleanupIssue() {
        WorkspaceAccountEntity account = new WorkspaceAccountEntity();
        account.setId(10L);
        account.setStaffCode("LOCAL-ADMIN");

        WorkspaceAccountTeamScopeEntity scope = new WorkspaceAccountTeamScopeEntity();
        scope.setId(901L);
        scope.setAccountId(10L);
        scope.setTeamId(999L);

        when(importBatchMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        when(importIssueMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        when(staffMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        when(shiftDefinitionMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        when(shiftDefinitionTeamRelMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        when(rosterAssignmentMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        when(lookupService.teamMap()).thenReturn(java.util.Map.of());
        when(lookupService.listTeams()).thenReturn(List.of());
        when(workspaceAccountTeamScopeMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(scope));
        when(workspaceAccountMapper.selectBatchIds(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(account));
        when(authContextService.isLoggedIn()).thenReturn(true);
        when(authContextService.requireLogin()).thenReturn(adminAccount());

        var response = validationService.getValidation(2026, 3);

        assertEquals(1, response.getIssues().size());
        assertEquals("Invalid Team Scope", response.getIssues().get(0).getType());
        assertEquals("delete_invalid_team_scope", response.getIssues().get(0).getRemediation().getActionKey());
    }

    @Test
    void shouldPreviewAndApplyInvalidTeamScopeRemediation() {
        WorkspaceAccountEntity account = new WorkspaceAccountEntity();
        account.setId(10L);
        account.setStaffCode("LOCAL-ADMIN");

        WorkspaceAccountTeamScopeEntity scope = new WorkspaceAccountTeamScopeEntity();
        scope.setId(901L);
        scope.setAccountId(10L);
        scope.setTeamId(999L);

        List<WorkspaceAccountTeamScopeEntity> scopes = new ArrayList<>(List.of(scope));
        when(importBatchMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        when(importIssueMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        when(staffMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        when(shiftDefinitionMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        when(shiftDefinitionTeamRelMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        when(rosterAssignmentMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        when(lookupService.teamMap()).thenReturn(java.util.Map.of());
        when(lookupService.listTeams()).thenReturn(List.of());
        when(workspaceAccountMapper.selectBatchIds(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(account));
        when(workspaceAccountTeamScopeMapper.selectList(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> List.copyOf(scopes));
        when(workspaceAccountTeamScopeMapper.selectById(901L)).thenAnswer(invocation -> scopes.stream().filter(item -> item.getId().equals(901L)).findFirst().orElse(null));
        when(workspaceAccountTeamScopeMapper.deleteById(901L)).thenAnswer(invocation -> scopes.removeIf(item -> item.getId().equals(901L)) ? 1 : 0);
        when(authContextService.isLoggedIn()).thenReturn(true);
        when(authContextService.requireLogin()).thenReturn(adminAccount());

        var preview = validationService.previewRemediation(901L, new com.support.server.supportrosterserver.dto.workspace.WorkspaceValidationRemediationRequest(2026, 3, "delete_invalid_team_scope"));
        assertEquals(1, preview.getRecordCount());
        assertEquals(List.of(901L), preview.getRecordIds());

        var apply = validationService.applyRemediation(901L, new com.support.server.supportrosterserver.dto.workspace.WorkspaceValidationRemediationRequest(2026, 3, "delete_invalid_team_scope"));
        assertEquals(1, apply.getAppliedCount());
        assertTrue(apply.getValidation().getIssues().isEmpty());
        verify(workspaceOperationLogService).log("Admin", "Cleanup validation issue", "workspace_account_team_scope", 901L, "Action=delete_invalid_team_scope, rule=config.account.team-scope-invalid");
    }

    @Test
    void shouldHideInvalidTeamScopeIssueFromNonAdminValidationView() {
        WorkspaceAccountEntity account = new WorkspaceAccountEntity();
        account.setId(10L);
        account.setStaffCode("LOCAL-ADMIN");

        WorkspaceAccountTeamScopeEntity scope = new WorkspaceAccountTeamScopeEntity();
        scope.setId(901L);
        scope.setAccountId(10L);
        scope.setTeamId(999L);

        when(importBatchMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        when(importIssueMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        when(staffMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        when(shiftDefinitionMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        when(shiftDefinitionTeamRelMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        when(rosterAssignmentMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        when(lookupService.teamMap()).thenReturn(java.util.Map.of());
        when(lookupService.listTeams()).thenReturn(List.of());
        when(workspaceAccountTeamScopeMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(scope));
        when(workspaceAccountMapper.selectBatchIds(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(account));
        when(authContextService.isLoggedIn()).thenReturn(false);

        var response = validationService.getValidation(2026, 3);

        assertTrue(response.getIssues().isEmpty());
    }

    @Test
    void shouldPreviewAndApplyOrphanAssignmentRemediation() {
        TeamEntity team = new TeamEntity();
        team.setId(100L);
        team.setName("AP L2");
        team.setVisible(true);

        RosterAssignmentEntity orphanAssignment = new RosterAssignmentEntity();
        orphanAssignment.setId(701L);
        orphanAssignment.setStaffId(88L);
        orphanAssignment.setTeamId(100L);
        orphanAssignment.setShiftDefinitionId(999L);
        orphanAssignment.setShiftCode("DS");
        orphanAssignment.setAssignmentDate(LocalDate.of(2026, 3, 1));

        List<RosterAssignmentEntity> assignments = new ArrayList<>(List.of(orphanAssignment));
        when(importBatchMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        when(importIssueMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        when(staffMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        when(shiftDefinitionMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        when(shiftDefinitionTeamRelMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        when(rosterAssignmentMapper.selectList(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> List.copyOf(assignments));
        when(rosterAssignmentMapper.selectById(701L)).thenAnswer(invocation -> assignments.stream().filter(item -> item.getId().equals(701L)).findFirst().orElse(null));
        when(rosterAssignmentMapper.deleteById(701L)).thenAnswer(invocation -> assignments.removeIf(item -> item.getId().equals(701L)) ? 1 : 0);
        when(lookupService.teamMap()).thenReturn(java.util.Map.of(100L, team));
        when(lookupService.listTeams()).thenReturn(List.of(team));

        var response = validationService.getValidation(2026, 3);
        assertEquals("Orphan Assignment", response.getIssues().get(0).getType());
        assertEquals("delete_orphan_assignment", response.getIssues().get(0).getRemediation().getActionKey());

        var preview = validationService.previewRemediation(701L, new com.support.server.supportrosterserver.dto.workspace.WorkspaceValidationRemediationRequest(2026, 3, "delete_orphan_assignment"));
        assertEquals("Delete orphan assignment", preview.getTitle());
        assertEquals(List.of(701L), preview.getRecordIds());

        var apply = validationService.applyRemediation(701L, new com.support.server.supportrosterserver.dto.workspace.WorkspaceValidationRemediationRequest(2026, 3, "delete_orphan_assignment"));
        assertEquals(1, apply.getAppliedCount());
        assertTrue(apply.getValidation().getIssues().isEmpty());
        verify(workspaceOperationLogService).log("Admin", "Cleanup validation issue", "workspace_roster_assignment", 701L, "Action=delete_orphan_assignment, rule=roster.assignment.orphaned");
    }

    private ShiftDefinitionTeamRelEntity buildRelation(Long shiftDefinitionId, Long teamId) {
        ShiftDefinitionTeamRelEntity relation = new ShiftDefinitionTeamRelEntity();
        relation.setShiftDefinitionId(shiftDefinitionId);
        relation.setTeamId(teamId);
        return relation;
    }

    private AuthenticatedAccount adminAccount() {
        return new AuthenticatedAccount(
            1L,
            1L,
            "ADMIN",
            "Admin",
            AccountRole.ADMIN.getCode(),
            "ACTIVE",
            "LOCAL_PASSWORD",
            Set.of(),
            List.of()
        );
    }
}
