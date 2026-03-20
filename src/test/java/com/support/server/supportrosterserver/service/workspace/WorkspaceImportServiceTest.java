package com.support.server.supportrosterserver.service.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import com.support.server.supportrosterserver.dto.workspace.WorkspaceImportPreviewResponse;
import com.support.server.supportrosterserver.entity.workspace.ImportBatchEntity;
import com.support.server.supportrosterserver.entity.workspace.RosterAssignmentEntity;
import com.support.server.supportrosterserver.entity.workspace.ShiftDefinitionEntity;
import com.support.server.supportrosterserver.entity.workspace.ShiftDefinitionTeamRelEntity;
import com.support.server.supportrosterserver.entity.workspace.StaffEntity;
import com.support.server.supportrosterserver.entity.workspace.TeamEntity;
import com.support.server.supportrosterserver.mapper.ImportBatchMapper;
import com.support.server.supportrosterserver.mapper.ImportIssueMapper;
import com.support.server.supportrosterserver.mapper.ImportRecordMapper;
import com.support.server.supportrosterserver.mapper.RosterAssignmentMapper;
import com.support.server.supportrosterserver.mapper.ShiftDefinitionMapper;
import com.support.server.supportrosterserver.mapper.ShiftDefinitionTeamRelMapper;
import com.support.server.supportrosterserver.mapper.StaffMapper;

import tools.jackson.databind.ObjectMapper;

class WorkspaceImportServiceTest {

    private ImportBatchMapper importBatchMapper;
    private ImportRecordMapper importRecordMapper;
    private ImportIssueMapper importIssueMapper;
    private ShiftDefinitionMapper shiftDefinitionMapper;
    private ShiftDefinitionTeamRelMapper shiftDefinitionTeamRelMapper;
    private StaffMapper staffMapper;
    private RosterAssignmentMapper rosterAssignmentMapper;
    private WorkspaceLookupService lookupService;
    private WorkspaceImportService workspaceImportService;

    @BeforeEach
    void setUp() {
        importBatchMapper = mock(ImportBatchMapper.class);
        importRecordMapper = mock(ImportRecordMapper.class);
        importIssueMapper = mock(ImportIssueMapper.class);
        shiftDefinitionMapper = mock(ShiftDefinitionMapper.class);
        shiftDefinitionTeamRelMapper = mock(ShiftDefinitionTeamRelMapper.class);
        staffMapper = mock(StaffMapper.class);
        rosterAssignmentMapper = mock(RosterAssignmentMapper.class);
        lookupService = mock(WorkspaceLookupService.class);

        workspaceImportService = new WorkspaceImportService(
            importBatchMapper,
            importRecordMapper,
            importIssueMapper,
            shiftDefinitionMapper,
            shiftDefinitionTeamRelMapper,
            staffMapper,
            rosterAssignmentMapper,
            mock(com.support.server.supportrosterserver.mapper.TeamMapper.class),
            lookupService,
            new ObjectMapper()
        );
    }

