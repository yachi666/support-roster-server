package com.support.server.supportrosterserver.service.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import com.support.server.supportrosterserver.dto.workspace.WorkspaceImportPreviewResponse;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceImportPreviewSaveRequest;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceImportPreviewSaveRowRequest;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceImportSaveResponse;
import com.support.server.supportrosterserver.entity.workspace.RosterAssignmentEntity;
import com.support.server.supportrosterserver.entity.workspace.ShiftDefinitionEntity;
import com.support.server.supportrosterserver.entity.workspace.ShiftDefinitionTeamRelEntity;
import com.support.server.supportrosterserver.entity.workspace.StaffEntity;
import com.support.server.supportrosterserver.entity.workspace.TeamEntity;
import com.support.server.supportrosterserver.mapper.RosterAssignmentMapper;
import com.support.server.supportrosterserver.mapper.ShiftDefinitionMapper;
import com.support.server.supportrosterserver.mapper.ShiftDefinitionTeamRelMapper;
import com.support.server.supportrosterserver.mapper.StaffMapper;
import com.support.server.supportrosterserver.mapper.TeamMapper;
import com.support.server.supportrosterserver.service.auth.AuthContextService;

class WorkspaceImportServiceTest {

    private ShiftDefinitionMapper shiftDefinitionMapper;
    private ShiftDefinitionTeamRelMapper shiftDefinitionTeamRelMapper;
    private StaffMapper staffMapper;
    private RosterAssignmentMapper rosterAssignmentMapper;
    private TeamMapper teamMapper;
    private WorkspaceLookupService lookupService;
    private AuthContextService authContextService;
    private WorkspaceImportService workspaceImportService;

    @BeforeEach
    void setUp() {
        shiftDefinitionMapper = mock(ShiftDefinitionMapper.class);
        shiftDefinitionTeamRelMapper = mock(ShiftDefinitionTeamRelMapper.class);
        staffMapper = mock(StaffMapper.class);
        rosterAssignmentMapper = mock(RosterAssignmentMapper.class);
        teamMapper = mock(TeamMapper.class);
        lookupService = mock(WorkspaceLookupService.class);
        authContextService = mock(AuthContextService.class);

        workspaceImportService = new WorkspaceImportService(
            shiftDefinitionMapper,
            shiftDefinitionTeamRelMapper,
            staffMapper,
            rosterAssignmentMapper,
            teamMapper,
            lookupService,
            authContextService,
            new WorkspaceShiftTimeSupport()
        );
    }

