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

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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
import com.support.server.supportrosterserver.service.auth.AuthContextService;

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
    private final AuthContextService authContextService;
    private final WorkspaceShiftTimeSupport shiftTimeSupport;

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
        batch.setOperatorName(authContextService.currentActor(operator));
        importBatchMapper.insert(batch);

        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("workspace-import-", ".xlsx");
            file.transferTo(tempFile);

            ParsedImportWorkbook parsedWorkbook = readImportWorkbook(tempFile);

            Map<String, String> colorHexByCode = new HashMap<>();
            List<ImportRecordEntity> records = new ArrayList<>();
            List<ImportIssueEntity> issues = new ArrayList<>();
            Set<Long> referencedTeamIds = new HashSet<>();
            Map<String, TeamEntity> teamsByName = lookupService.listTeams().stream()
                .collect(java.util.stream.Collectors.toMap(
                    team -> team.getName().toLowerCase(Locale.ROOT),
                    team -> team,
                    (left, right) -> left,
                    HashMap::new
                ));
            Set<String> validShiftKeys = new HashSet<>();
            Set<String> staffDayKeys = new HashSet<>();

            for (ColorDefinitionRow colorRow : parsedWorkbook.colorRows()) {
                if (colorRow.getCode() == null || colorRow.getCode().isBlank() || "code".equalsIgnoreCase(colorRow.getCode())) {
                    continue;
                }
                colorHexByCode.put(colorRow.getCode(), colorRow.getHex());
                records.add(buildRecord(batch.getId(), "Color Definitions", records.size() + 1, "COLOR", colorRow, true));
            }

            int rowIndex = 1;
            for (ShiftDefinitionRow row : parsedWorkbook.shiftDefinitionRows()) {
                if (row.getTeam() == null || row.getTeam().isBlank() || "team".equalsIgnoreCase(row.getTeam())) {
                    continue;
                }
                if (row.getCode() == null || row.getCode().isBlank()) {
                    continue;
                }
                if (row.getStartTime() != null && row.getStartTime().startsWith("#")) {
                    continue;
                }
                Integer durationMinutes = parseDurationMinutes(row.getStartTime(), row.getEndTime());
                LocalTime startTime = parseTime(row.getStartTime());
                if (startTime == null && durationMinutes == null && row.getTimezone() == null) {
                    continue;
                }
                boolean valid = true;
                List<TeamEntity> teams = resolveTeams(row.getTeam(), teamsByName);
                if (row.getCode() == null || row.getCode().isBlank()) {
                    valid = false;
                    issues.add(buildIssue(batch.getId(), "medium", "Invalid Shift Code", "Shift definition code is missing.", row.getTeam(), null, null));
                }
                if (startTime == null || durationMinutes == null) {
                    valid = false;
                    issues.add(buildIssue(batch.getId(), "medium", "Invalid Shift Definition", "Shift definition time range is invalid for team '" + row.getTeam() + "'.", row.getTeam(), null, null));
                }
                if (teams.isEmpty()) {
                    valid = false;
                    issues.add(buildIssue(batch.getId(), "medium", "Missing Team", "Team '" + row.getTeam() + "' does not exist.", row.getTeam(), null, null));
                }
                for (TeamEntity team : teams) {
                    referencedTeamIds.add(team.getId());
                    validShiftKeys.add(team.getId() + "|" + row.getCode());
                }
                Map<String, Object> payload = new HashMap<>();
                payload.put("teams", teams.stream().map(TeamEntity::getName).toList());
                payload.put("code", row.getCode());
                payload.put("meaning", row.getMeaning());
                payload.put("startTime", row.getStartTime());
                payload.put("durationMinutes", durationMinutes);
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
            for (StaffShiftRow row : parsedWorkbook.staffShiftRows()) {
                if (row.getName() == null || row.getName().isBlank() || "name".equalsIgnoreCase(row.getName())) {
                    continue;
                }
                boolean valid = true;
                TeamEntity team = row.getTeam() == null ? null : teamsByName.get(row.getTeam().trim().toLowerCase(Locale.ROOT));
                if (team != null) {
                    referencedTeamIds.add(team.getId());
                }
                if (row.getStaffId() == null || row.getStaffId().isBlank()) {
                    valid = false;
                    issues.add(buildIssue(batch.getId(), "medium", "Invalid Staff ID", "Staff ID is missing for row '" + row.getName() + "'.", row.getTeam(), row.getName(), null));
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
                }
                records.add(buildRecord(batch.getId(), "Staff Shifts", rowIndex++, "STAFF_SHIFT", row, valid));
            }

            authContextService.requireWritableTeams(referencedTeamIds);

            records.forEach(importRecordMapper::insert);
            issues.forEach(importIssueMapper::insert);

            long validRecordCount = records.stream().filter(ImportRecordEntity::getValid).count();
            boolean hasBlockingIssues = issues.stream().anyMatch(i -> "high".equals(i.getSeverity()) || "medium".equals(i.getSeverity()));
            batch.setStatus(hasBlockingIssues ? "INVALID" : "VALIDATED");
            batch.setTotalRecords(records.size());
            batch.setValidRecords((int) validRecordCount);
            batch.setInvalidRecords(issues.size());
            importBatchMapper.updateById(batch);
            cleanupPreviousPreviewBatches(targetMonth, batch.getId());

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
        Set<Long> importTeamIds = collectImportTeamIds(records);
        authContextService.requireWritableTeams(importTeamIds);

        YearMonth targetMonth = YearMonth.of(batch.getRosterYear(), batch.getRosterMonth());

        rosterAssignmentMapper.delete(Wrappers.<RosterAssignmentEntity>lambdaQuery()
            .between(RosterAssignmentEntity::getAssignmentDate, targetMonth.atDay(1), targetMonth.atEndOfMonth())
            .in(RosterAssignmentEntity::getTeamId, importTeamIds));

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
                Integer durationMinutes = readDurationMinutes(payload.get("durationMinutes"));
                entity.setDurationMinutes(durationMinutes);
                entity.setEndTime(shiftTimeSupport.deriveEndTime(entity.getStartTime(), durationMinutes));
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
            TeamEntity team = lookupService.findTeamByName(row.getTeam());
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
                assignment.setShiftCode(shiftDefinition.getCode());
                assignment.setSourceType("IMPORT");
                assignment.setNotes(null);
                rosterAssignmentMapper.insert(assignment);
            }
        }

        batch.setStatus("APPLIED");
        batch.setAppliedTime(LocalDateTime.now());
        batch.setOperatorName(authContextService.currentActor(operator));
        importBatchMapper.updateById(batch);
        markBatchIssuesResolved(batch.getId());

        return new WorkspaceImportApplyResponse(batch.getId(), batch.getRosterYear(), batch.getRosterMonth(), batch.getStatus(), records.size());
    }

    private void cleanupPreviousPreviewBatches(YearMonth targetMonth, Long currentBatchId) {
        List<Long> staleBatchIds = importBatchMapper.selectList(Wrappers.<ImportBatchEntity>lambdaQuery()
                .eq(ImportBatchEntity::getRosterYear, targetMonth.getYear())
                .eq(ImportBatchEntity::getRosterMonth, targetMonth.getMonthValue())
                .ne(ImportBatchEntity::getStatus, "APPLIED")
                .ne(ImportBatchEntity::getId, currentBatchId))
            .stream()
            .map(ImportBatchEntity::getId)
            .toList();

        if (staleBatchIds.isEmpty()) {
            return;
        }

        importIssueMapper.delete(Wrappers.<ImportIssueEntity>lambdaQuery()
            .in(ImportIssueEntity::getBatchId, staleBatchIds));
        importRecordMapper.delete(Wrappers.<ImportRecordEntity>lambdaQuery()
            .in(ImportRecordEntity::getBatchId, staleBatchIds));
        importBatchMapper.delete(Wrappers.<ImportBatchEntity>lambdaQuery()
            .in(ImportBatchEntity::getId, staleBatchIds));
    }

    private void markBatchIssuesResolved(Long batchId) {
        if (batchId == null) {
            return;
        }

        List<ImportIssueEntity> batchIssues = importIssueMapper.selectList(Wrappers.<ImportIssueEntity>lambdaQuery()
            .eq(ImportIssueEntity::getBatchId, batchId)
            .eq(ImportIssueEntity::getResolved, false));

        for (ImportIssueEntity issue : batchIssues) {
            issue.setResolved(true);
            importIssueMapper.updateById(issue);
        }
    }

    public ResponseEntity<byte[]> exportRoster(Integer year, Integer month) {
        YearMonth targetMonth = resolveMonth(year, month);
        Map<Long, TeamEntity> teamMap = lookupService.teamMap();
        List<Long> readableTeamIds = authContextService.readableTeamIds();
        List<ShiftDefinitionEntity> shiftDefinitions = shiftDefinitionMapper.selectList(Wrappers.<ShiftDefinitionEntity>lambdaQuery()
            .orderByAsc(ShiftDefinitionEntity::getTeamId)
            .orderByAsc(ShiftDefinitionEntity::getCode))
            .stream()
            .filter(definition -> shiftDefinitionTeamRelMapper.selectList(Wrappers.<ShiftDefinitionTeamRelEntity>lambdaQuery()
                    .eq(ShiftDefinitionTeamRelEntity::getShiftDefinitionId, definition.getId()))
                .stream()
                .map(ShiftDefinitionTeamRelEntity::getTeamId)
                .anyMatch(readableTeamIds::contains))
            .toList();
        List<RosterAssignmentEntity> assignments = rosterAssignmentMapper.selectList(Wrappers.<RosterAssignmentEntity>lambdaQuery()
            .between(RosterAssignmentEntity::getAssignmentDate, targetMonth.atDay(1), targetMonth.atEndOfMonth())
            .in(RosterAssignmentEntity::getTeamId, readableTeamIds)
            .orderByAsc(RosterAssignmentEntity::getStaffId)
            .orderByAsc(RosterAssignmentEntity::getAssignmentDate));
        Map<Long, Long> assignmentCountByStaffId = assignments.stream().collect(Collectors.groupingBy(
            RosterAssignmentEntity::getStaffId,
            Collectors.counting()
        ));
        Map<String, String> schedule = new HashMap<>();
        Map<Long, ShiftDefinitionEntity> shiftDefinitionById = shiftDefinitions.stream()
            .collect(Collectors.toMap(ShiftDefinitionEntity::getId, definition -> definition, (left, right) -> left, LinkedHashMap::new));
        for (RosterAssignmentEntity assignment : assignments) {
            ShiftDefinitionEntity definition = shiftDefinitionById.get(assignment.getShiftDefinitionId());
            schedule.put(
                assignment.getStaffId() + "|" + assignment.getAssignmentDate().getDayOfMonth(),
                definition == null || definition.getCode() == null || definition.getCode().isBlank()
                    ? assignment.getShiftCode()
                    : definition.getCode()
            );
        }
        List<StaffEntity> staffList = dedupeStaffForExport(
            staffMapper.selectList(Wrappers.<StaffEntity>lambdaQuery()
                .in(StaffEntity::getTeamId, readableTeamIds)),
            teamMap,
            assignmentCountByStaffId
        );

        try (Workbook workbook = createExportWorkbook(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
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

    private Workbook createExportWorkbook() {
        Workbook workbook = new XSSFWorkbook();
        createHeaderRow(workbook.createSheet("Shift Definitions"), List.of(
            "team", "code", "meaning", "start_time", "end_time", "timezone", "show_on_roster_page", "remark"
        ));

        List<String> staffHeaders = new ArrayList<>(List.of("name", "staff_id", "team", "region", "contact", "notes"));
        for (int day = 1; day <= 31; day++) {
            staffHeaders.add(String.valueOf(day));
        }
        createHeaderRow(workbook.createSheet("Staff Shifts"), staffHeaders);
        createHeaderRow(workbook.createSheet("Color Definitions"), List.of("code", "color_name", "rgb", "hex"));
        return workbook;
    }

    private Set<Long> collectImportTeamIds(List<ImportRecordEntity> records) {
        Set<Long> teamIds = new HashSet<>();
        for (ImportRecordEntity record : records) {
            if ("SHIFT_DEFINITION".equals(record.getRecordType())) {
                @SuppressWarnings("unchecked")
                Map<String, Object> payload = readValue(record.getPayloadJson(), Map.class);
                @SuppressWarnings("unchecked")
                List<String> teamNames = (List<String>) payload.get("teams");
                resolveTeams(teamNames, teamMapper.selectList(Wrappers.<TeamEntity>lambdaQuery()))
                    .stream()
                    .map(TeamEntity::getId)
                    .forEach(teamIds::add);
            }
            if ("STAFF_SHIFT".equals(record.getRecordType())) {
                StaffShiftRow row = readValue(record.getPayloadJson(), StaffShiftRow.class);
                TeamEntity team = lookupService.findTeamByName(row.getTeam());
                if (team != null) {
                    teamIds.add(team.getId());
                }
            }
        }
        return teamIds;
    }

    private void createHeaderRow(Sheet sheet, List<String> headers) {
        Row headerRow = sheet.createRow(0);
        for (int columnIndex = 0; columnIndex < headers.size(); columnIndex++) {
            headerRow.createCell(columnIndex).setCellValue(headers.get(columnIndex));
            sheet.setColumnWidth(columnIndex, 18 * 256);
        }
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
                formatTime(shiftTimeSupport.deriveEndTime(shiftDefinition.getStartTime(), shiftTimeSupport.resolveDurationMinutes(shiftDefinition))),
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

    private List<StaffEntity> dedupeStaffForExport(List<StaffEntity> staffEntities, Map<Long, TeamEntity> teamMap, Map<Long, Long> assignmentCountByStaffId) {
        List<StaffEntity> sortedStaff = new ArrayList<>(staffEntities);
        sortedStaff.sort(Comparator
            .comparing((StaffEntity staff) -> {
                TeamEntity team = staff.getTeamId() == null ? null : teamMap.get(staff.getTeamId());
                return team == null || team.getDisplayOrder() == null ? Integer.MAX_VALUE : team.getDisplayOrder();
            })
            .thenComparing((StaffEntity staff) -> assignmentCountByStaffId.getOrDefault(staff.getId(), 0L), Comparator.reverseOrder())
            .thenComparing(staff -> staff.getStaffCode() == null ? "" : staff.getStaffCode())
            .thenComparing(staff -> staff.getName() == null ? "" : staff.getName())
            .thenComparing(staff -> staff.getId() == null ? Long.MAX_VALUE : staff.getId()));

        Map<String, StaffEntity> uniqueStaffByCode = new LinkedHashMap<>();
        for (StaffEntity staff : sortedStaff) {
            uniqueStaffByCode.putIfAbsent(exportStaffKey(staff), staff);
        }
        return new ArrayList<>(uniqueStaffByCode.values());
    }

    private String exportStaffKey(StaffEntity staff) {
        if (staff == null) {
            return "";
        }

        if (staff.getStaffCode() == null || staff.getStaffCode().isBlank()) {
            return "__id__" + (staff.getId() == null ? "" : staff.getId());
        }
        return staff.getStaffCode().trim().toLowerCase(Locale.ROOT);
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

        Row styleRow = sheet.getLastRowNum() >= 1 ? sheet.getRow(1) : null;
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

    private ParsedImportWorkbook readImportWorkbook(Path tempFile) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(tempFile.toFile())) {
            DataFormatter formatter = new DataFormatter(Locale.ENGLISH);
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            return new ParsedImportWorkbook(
                readShiftDefinitionRows(workbook, formatter, evaluator),
                readStaffShiftRows(workbook, formatter, evaluator),
                readColorDefinitionRows(workbook, formatter, evaluator)
            );
        }
    }

    private List<ShiftDefinitionRow> readShiftDefinitionRows(Workbook workbook, DataFormatter formatter, FormulaEvaluator evaluator) {
        Sheet sheet = getSheet(workbook, "Shift Definitions", 0);
        if (sheet == null) {
            return List.of();
        }

        List<ShiftDefinitionRow> rows = new ArrayList<>();
        for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (isBlankRow(row, 8, formatter, evaluator)) {
                continue;
            }

            ShiftDefinitionRow item = new ShiftDefinitionRow();
            item.setTeam(readCell(row, 0, formatter, evaluator));
            item.setCode(readCell(row, 1, formatter, evaluator));
            item.setMeaning(readCell(row, 2, formatter, evaluator));
            item.setStartTime(readCell(row, 3, formatter, evaluator));
            item.setEndTime(readCell(row, 4, formatter, evaluator));
            item.setTimezone(readCell(row, 5, formatter, evaluator));
            item.setShowOnRosterPage(readCell(row, 6, formatter, evaluator));
            item.setRemark(readCell(row, 7, formatter, evaluator));
            rows.add(item);
        }
        return rows;
    }

    private List<StaffShiftRow> readStaffShiftRows(Workbook workbook, DataFormatter formatter, FormulaEvaluator evaluator) {
        Sheet sheet = getSheet(workbook, "Staff Shifts", 1);
        if (sheet == null) {
            return List.of();
        }

        List<StaffShiftRow> rows = new ArrayList<>();
        for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (isBlankRow(row, 37, formatter, evaluator)) {
                continue;
            }

            StaffShiftRow item = new StaffShiftRow();
            item.setName(readCell(row, 0, formatter, evaluator));
            item.setStaffId(readCell(row, 1, formatter, evaluator));
            item.setTeam(readCell(row, 2, formatter, evaluator));
            item.setRegion(readCell(row, 3, formatter, evaluator));
            item.setContact(readCell(row, 4, formatter, evaluator));
            item.setNotes(readCell(row, 5, formatter, evaluator));
            for (int day = 1; day <= 31; day++) {
                setShiftCode(item, day, readCell(row, day + 5, formatter, evaluator));
            }
            rows.add(item);
        }
        return rows;
    }

    private List<ColorDefinitionRow> readColorDefinitionRows(Workbook workbook, DataFormatter formatter, FormulaEvaluator evaluator) {
        Sheet sheet = getSheet(workbook, "Color Definitions", 2);
        if (sheet == null) {
            return List.of();
        }

        List<ColorDefinitionRow> rows = new ArrayList<>();
        for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (isBlankRow(row, 4, formatter, evaluator)) {
                continue;
            }

            ColorDefinitionRow item = new ColorDefinitionRow();
            item.setCode(readCell(row, 0, formatter, evaluator));
            item.setColorName(readCell(row, 1, formatter, evaluator));
            item.setRgb(readCell(row, 2, formatter, evaluator));
            item.setHex(readCell(row, 3, formatter, evaluator));
            rows.add(item);
        }
        return rows;
    }

    private Sheet getSheet(Workbook workbook, String name, int fallbackIndex) {
        Sheet sheet = workbook.getSheet(name);
        if (sheet != null) {
            return sheet;
        }
        return fallbackIndex < workbook.getNumberOfSheets() ? workbook.getSheetAt(fallbackIndex) : null;
    }

    private boolean isBlankRow(Row row, int columnCount, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (row == null) {
            return true;
        }

        for (int columnIndex = 0; columnIndex < columnCount; columnIndex++) {
            if (readCell(row, columnIndex, formatter, evaluator) != null) {
                return false;
            }
        }
        return true;
    }

    private String readCell(Row row, int columnIndex, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (row == null) {
            return null;
        }

        Cell cell = row.getCell(columnIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) {
            return null;
        }

        String value = formatter.formatCellValue(cell, evaluator);
        if (value == null) {
            return null;
        }

        String trimmedValue = value.trim();
        return trimmedValue.isEmpty() ? null : trimmedValue;
    }

    private void setShiftCode(StaffShiftRow row, int day, String value) {
        switch (day) {
            case 1 -> row.setDay1(value);
            case 2 -> row.setDay2(value);
            case 3 -> row.setDay3(value);
            case 4 -> row.setDay4(value);
            case 5 -> row.setDay5(value);
            case 6 -> row.setDay6(value);
            case 7 -> row.setDay7(value);
            case 8 -> row.setDay8(value);
            case 9 -> row.setDay9(value);
            case 10 -> row.setDay10(value);
            case 11 -> row.setDay11(value);
            case 12 -> row.setDay12(value);
            case 13 -> row.setDay13(value);
            case 14 -> row.setDay14(value);
            case 15 -> row.setDay15(value);
            case 16 -> row.setDay16(value);
            case 17 -> row.setDay17(value);
            case 18 -> row.setDay18(value);
            case 19 -> row.setDay19(value);
            case 20 -> row.setDay20(value);
            case 21 -> row.setDay21(value);
            case 22 -> row.setDay22(value);
            case 23 -> row.setDay23(value);
            case 24 -> row.setDay24(value);
            case 25 -> row.setDay25(value);
            case 26 -> row.setDay26(value);
            case 27 -> row.setDay27(value);
            case 28 -> row.setDay28(value);
            case 29 -> row.setDay29(value);
            case 30 -> row.setDay30(value);
            case 31 -> row.setDay31(value);
            default -> throw new IllegalArgumentException("Unsupported day: " + day);
        }
    }

    private LocalTime parseTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String[] parts = value.split(":");
        try {
            if (parts.length < 2) {
                return null;
            }
            int hours = Integer.parseInt(parts[0]);
            int minutes = Integer.parseInt(parts[1]);
            if (hours == 24 && minutes == 0) {
                return LocalTime.MIDNIGHT;
            }
            return LocalTime.of(hours, minutes, parts.length > 2 ? Integer.parseInt(parts[2]) : 0);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private Integer parseDurationMinutes(String startValue, String endValue) {
        LocalTime startTime = parseTime(startValue);
        if (startTime == null) {
            return null;
        }
        if (endValue == null || endValue.isBlank()) {
            return null;
        }
        String normalizedEnd = endValue.trim();
        if ("24:00".equals(normalizedEnd) || "24:00:00".equals(normalizedEnd)) {
            int startMinutes = startTime.getHour() * 60 + startTime.getMinute();
            int durationMinutes = 1440 - startMinutes;
            return durationMinutes == 0 ? 1440 : durationMinutes;
        }
        LocalTime endTime = parseTime(endValue);
        if (endTime == null) {
            return null;
        }
        return shiftTimeSupport.durationFromTimes(startTime, endTime);
    }

    private Integer readDurationMinutes(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
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

    private record ParsedImportWorkbook(
        List<ShiftDefinitionRow> shiftDefinitionRows,
        List<StaffShiftRow> staffShiftRows,
        List<ColorDefinitionRow> colorRows
    ) {
    }

}