    @Test
    void shouldValidatePreviewWhenStaffIdContainsLetters() throws IOException {
        TeamEntity team = new TeamEntity();
        team.setId(301L);
        team.setName("China Support");

        doAnswer(invocation -> {
            ImportBatchEntity batch = invocation.getArgument(0);
            batch.setId(9001L);
            return 1;
        }).when(importBatchMapper).insert(any(ImportBatchEntity.class));

        when(lookupService.listTeams()).thenReturn(List.of(team));
        when(lookupService.normalizeWorkspaceTimezone(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(lookupService.inferTimezone("China", "China Support")).thenReturn("HKT");
        when(shiftDefinitionTeamRelMapper.selectList(any())).thenReturn(List.of());
        when(shiftDefinitionMapper.selectList(any())).thenReturn(List.of());

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "roundtrip.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            buildWorkbook()
        );

        WorkspaceImportPreviewResponse response = workspaceImportService.previewImport(file, 2026, 3, "tester");

        assertEquals("VALIDATED", response.getStatus());
        assertTrue(response.getIssues().stream().noneMatch(issue -> "Invalid Staff ID".equals(issue.getType())));
    }

    @Test
    void shouldValidateExportedWorkbookRoundtrip() throws IOException {
        TeamEntity l1 = buildTeam(101L, "L1");
        TeamEntity apL2 = buildTeam(102L, "AP L2");

        ShiftDefinitionEntity a = buildShiftDefinition(1001L, 101L, "A", "Day Shift", LocalTime.of(9, 0), LocalTime.of(18, 0), "#FFA500");
        ShiftDefinitionEntity b = buildShiftDefinition(1002L, 101L, "B", "Late Shift", LocalTime.of(18, 0), LocalTime.of(23, 0), "#FF8C00");
        ShiftDefinitionEntity ds = buildShiftDefinition(1003L, 102L, "DS", "Day Shift", LocalTime.of(9, 30), LocalTime.of(18, 30), "#4169E1");
        ShiftDefinitionEntity ns = buildShiftDefinition(1004L, 102L, "NS", "Night Shift", LocalTime.of(18, 30), LocalTime.of(9, 30), "#191970");
        ShiftDefinitionEntity oc = buildShiftDefinition(1005L, 101L, "OC", "On Call", LocalTime.of(0, 0), LocalTime.of(8, 0), "#22C55E");

        StaffEntity alice = new StaffEntity();
        alice.setId(201L);
        alice.setStaffCode("402X9");
        alice.setName("Alice");
        alice.setRegion("China");
        alice.setTeamId(101L);

        StaffEntity bob = new StaffEntity();
        bob.setId(202L);
        bob.setStaffCode("1002");
        bob.setName("Bob");
        bob.setRegion("China");
        bob.setTeamId(102L);

        StaffEntity bobDuplicate = new StaffEntity();
        bobDuplicate.setId(203L);
        bobDuplicate.setStaffCode("1002");
        bobDuplicate.setName("Bob");
        bobDuplicate.setRegion("China");
        bobDuplicate.setTeamId(102L);

        doAnswer(invocation -> {
            ImportBatchEntity batch = invocation.getArgument(0);
            batch.setId(9002L);
            return 1;
        }).when(importBatchMapper).insert(any(ImportBatchEntity.class));

        when(lookupService.teamMap()).thenReturn(java.util.Map.of(101L, l1, 102L, apL2));
        when(lookupService.listTeams()).thenReturn(List.of(l1, apL2));
        when(lookupService.normalizeWorkspaceTimezone(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(lookupService.inferTimezone("China", "L1")).thenReturn("HKT");
        when(lookupService.inferTimezone("China", "AP L2")).thenReturn("HKT");
        when(shiftDefinitionMapper.selectList(any())).thenReturn(List.of(a, b, ds, ns, oc));
        when(shiftDefinitionTeamRelMapper.selectList(any())).thenReturn(List.of(
            buildRelation(1001L, 101L),
            buildRelation(1002L, 101L),
            buildRelation(1003L, 102L),
            buildRelation(1004L, 102L),
            buildRelation(1005L, 101L),
            buildRelation(1005L, 102L)
        ));
        when(staffMapper.selectList(any())).thenReturn(List.of(alice, bob, bobDuplicate));
        when(rosterAssignmentMapper.selectList(any())).thenReturn(buildMonthAssignments(alice, bob));

        byte[] exportedWorkbook = workspaceImportService.exportRoster(2026, 3).getBody();
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "workspace-roster-2026-03.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            exportedWorkbook
        );

        WorkspaceImportPreviewResponse response = workspaceImportService.previewImport(file, 2026, 3, "tester");

        assertEquals(1, countStaffRowsByCode(exportedWorkbook, "1002"));
        assertEquals("VALIDATED", response.getStatus());
        assertTrue(response.getIssues().stream().noneMatch(issue -> "Missing Primary Coverage".equals(issue.getType())));
        assertTrue(response.getIssues().stream().noneMatch(issue -> "Missing Team".equals(issue.getType())));
        assertTrue(response.getIssues().stream().noneMatch(issue -> "Invalid Shift Code".equals(issue.getType())));
    }

    private byte[] buildWorkbook() throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            var shiftSheet = workbook.createSheet("Shift Definitions");
            shiftSheet.createRow(0).createCell(0).setCellValue("team");
            var shiftRow = shiftSheet.createRow(1);
            shiftRow.createCell(0).setCellValue("China Support");
            shiftRow.createCell(1).setCellValue("A");
            shiftRow.createCell(2).setCellValue("Day Shift");
            shiftRow.createCell(3).setCellValue("09:00");
            shiftRow.createCell(4).setCellValue("18:00");
            shiftRow.createCell(5).setCellValue("HKT");
            shiftRow.createCell(6).setCellValue("Y");
            shiftRow.createCell(7).setCellValue("");

            var staffSheet = workbook.createSheet("Staff Shifts");
            staffSheet.createRow(0).createCell(0).setCellValue("name");
            var staffRow = staffSheet.createRow(1);
            staffRow.createCell(0).setCellValue("Alice");
            staffRow.createCell(1).setCellValue("402X9");
            staffRow.createCell(2).setCellValue("China Support");
            staffRow.createCell(3).setCellValue("China");
            staffRow.createCell(4).setCellValue("");
            staffRow.createCell(5).setCellValue("");
            staffRow.createCell(6).setCellValue("A");

            var colorSheet = workbook.createSheet("Color Definitions");
            colorSheet.createRow(0).createCell(0).setCellValue("code");
            var colorRow = colorSheet.createRow(1);
            colorRow.createCell(0).setCellValue("A");
            colorRow.createCell(1).setCellValue("Orange");
            colorRow.createCell(2).setCellValue("255 165 0");
            colorRow.createCell(3).setCellValue("#FFA500");

            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    private TeamEntity buildTeam(Long id, String name) {
        TeamEntity team = new TeamEntity();
        team.setId(id);
        team.setName(name);
        team.setVisible(true);
        team.setDisplayOrder(id.intValue());
        return team;
    }

    private ShiftDefinitionEntity buildShiftDefinition(Long id, Long teamId, String code, String meaning, LocalTime startTime, LocalTime endTime, String colorHex) {
        ShiftDefinitionEntity entity = new ShiftDefinitionEntity();
        entity.setId(id);
        entity.setTeamId(teamId);
        entity.setCode(code);
        entity.setMeaning(meaning);
        entity.setStartTime(startTime);
        entity.setEndTime(endTime);
        entity.setTimezone("HKT");
        entity.setVisible(true);
        entity.setColorHex(colorHex);
        return entity;
    }

    private ShiftDefinitionTeamRelEntity buildRelation(Long shiftDefinitionId, Long teamId) {
        ShiftDefinitionTeamRelEntity relation = new ShiftDefinitionTeamRelEntity();
        relation.setShiftDefinitionId(shiftDefinitionId);
        relation.setTeamId(teamId);
        return relation;
    }

    private List<RosterAssignmentEntity> buildMonthAssignments(StaffEntity alice, StaffEntity bob) {
        List<RosterAssignmentEntity> assignments = new ArrayList<>();
        for (int day = 1; day <= 31; day++) {
            assignments.add(buildAssignment(alice.getId(), alice.getTeamId(), LocalDate.of(2026, 3, day), day % 2 == 0 ? "B" : "A"));
            assignments.add(buildAssignment(bob.getId(), bob.getTeamId(), LocalDate.of(2026, 3, day), day % 2 == 0 ? "NS" : "DS"));
        }
        return assignments;
    }

    private RosterAssignmentEntity buildAssignment(Long staffId, Long teamId, LocalDate date, String shiftCode) {
        RosterAssignmentEntity assignment = new RosterAssignmentEntity();
        assignment.setStaffId(staffId);
        assignment.setTeamId(teamId);
        assignment.setAssignmentDate(date);
        assignment.setShiftCode(shiftCode);
        return assignment;
    }

    private int countStaffRowsByCode(byte[] workbookBytes, String staffCode) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(new java.io.ByteArrayInputStream(workbookBytes))) {
            int count = 0;
            var sheet = workbook.getSheet("Staff Shifts");
            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                var row = sheet.getRow(rowIndex);
                if (row == null || row.getCell(1) == null) {
                    continue;
                }
                if (staffCode.equals(row.getCell(1).getStringCellValue())) {
                    count++;
                }
            }
            return count;
        }
    }
}