    @Test
    void shouldPreviewSimplifiedWorkbookAndBlankInvalidShiftCodes() throws IOException {
        TeamEntity team = buildTeam(101L, "China Support");
        StaffEntity existingStaff = new StaffEntity();
        existingStaff.setId(201L);
        existingStaff.setStaffCode("1001");
        existingStaff.setName("Alice");
        existingStaff.setRoleName("L1");
        existingStaff.setTeamId(101L);

        ShiftDefinitionEntity shift = buildShiftDefinition(1001L, "A");

        when(lookupService.listTeams()).thenReturn(List.of(team));
        when(lookupService.normalizeWorkspaceTimezone(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(authContextService.readableTeamIds()).thenReturn(List.of(101L));
        when(staffMapper.selectList(any())).thenReturn(List.of(existingStaff));
        when(shiftDefinitionTeamRelMapper.selectList(any())).thenReturn(List.of(buildRelation(1001L, 101L)));
        when(shiftDefinitionMapper.selectList(any())).thenReturn(List.of(shift));

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "preview.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            buildWorkbook(List.of(Map.of("staff_id", "1001", "team", "China Support", "1", "A", "2", "ZZ")))
        );

        WorkspaceImportPreviewResponse response = workspaceImportService.previewImport(file, 2026, 3, "tester");

        assertEquals(1, response.getTotalRecords());
        assertEquals(0, response.getValidRecords());
        assertEquals(1, response.getInvalidRecords());
        assertEquals("A", response.getGroups().get(0).getStaff().get(0).getSchedule().get(1));
        assertEquals("", response.getGroups().get(0).getStaff().get(0).getSchedule().get(2));
        assertTrue(response.getIssues().stream().anyMatch(issue -> "Invalid Shift Code".equals(issue.getType())));
    }

    @Test
    void shouldRejectDuplicateStaffRowsDuringPreview() throws IOException {
        TeamEntity team = buildTeam(101L, "China Support");
        ShiftDefinitionEntity shift = buildShiftDefinition(1001L, "A");

        when(lookupService.listTeams()).thenReturn(List.of(team));
        when(lookupService.normalizeWorkspaceTimezone(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(authContextService.readableTeamIds()).thenReturn(List.of(101L));
        when(staffMapper.selectList(any())).thenReturn(List.of());
        when(shiftDefinitionTeamRelMapper.selectList(any())).thenReturn(List.of(buildRelation(1001L, 101L)));
        when(shiftDefinitionMapper.selectList(any())).thenReturn(List.of(shift));

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "duplicate.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            buildWorkbook(List.of(
                Map.of("staff_id", "1001", "team", "China Support", "1", "A"),
                Map.of("staff_id", "1001", "team", "China Support", "2", "A")
            ))
        );

        WorkspaceImportPreviewResponse response = workspaceImportService.previewImport(file, 2026, 3, "tester");

        assertTrue(response.getIssues().stream().anyMatch(issue -> "Duplicate Staff ID".equals(issue.getType())));
        assertEquals(1, response.getValidRecords());
        assertEquals(1, response.getInvalidRecords());
    }

    @Test
    void shouldRedactOutOfScopeStaffAndTeamsDuringPreview() throws IOException {
        TeamEntity readableTeam = buildTeam(101L, "China Support");
        TeamEntity hiddenTeam = buildTeam(102L, "Secret Team");
        StaffEntity hiddenStaff = new StaffEntity();
        hiddenStaff.setId(301L);
        hiddenStaff.setStaffCode("9001");
        hiddenStaff.setName("Hidden User");
        hiddenStaff.setRoleName("Secret");
        hiddenStaff.setTeamId(102L);
        ShiftDefinitionEntity shift = buildShiftDefinition(1001L, "A");

        when(lookupService.listTeams()).thenReturn(List.of(readableTeam, hiddenTeam));
        when(lookupService.normalizeWorkspaceTimezone(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(authContextService.readableTeamIds()).thenReturn(List.of(101L));
        when(staffMapper.selectList(any())).thenReturn(List.of(hiddenStaff));
        when(shiftDefinitionTeamRelMapper.selectList(any())).thenReturn(List.of(buildRelation(1001L, 101L)));
        when(shiftDefinitionMapper.selectList(any())).thenReturn(List.of(shift));

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "scope.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            buildWorkbook(List.of(
                Map.of("staff_id", "9001", "team", "China Support", "1", "A"),
                Map.of("staff_id", "1002", "team", "Secret Team", "1", "A")
            ))
        );

        WorkspaceImportPreviewResponse response = workspaceImportService.previewImport(file, 2026, 3, "tester");

