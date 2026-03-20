package com.support.server.supportrosterserver.service.workspace;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.fesod.sheet.FesodSheet;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceImportApplyResponse;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceImportPreviewResponse;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceValidationIssueDto;
import com.support.server.supportrosterserver.entity.ColorDefinitionRow;
import com.support.server.supportrosterserver.entity.ShiftDefinitionRow;
import com.support.server.supportrosterserver.entity.StaffShiftRow;
import com.support.server.supportrosterserver.entity.workspace.ImportBatchEntity;
import com.support.server.supportrosterserver.entity.workspace.ImportIssueEntity;
import com.support.server.supportrosterserver.entity.workspace.ImportRecordEntity;
import com.support.server.supportrosterserver.entity.workspace.RosterAssignmentEntity;
import com.support.server.supportrosterserver.entity.workspace.ShiftDefinitionEntity;
import com.support.server.supportrosterserver.entity.workspace.ShiftDefinitionTeamRelEntity;
import com.support.server.supportrosterserver.entity.workspace.StaffEntity;
import com.support.server.supportrosterserver.entity.workspace.TeamEntity;
import com.support.server.supportrosterserver.exception.BadRequestException;
import com.support.server.supportrosterserver.mapper.ImportBatchMapper;
import com.support.server.supportrosterserver.mapper.ImportIssueMapper;
import com.support.server.supportrosterserver.mapper.ImportRecordMapper;
import com.support.server.supportrosterserver.mapper.RosterAssignmentMapper;
import com.support.server.supportrosterserver.mapper.ShiftDefinitionMapper;
import com.support.server.supportrosterserver.mapper.ShiftDefinitionTeamRelMapper;
import com.support.server.supportrosterserver.mapper.StaffMapper;
import com.support.server.supportrosterserver.mapper.TeamMapper;
import com.support.server.supportrosterserver.repository.ColorDefinitionDataListener;
import com.support.server.supportrosterserver.repository.ShiftDefinitionDataListener;
import com.support.server.supportrosterserver.repository.StaffShiftDataListener;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WorkspaceImportService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MMM dd", Locale.ENGLISH);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final Set<String> PRIMARY_CODES = Set.of("OC", "DS", "NS", "A", "B", "D");

    private final ImportBatchMapper importBatchMapper;
    private final ImportRecordMapper importRecordMapper;
    private final ImportIssueMapper importIssueMapper;
    private final ShiftDefinitionMapper shiftDefinitionMapper;
    private final ShiftDefinitionTeamRelMapper shiftDefinitionTeamRelMapper;
    private final StaffMapper staffMapper;
    private final RosterAssignmentMapper rosterAssignmentMapper;
    private final TeamMapper teamMapper;
    private final WorkspaceLookupService lookupService;
    private final ObjectMapper objectMapper;

    @Transactional
    public WorkspaceImportPreviewResponse previewImport(MultipartFile file, Integer year, Integer month, String operator) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Import file is required.");
        }
        YearMonth targetMonth = resolveMonth(year, month);
        ImportBatchEntity batch = new ImportBatchEntity();
        batch.setRosterYear(targetMonth.getYear());
        batch.setRosterMonth(targetMonth.getMonthValue());
        batch.setFileName(file.getOriginalFilename());
        batch.setStatus("PREVIEWING");
        batch.setOperatorName(operator == null || operator.isBlank() ? "system" : operator);
        importBatchMapper.insert(batch);

        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("workspace-import-", ".xlsx");
            file.transferTo(tempFile);

            ShiftDefinitionDataListener shiftListener = new ShiftDefinitionDataListener();
            StaffShiftDataListener staffListener = new StaffShiftDataListener();
            ColorDefinitionDataListener colorListener = new ColorDefinitionDataListener();
            FesodSheet.read(tempFile.toString(), ShiftDefinitionRow.class, shiftListener).sheet(0).doRead();
            FesodSheet.read(tempFile.toString(), StaffShiftRow.class, staffListener).sheet(1).doRead();
            try {
                FesodSheet.read(tempFile.toString(), ColorDefinitionRow.class, colorListener).sheet(2).doRead();
            } catch (Exception ignored) {
            }

            Map<String, String> colorHexByCode = new HashMap<>();
            List<ImportRecordEntity> records = new ArrayList<>();
            List<ImportIssueEntity> issues = new ArrayList<>();
            Map<String, TeamEntity> teamsByName = lookupService.listTeams().stream()
                .collect(java.util.stream.Collectors.toMap(
                    team -> team.getName().toLowerCase(Locale.ROOT),
                    team -> team,
                    (left, right) -> left,
                    HashMap::new
                ));
            Set<String> validShiftKeys = new HashSet<>();
            Set<String> scheduledPrimaryCoverage = new HashSet<>();
            Set<String> staffDayKeys = new HashSet<>();

            for (ColorDefinitionRow colorRow : colorListener.getDataList()) {
                if (colorRow.getCode() == null || colorRow.getCode().isBlank() || "code".equalsIgnoreCase(colorRow.getCode())) {
                    continue;
                }
                colorHexByCode.put(colorRow.getCode(), colorRow.getHex());
                records.add(buildRecord(batch.getId(), "Color Definitions", records.size() + 1, "COLOR", colorRow, true));
            }

            int rowIndex = 1;
            for (ShiftDefinitionRow row : shiftListener.getDataList()) {
                if (row.getTeam() == null || row.getTeam().isBlank() || "team".equalsIgnoreCase(row.getTeam())) {
                    continue;
                }
                if (row.getCode() == null || row.getCode().isBlank()) {
                    continue;
                }
                if (row.getStartTime() != null && row.getStartTime().startsWith("#")) {
                    continue;
                }
                if (parseTime(row.getStartTime()) == null && parseTime(row.getEndTime()) == null && row.getTimezone() == null) {
                    continue;
                }
                boolean valid = true;
                List<TeamEntity> teams = resolveTeams(row.getTeam(), teamsByName);
                if (row.getCode() == null || row.getCode().isBlank()) {
                    valid = false;
                    issues.add(buildIssue(batch.getId(), "medium", "Invalid Shift Code", "Shift definition code is missing.", row.getTeam(), null, null));
                }
                if (parseTime(row.getStartTime()) == null || parseTime(row.getEndTime()) == null) {
                    valid = false;
                    issues.add(buildIssue(batch.getId(), "medium", "Invalid Shift Definition", "Shift definition time range is invalid for team '" + row.getTeam() + "'.", row.getTeam(), null, null));
                }
                if (teams.isEmpty()) {
                    valid = false;
                    issues.add(buildIssue(batch.getId(), "medium", "Missing Team", "Team '" + row.getTeam() + "' does not exist.", row.getTeam(), null, null));
                }
                for (TeamEntity team : teams) {
                    validShiftKeys.add(team.getId() + "|" + row.getCode());
                }
                Map<String, Object> payload = new HashMap<>();
                payload.put("teams", teams.stream().map(TeamEntity::getName).toList());
                payload.put("code", row.getCode());
                payload.put("meaning", row.getMeaning());
                payload.put("startTime", row.getStartTime());
                payload.put("endTime", row.getEndTime());
                payload.put("timezone", lookupService.normalizeWorkspaceTimezone(row.getTimezone()));
                payload.put("showOnRosterPage", row.getShowOnRosterPage());
                payload.put("remark", row.getRemark());
                payload.put("colorHex", colorHexByCode.get(row.getCode()));
                records.add(buildRecord(batch.getId(), "Shift Definitions", rowIndex++, "SHIFT_DEFINITION", payload, valid));
            }

            Map<Long, List<Long>> databaseTeamIdsByDefinitionId = shiftDefinitionTeamRelMapper.selectList(Wrappers.<ShiftDefinitionTeamRelEntity>lambdaQuery())
                .stream()
                .collect(Collectors.groupingBy(
                    ShiftDefinitionTeamRelEntity::getShiftDefinitionId,
                    Collectors.mapping(ShiftDefinitionTeamRelEntity::getTeamId, Collectors.toList())
                ));
            for (ShiftDefinitionEntity entity : shiftDefinitionMapper.selectList(Wrappers.lambdaQuery())) {
                for (Long teamId : databaseTeamIdsByDefinitionId.getOrDefault(entity.getId(), List.of())) {
                    validShiftKeys.add(teamId + "|" + entity.getCode());
                }
            }

            rowIndex = 1;
            for (StaffShiftRow row : staffListener.getDataList()) {
                if (row.getName() == null || row.getName().isBlank() || "name".equalsIgnoreCase(row.getName())) {
                    continue;
                }
                boolean valid = true;
                TeamEntity team = row.getTeam() == null ? null : teamsByName.get(row.getTeam().trim().toLowerCase(Locale.ROOT));
                if (parseLong(row.getStaffId()) == null) {
                    valid = false;
                    issues.add(buildIssue(batch.getId(), "medium", "Invalid Staff ID", "Staff ID is missing or not numeric for row '" + row.getName() + "'.", row.getTeam(), row.getName(), null));
                }
                if (row.getTeam() == null || row.getTeam().isBlank() || team == null) {
                    valid = false;
                    issues.add(buildIssue(batch.getId(), "medium", "Missing Team", "Team '" + row.getTeam() + "' does not exist in import or database.", row.getTeam(), row.getName(), null));
                }
                if (lookupService.inferTimezone(row.getRegion(), row.getTeam()) == null) {
                    valid = false;
                    issues.add(buildIssue(batch.getId(), "low", "Time Zone Ambiguity", "Staff '" + row.getName() + "' has no inferable timezone.", row.getTeam(), row.getName(), null));
                }

                for (int day = 1; day <= targetMonth.lengthOfMonth(); day++) {
                    String shiftCode = row.getShiftCodeByDay(day);
                    if (shiftCode == null || shiftCode.isBlank()) {
                        continue;
                    }
                    String directKey = team == null ? null : team.getId() + "|" + shiftCode;
                    boolean shiftExists = directKey != null && validShiftKeys.contains(directKey);
                    if (!shiftExists) {
                        valid = false;
                        issues.add(buildIssue(batch.getId(), "medium", "Invalid Shift Code", "Code '" + shiftCode + "' not found for team '" + row.getTeam() + "'.", row.getTeam(), row.getName(), targetMonth.atDay(day)));
                    }
                    String staffDayKey = row.getStaffId() + "|" + day;
                    if (!staffDayKeys.add(staffDayKey)) {
                        valid = false;
                        issues.add(buildIssue(batch.getId(), "high", "Overlapping Assignment", "Staff '" + row.getName() + "' has duplicate assignments on the same day.", row.getTeam(), row.getName(), targetMonth.atDay(day)));
                    }
                    if (PRIMARY_CODES.contains(shiftCode) && team != null) {
                        scheduledPrimaryCoverage.add(team.getId() + "|" + day);
                    }
                }
                records.add(buildRecord(batch.getId(), "Staff Shifts", rowIndex++, "STAFF_SHIFT", row, valid));
            }

            for (TeamEntity team : teamsByName.values()) {
                for (int day = 1; day <= targetMonth.lengthOfMonth(); day++) {
                    if (!scheduledPrimaryCoverage.contains(team.getId() + "|" + day)) {
                        issues.add(buildIssue(batch.getId(), "low", "Missing Primary Coverage", "No primary shift scheduled for " + team.getName() + " on " + targetMonth.atDay(day).format(DATE_FORMATTER) + ".", team.getName(), null, targetMonth.atDay(day)));
                    }
                }
            }

            records.forEach(importRecordMapper::insert);
            issues.forEach(importIssueMapper::insert);

            long validRecordCount = records.stream().filter(ImportRecordEntity::getValid).count();
            boolean hasBlockingIssues = issues.stream().anyMatch(i -> "high".equals(i.getSeverity()) || "medium".equals(i.getSeverity()));
            batch.setStatus(hasBlockingIssues ? "INVALID" : "VALIDATED");
            batch.setTotalRecords(records.size());
            batch.setValidRecords((int) validRecordCount);
            batch.setInvalidRecords(issues.size());
            importBatchMapper.updateById(batch);

            return new WorkspaceImportPreviewResponse(
                batch.getId(),
                batch.getRosterYear(),
                batch.getRosterMonth(),
                batch.getStatus(),
                batch.getTotalRecords(),
                batch.getValidRecords(),
                batch.getInvalidRecords(),
                issues.stream().map(this::toDto).toList()
            );
        } catch (IOException ex) {
            throw new BadRequestException("Failed to read import file: " + ex.getMessage());
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                }
            }
        }
    }

    @Transactional
    public WorkspaceImportApplyResponse applyImport(Long batchId, String operator) {
        ImportBatchEntity batch = importBatchMapper.selectById(batchId);
        if (batch == null) {
            throw new BadRequestException("Import batch not found.");
        }
        if (!"VALIDATED".equals(batch.getStatus())) {
            throw new BadRequestException("Only validated import batches can be applied.");
        }

        List<ImportRecordEntity> records = importRecordMapper.selectList(Wrappers.<ImportRecordEntity>lambdaQuery()
            .eq(ImportRecordEntity::getBatchId, batchId)
            .eq(ImportRecordEntity::getValid, true)
            .orderByAsc(ImportRecordEntity::getRecordType)
            .orderByAsc(ImportRecordEntity::getRowNumber));

        YearMonth targetMonth = YearMonth.of(batch.getRosterYear(), batch.getRosterMonth());

        rosterAssignmentMapper.delete(Wrappers.<RosterAssignmentEntity>lambdaQuery()
            .between(RosterAssignmentEntity::getAssignmentDate, targetMonth.atDay(1), targetMonth.atEndOfMonth()));

        for (ImportRecordEntity record : records) {
            if ("SHIFT_DEFINITION".equals(record.getRecordType())) {
                @SuppressWarnings("unchecked")
                Map<String, Object> payload = readValue(record.getPayloadJson(), Map.class);
                @SuppressWarnings("unchecked")
                List<String> teamNames = (List<String>) payload.get("teams");
                List<TeamEntity> teams = resolveTeams(teamNames, teamMapper.selectList(Wrappers.<TeamEntity>lambdaQuery()));
                if (teams.isEmpty()) {
                    throw new BadRequestException("At least one team is required for shift definition import.");
                }

                ShiftDefinitionEntity entity = findExistingShiftDefinition(teams.get(0).getId(), String.valueOf(payload.get("code")));
                if (entity == null) {
                    entity = new ShiftDefinitionEntity();
                }
                entity.setTeamId(teams.get(0).getId());
                entity.setRoleGroupId(null);
                entity.setCode(String.valueOf(payload.get("code")));
                entity.setMeaning((String) payload.get("meaning"));
                entity.setStartTime(parseTime((String) payload.get("startTime")));
                entity.setEndTime(parseTime((String) payload.get("endTime")));
                entity.setTimezone(lookupService.normalizeWorkspaceTimezone((String) payload.get("timezone")));
                entity.setVisible("Y".equalsIgnoreCase(String.valueOf(payload.get("showOnRosterPage"))));
                entity.setPrimaryShift(PRIMARY_CODES.contains(entity.getCode()));
                entity.setRemark((String) payload.get("remark"));
                entity.setColorHex((String) payload.get("colorHex"));
                if (entity.getId() == null) {
                    shiftDefinitionMapper.insert(entity);
                } else {
                    shiftDefinitionMapper.updateById(entity);
                }
                replaceShiftTeams(entity.getId(), teams.stream().map(TeamEntity::getId).toList());
            }
        }
        for (ImportRecordEntity record : records) {
            if (!"STAFF_SHIFT".equals(record.getRecordType())) {
                continue;
            }
            StaffShiftRow row = readValue(record.getPayloadJson(), StaffShiftRow.class);
            TeamEntity team = teamMapper.selectOne(Wrappers.<TeamEntity>lambdaQuery()
                .eq(TeamEntity::getName, row.getTeam())
                .last("limit 1"));
            if (team == null) {
                throw new BadRequestException("No team found for '" + row.getTeam() + "'.");
            }

            StaffEntity staff = staffMapper.selectOne(Wrappers.<StaffEntity>lambdaQuery()
                .eq(StaffEntity::getStaffCode, row.getStaffId())
                .last("limit 1"));
            if (staff == null) {
                staff = new StaffEntity();
            }
            staff.setStaffCode(row.getStaffId());
            staff.setName(row.getName());
            staff.setRegion(row.getRegion());
            staff.setTimezone(lookupService.normalizeWorkspaceTimezone(lookupService.inferTimezone(row.getRegion(), row.getTeam())));
            staff.setRoleName(team.getName());
            staff.setTeamId(team.getId());
            staff.setRoleGroupId(null);
            staff.setStatus("Active");
            staff.setPhone(row.getContact());
            staff.setNotes(row.getNotes());
            if (staff.getId() == null) {
                staffMapper.insert(staff);
            } else {
                staffMapper.updateById(staff);
            }

            for (int day = 1; day <= targetMonth.lengthOfMonth(); day++) {
                String shiftCode = row.getShiftCodeByDay(day);
                if (shiftCode == null || shiftCode.isBlank()) {
                    continue;
                }
                ShiftDefinitionEntity shiftDefinition = findExistingShiftDefinition(team.getId(), shiftCode);
                if (shiftDefinition == null) {
                    continue;
                }
                RosterAssignmentEntity assignment = new RosterAssignmentEntity();
                assignment.setStaffId(staff.getId());
                assignment.setRoleGroupId(null);
                assignment.setTeamId(team.getId());
                assignment.setShiftDefinitionId(shiftDefinition.getId());
                assignment.setAssignmentDate(targetMonth.atDay(day));
                assignment.setShiftCode(shiftCode);
                assignment.setSourceType("IMPORT");
                assignment.setNotes(null);
                rosterAssignmentMapper.insert(assignment);
            }
        }

        batch.setStatus("APPLIED");
        batch.setAppliedTime(LocalDateTime.now());
        batch.setOperatorName(operator == null || operator.isBlank() ? batch.getOperatorName() : operator);
        importBatchMapper.updateById(batch);

        return new WorkspaceImportApplyResponse(batch.getId(), batch.getRosterYear(), batch.getRosterMonth(), batch.getStatus(), records.size());
    }

    public ResponseEntity<byte[]> exportRoster(Integer year, Integer month) {
        YearMonth targetMonth = resolveMonth(year, month);
        Map<Long, TeamEntity> teamMap = lookupService.teamMap();
        List<ShiftDefinitionEntity> shiftDefinitions = shiftDefinitionMapper.selectList(Wrappers.<ShiftDefinitionEntity>lambdaQuery()
            .orderByAsc(ShiftDefinitionEntity::getTeamId)
            .orderByAsc(ShiftDefinitionEntity::getCode));
        List<StaffEntity> staffList = new ArrayList<>(staffMapper.selectList(Wrappers.<StaffEntity>lambdaQuery()));
        staffList.sort(Comparator
            .comparing((StaffEntity staff) -> {
                TeamEntity team = staff.getTeamId() == null ? null : teamMap.get(staff.getTeamId());
                return team == null || team.getDisplayOrder() == null ? Integer.MAX_VALUE : team.getDisplayOrder();
            })
            .thenComparing(staff -> staff.getStaffCode() == null ? "" : staff.getStaffCode())
            .thenComparing(staff -> staff.getName() == null ? "" : staff.getName()));

        List<RosterAssignmentEntity> assignments = rosterAssignmentMapper.selectList(Wrappers.<RosterAssignmentEntity>lambdaQuery()
            .between(RosterAssignmentEntity::getAssignmentDate, targetMonth.atDay(1), targetMonth.atEndOfMonth())
            .orderByAsc(RosterAssignmentEntity::getStaffId)
            .orderByAsc(RosterAssignmentEntity::getAssignmentDate));
        Map<String, String> schedule = new HashMap<>();
        for (RosterAssignmentEntity assignment : assignments) {
            schedule.put(assignment.getStaffId() + "|" + assignment.getAssignmentDate().getDayOfMonth(), assignment.getShiftCode());
        }

        try (Workbook workbook = loadTemplateWorkbook(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            populateShiftDefinitionsSheet(workbook.getSheet("Shift Definitions"), shiftDefinitions, teamMap);
            populateStaffShiftsSheet(workbook.getSheet("Staff Shifts"), staffList, teamMap, schedule, targetMonth);
            populateColorDefinitionsSheet(workbook.getSheet("Color Definitions"), shiftDefinitions);
            workbook.write(outputStream);

            String fileName = "workspace-roster-" + targetMonth.getYear() + "-" + String.format("%02d", targetMonth.getMonthValue()) + ".xlsx";
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + fileName)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(outputStream.toByteArray());
        } catch (IOException ex) {
            throw new BadRequestException("Failed to export roster workbook: " + ex.getMessage());
        }
    }

    public ResponseEntity<byte[]> downloadTemplate() {
        try {
            ClassPathResource resource = new ClassPathResource("roster.xlsx");
            byte[] content = resource.getContentAsByteArray();
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=import-template.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(content);
        } catch (IOException ex) {
            throw new BadRequestException("Failed to read template file: " + ex.getMessage());
        }
    }

    private Workbook loadTemplateWorkbook() throws IOException {
        ClassPathResource resource = new ClassPathResource("roster.xlsx");
        return WorkbookFactory.create(resource.getInputStream());
    }

    private void populateShiftDefinitionsSheet(Sheet sheet, List<ShiftDefinitionEntity> shiftDefinitions, Map<Long, TeamEntity> teamMap) {
        Map<Long, List<TeamEntity>> teamsByShiftDefinitionId = shiftDefinitionTeamRelMapper.selectList(Wrappers.<ShiftDefinitionTeamRelEntity>lambdaQuery())
            .stream()
            .collect(Collectors.groupingBy(
                ShiftDefinitionTeamRelEntity::getShiftDefinitionId,
                Collectors.mapping(relation -> teamMap.get(relation.getTeamId()), Collectors.toList())
            ));
        List<List<String>> rows = new ArrayList<>();

        for (ShiftDefinitionEntity shiftDefinition : shiftDefinitions) {
            String teamNames = teamsByShiftDefinitionId.getOrDefault(shiftDefinition.getId(), List.of()).stream()
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(team -> team.getDisplayOrder() == null ? Integer.MAX_VALUE : team.getDisplayOrder()))
                .map(TeamEntity::getName)
                .collect(Collectors.joining(", "));
            rows.add(List.of(
                safeString(teamNames),
                safeString(shiftDefinition.getCode()),
                safeString(shiftDefinition.getMeaning()),
                formatTime(shiftDefinition.getStartTime()),
                formatTime(shiftDefinition.getEndTime()),
                lookupService.normalizeWorkspaceTimezone(shiftDefinition.getTimezone()),
                Boolean.TRUE.equals(shiftDefinition.getVisible()) ? "Y" : "N",
                safeString(shiftDefinition.getRemark())
            ));
        }

        replaceSheetRows(sheet, rows);
    }

    private void populateStaffShiftsSheet(Sheet sheet, List<StaffEntity> staffList, Map<Long, TeamEntity> teamMap,
                                          Map<String, String> schedule, YearMonth targetMonth) {
        List<List<String>> rows = new ArrayList<>();

        for (StaffEntity staff : staffList) {
            TeamEntity team = staff.getTeamId() == null ? null : teamMap.get(staff.getTeamId());
            List<String> row = new ArrayList<>();
            row.add(safeString(staff.getName()));
            row.add(safeString(staff.getStaffCode()));
            row.add(team == null ? "" : safeString(team.getName()));
            row.add(safeString(staff.getRegion()));
            row.add(safeString(staff.getPhone()));
            row.add(safeString(staff.getNotes()));

            for (int day = 1; day <= 31; day++) {
                if (day <= targetMonth.lengthOfMonth()) {
                    row.add(safeString(schedule.get(staff.getId() + "|" + day)));
                } else {
                    row.add("");
                }
            }

            rows.add(row);
        }

        replaceSheetRows(sheet, rows);
    }

    private void populateColorDefinitionsSheet(Sheet sheet, List<ShiftDefinitionEntity> shiftDefinitions) {
        Map<String, ShiftDefinitionEntity> uniqueDefinitions = new LinkedHashMap<>();
        for (ShiftDefinitionEntity shiftDefinition : shiftDefinitions) {
            if (shiftDefinition.getCode() == null || shiftDefinition.getCode().isBlank()) {
                continue;
            }
            uniqueDefinitions.putIfAbsent(shiftDefinition.getCode(), shiftDefinition);
        }

        List<List<String>> rows = new ArrayList<>();
        for (ShiftDefinitionEntity shiftDefinition : uniqueDefinitions.values()) {
            rows.add(List.of(
                safeString(shiftDefinition.getCode()),
                safeString(shiftDefinition.getCode()),
                toRgbString(shiftDefinition.getColorHex()),
                safeString(shiftDefinition.getColorHex())
            ));
        }

        replaceSheetRows(sheet, rows);
    }

    private void replaceSheetRows(Sheet sheet, List<List<String>> rows) {
        if (sheet == null) {
            return;
        }

        Row styleRow = sheet.getRow(Math.min(1, sheet.getLastRowNum()));
        int maxColumns = styleRow == null ? 0 : styleRow.getLastCellNum();
        int rowIndex = 1;

        for (List<String> rowValues : rows) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                row = sheet.createRow(rowIndex);
            }

            int columns = Math.max(maxColumns, rowValues.size());
            for (int columnIndex = 0; columnIndex < columns; columnIndex++) {
                Cell cell = row.getCell(columnIndex, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                if (styleRow != null) {
                    Cell styleCell = styleRow.getCell(columnIndex, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                    cell.setCellStyle(styleCell.getCellStyle());
                }
                cell.setCellValue(columnIndex < rowValues.size() ? safeString(rowValues.get(columnIndex)) : "");
            }

            rowIndex++;
        }

        for (int clearIndex = rowIndex; clearIndex <= sheet.getLastRowNum(); clearIndex++) {
            Row row = sheet.getRow(clearIndex);
            if (row == null) {
                continue;
            }
            int lastCellNum = Math.max(row.getLastCellNum(), maxColumns);
            for (int columnIndex = 0; columnIndex < lastCellNum; columnIndex++) {
                Cell cell = row.getCell(columnIndex, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                if (styleRow != null) {
                    Cell styleCell = styleRow.getCell(columnIndex, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                    cell.setCellStyle(styleCell.getCellStyle());
                }
                cell.setCellValue("");
            }
        }
    }

    private String formatTime(LocalTime value) {
        return value == null ? "" : value.format(TIME_FORMATTER);
    }

    private String toRgbString(String hex) {
        if (hex == null || !hex.matches("^#[0-9a-fA-F]{6}$")) {
            return "";
        }

        int red = Integer.parseInt(hex.substring(1, 3), 16);
        int green = Integer.parseInt(hex.substring(3, 5), 16);
        int blue = Integer.parseInt(hex.substring(5, 7), 16);
        return red + " " + green + " " + blue;
    }

    private String safeString(String value) {
        return value == null ? "" : value;
    }

    private ImportRecordEntity buildRecord(Long batchId, String sheetName, int rowNumber, String recordType, Object payload, boolean valid) {
        ImportRecordEntity entity = new ImportRecordEntity();
        entity.setBatchId(batchId);
        entity.setSheetName(sheetName);
        entity.setRowNumber(rowNumber);
        entity.setRecordType(recordType);
        entity.setPayloadJson(writeValue(payload));
        entity.setValid(valid);
        return entity;
    }

    private ImportIssueEntity buildIssue(Long batchId, String severity, String issueType, String description,
                                         String teamName, String staffName, LocalDate issueDate) {
        ImportIssueEntity issue = new ImportIssueEntity();
        issue.setBatchId(batchId);
        issue.setSeverity(severity);
        issue.setIssueType(issueType);
        issue.setDescription(description);
        issue.setTeamName(teamName);
        issue.setStaffName(staffName);
        issue.setIssueDate(issueDate);
        issue.setResolved(false);
        return issue;
    }

    private WorkspaceValidationIssueDto toDto(ImportIssueEntity issue) {
        return new WorkspaceValidationIssueDto(
            issue.getId(),
            issue.getSeverity(),
            issue.getIssueType(),
            issue.getDescription(),
            issue.getTeamName(),
            issue.getIssueDate() == null ? "-" : issue.getIssueDate().format(DATE_FORMATTER),
            false,
            null
        );
    }

    private <T> T readValue(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JacksonException ex) {
            throw new BadRequestException("Failed to parse import record payload.");
        }
    }

    private String writeValue(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException ex) {
            throw new BadRequestException("Failed to serialize import record payload.");
        }
    }

    private Long parseLong(String value) {
        try {
            return value == null ? null : Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private LocalTime parseTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String[] parts = value.split(":");
        try {
            return LocalTime.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), parts.length > 2 ? Integer.parseInt(parts[2]) : 0);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private List<TeamEntity> resolveTeams(String rawTeamNames, Map<String, TeamEntity> teamsByName) {
        if (rawTeamNames == null || rawTeamNames.isBlank()) {
            return List.of();
        }

        return java.util.Arrays.stream(rawTeamNames.split(",|;|\\n"))
            .map(String::trim)
            .filter(name -> !name.isBlank())
            .map(name -> teamsByName.get(name.toLowerCase(Locale.ROOT)))
            .filter(java.util.Objects::nonNull)
            .distinct()
            .toList();
    }

    private List<TeamEntity> resolveTeams(List<String> teamNames, List<TeamEntity> teams) {
        if (teamNames == null || teamNames.isEmpty()) {
            return List.of();
        }

        Map<String, TeamEntity> teamsByName = teams.stream().collect(Collectors.toMap(
            team -> team.getName().toLowerCase(Locale.ROOT),
            team -> team,
            (left, right) -> left,
            HashMap::new
        ));
        return teamNames.stream()
            .map(name -> teamsByName.get(name.toLowerCase(Locale.ROOT)))
            .filter(java.util.Objects::nonNull)
            .distinct()
            .toList();
    }

    private ShiftDefinitionEntity findExistingShiftDefinition(Long teamId, String code) {
        List<ShiftDefinitionEntity> candidates = shiftDefinitionMapper.selectList(Wrappers.<ShiftDefinitionEntity>lambdaQuery()
            .eq(ShiftDefinitionEntity::getCode, code));
        if (candidates.isEmpty()) {
            return null;
        }

        Set<Long> candidateIds = candidates.stream().map(ShiftDefinitionEntity::getId).collect(Collectors.toSet());
        Set<Long> matchingIds = shiftDefinitionTeamRelMapper.selectList(Wrappers.<ShiftDefinitionTeamRelEntity>lambdaQuery()
                .eq(ShiftDefinitionTeamRelEntity::getTeamId, teamId)
                .in(ShiftDefinitionTeamRelEntity::getShiftDefinitionId, candidateIds))
            .stream()
            .map(ShiftDefinitionTeamRelEntity::getShiftDefinitionId)
            .collect(Collectors.toSet());

        return candidates.stream().filter(candidate -> matchingIds.contains(candidate.getId())).findFirst().orElse(null);
    }

    private void replaceShiftTeams(Long shiftDefinitionId, List<Long> teamIds) {
        shiftDefinitionTeamRelMapper.delete(Wrappers.<ShiftDefinitionTeamRelEntity>lambdaQuery()
            .eq(ShiftDefinitionTeamRelEntity::getShiftDefinitionId, shiftDefinitionId));
        for (Long teamId : teamIds.stream().distinct().toList()) {
            ShiftDefinitionTeamRelEntity relation = new ShiftDefinitionTeamRelEntity();
            relation.setShiftDefinitionId(shiftDefinitionId);
            relation.setTeamId(teamId);
            shiftDefinitionTeamRelMapper.insert(relation);
        }
    }

    private YearMonth resolveMonth(Integer year, Integer month) {
        YearMonth now = YearMonth.now();
        return YearMonth.of(year == null ? now.getYear() : year, month == null ? now.getMonthValue() : month);
    }

}