        assertTrue(response.getIssues().stream().anyMatch(issue -> "Staff Out Of Scope".equals(issue.getType())));
        assertTrue(response.getIssues().stream().anyMatch(issue -> "Team Out Of Scope".equals(issue.getType())));
        assertTrue(response.getGroups().isEmpty());
    }

    @Test
    void shouldSavePreviewAndCreateMissingTeamAndStaff() {
        ShiftDefinitionEntity shift = buildShiftDefinition(1001L, "A");
        ShiftDefinitionTeamRelEntity relation = buildRelation(1001L, 301L);

        when(teamMapper.selectList(any())).thenReturn(List.of());
        when(staffMapper.selectList(any())).thenReturn(List.of());
        when(lookupService.inferTimezone(null, "New Team")).thenReturn("UTC");
        when(lookupService.normalizeWorkspaceTimezone("UTC")).thenReturn("UTC");
        when(shiftDefinitionMapper.selectList(any())).thenReturn(List.of(shift));
        when(shiftDefinitionTeamRelMapper.selectList(any())).thenReturn(List.of(relation));
        doNothing().when(authContextService).requireWritableTeam(anyLong());
        doAnswer(invocation -> {
            TeamEntity entity = invocation.getArgument(0);
            entity.setId(301L);
            return 1;
        }).when(teamMapper).insert(any(TeamEntity.class));
        doAnswer(invocation -> {
            StaffEntity entity = invocation.getArgument(0);
            entity.setId(401L);
            return 1;
        }).when(staffMapper).insert(any(StaffEntity.class));

        WorkspaceImportPreviewSaveRowRequest row = new WorkspaceImportPreviewSaveRowRequest();
        row.setStaffCode("1002");
        row.setTeamName("New Team");
        row.setSchedule(Map.of(1, "A", 2, ""));

        WorkspaceImportPreviewSaveRequest request = new WorkspaceImportPreviewSaveRequest();
        request.setYear(2026);
        request.setMonth(3);
        request.setRows(List.of(row));

        WorkspaceImportSaveResponse response = workspaceImportService.savePreview(request);

        assertEquals(1, response.getAppliedStaffCount());
        assertEquals(1, response.getCreatedStaffCount());
        assertEquals(1, response.getCreatedTeamCount());
    }

    @Test
    void shouldExportSimplifiedWorkbook() throws IOException {
        TeamEntity team = buildTeam(101L, "L1");
        StaffEntity staff = new StaffEntity();
        staff.setId(201L);
        staff.setStaffCode("1001");
        staff.setName("Alice");
        staff.setTeamId(101L);

        ShiftDefinitionEntity a = buildShiftDefinition(1001L, "A");
        ShiftDefinitionEntity b = buildShiftDefinition(1002L, "B");
        RosterAssignmentEntity first = buildAssignment(201L, 101L, LocalDate.of(2026, 3, 1), 1001L, "A");
        RosterAssignmentEntity second = buildAssignment(201L, 101L, LocalDate.of(2026, 3, 2), 1002L, "B");

        when(authContextService.readableTeamIds()).thenReturn(List.of(101L));
        when(lookupService.teamMap()).thenReturn(Map.of(101L, team));
        when(rosterAssignmentMapper.selectList(any())).thenReturn(List.of(first, second));
        when(staffMapper.selectList(any())).thenReturn(List.of(staff));
        when(shiftDefinitionMapper.selectList(any())).thenReturn(List.of(a, b));

        byte[] workbookBytes = workspaceImportService.exportRoster(2026, 3).getBody();

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(workbookBytes))) {
            var sheet = workbook.getSheet("Monthly Roster");
            assertEquals("staff_id", sheet.getRow(0).getCell(0).getStringCellValue());
            assertEquals("team", sheet.getRow(0).getCell(1).getStringCellValue());
            assertEquals("1001", sheet.getRow(1).getCell(0).getStringCellValue());
            assertEquals("L1", sheet.getRow(1).getCell(1).getStringCellValue());
            assertEquals("A", sheet.getRow(1).getCell(2).getStringCellValue());
            assertEquals("B", sheet.getRow(1).getCell(3).getStringCellValue());
        }
    }

    @Test
    void shouldExportOnlyReadableTeamsForEditorScope() throws IOException {
        TeamEntity teamL1 = buildTeam(101L, "L1");
        TeamEntity teamL2 = buildTeam(102L, "AP L2");

        StaffEntity alice = new StaffEntity();
        alice.setId(201L);
        alice.setStaffCode("1001");
        alice.setName("Alice");
        alice.setTeamId(101L);

        StaffEntity bob = new StaffEntity();
        bob.setId(202L);
        bob.setStaffCode("1002");
        bob.setName("Bob");
        bob.setTeamId(102L);

        ShiftDefinitionEntity shiftA = buildShiftDefinition(1001L, "A");
        ShiftDefinitionEntity shiftB = buildShiftDefinition(1002L, "B");
        RosterAssignmentEntity aliceAssignment = buildAssignment(201L, 101L, LocalDate.of(2026, 3, 1), 1001L, "A");
        RosterAssignmentEntity bobAssignment = buildAssignment(202L, 102L, LocalDate.of(2026, 3, 1), 1002L, "B");

        when(authContextService.readableTeamIds()).thenReturn(List.of(101L));
        when(lookupService.teamMap()).thenReturn(Map.of(101L, teamL1, 102L, teamL2));
        when(rosterAssignmentMapper.selectList(any())).thenReturn(List.of(aliceAssignment, bobAssignment));
        when(staffMapper.selectList(any())).thenReturn(List.of(alice, bob));
        when(shiftDefinitionMapper.selectList(any())).thenReturn(List.of(shiftA, shiftB));

        byte[] workbookBytes = workspaceImportService.exportRoster(2026, 3).getBody();

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(workbookBytes))) {
            var sheet = workbook.getSheet("Monthly Roster");
            assertEquals("1001", sheet.getRow(1).getCell(0).getStringCellValue());
            assertEquals("L1", sheet.getRow(1).getCell(1).getStringCellValue());
            assertEquals("A", sheet.getRow(1).getCell(2).getStringCellValue());
            assertEquals(1, sheet.getLastRowNum());
        }
    }

    private TeamEntity buildTeam(Long id, String name) {
        TeamEntity team = new TeamEntity();
        team.setId(id);
        team.setName(name);
        team.setColor("#0F172A");
        team.setVisible(true);
        team.setDisplayOrder(1);
        return team;
    }

    private ShiftDefinitionEntity buildShiftDefinition(Long id, String code) {
        ShiftDefinitionEntity entity = new ShiftDefinitionEntity();
        entity.setId(id);
        entity.setCode(code);
        entity.setMeaning(code);
        entity.setStartTime(LocalTime.of(9, 0));
        entity.setEndTime(LocalTime.of(18, 0));
        entity.setDurationMinutes(540);
        entity.setTimezone("UTC");
        entity.setVisible(true);
        entity.setPrimaryShift(false);
        entity.setColorHex("#22C55E");
        return entity;
    }

    private ShiftDefinitionTeamRelEntity buildRelation(Long shiftDefinitionId, Long teamId) {
        ShiftDefinitionTeamRelEntity relation = new ShiftDefinitionTeamRelEntity();
        relation.setShiftDefinitionId(shiftDefinitionId);
        relation.setTeamId(teamId);
        return relation;
    }

    private RosterAssignmentEntity buildAssignment(Long staffId, Long teamId, LocalDate date, Long shiftDefinitionId, String shiftCode) {
        RosterAssignmentEntity assignment = new RosterAssignmentEntity();
        assignment.setStaffId(staffId);
        assignment.setTeamId(teamId);
        assignment.setAssignmentDate(date);
        assignment.setShiftDefinitionId(shiftDefinitionId);
        assignment.setShiftCode(shiftCode);
        return assignment;
    }

    private byte[] buildWorkbook(List<Map<String, String>> rows) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Monthly Roster");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("staff_id");
            header.createCell(1).setCellValue("team");
            for (int day = 1; day <= 31; day++) {
                header.createCell(day + 1).setCellValue(String.valueOf(day));
            }
            int rowIndex = 1;
            for (Map<String, String> row : rows) {
                var sheetRow = sheet.createRow(rowIndex++);
                sheetRow.createCell(0).setCellValue(row.getOrDefault("staff_id", ""));
                sheetRow.createCell(1).setCellValue(row.getOrDefault("team", ""));
                for (int day = 1; day <= 31; day++) {
                    sheetRow.createCell(day + 1).setCellValue(row.getOrDefault(String.valueOf(day), ""));
                }
            }
            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }
}
