package com.support.server.supportrosterserver.service.workspace;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.fesod.sheet.FesodSheet;
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
import com.support.server.supportrosterserver.entity.workspace.StaffEntity;
import com.support.server.supportrosterserver.entity.workspace.TeamEntity;
import com.support.server.supportrosterserver.exception.BadRequestException;
import com.support.server.supportrosterserver.mapper.ImportBatchMapper;
import com.support.server.supportrosterserver.mapper.ImportIssueMapper;
import com.support.server.supportrosterserver.mapper.ImportRecordMapper;
import com.support.server.supportrosterserver.mapper.RosterAssignmentMapper;
import com.support.server.supportrosterserver.mapper.ShiftDefinitionMapper;
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

    private static final byte[] UTF_8_BOM = new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MMM dd", Locale.ENGLISH);
    private static final Set<String> PRIMARY_CODES = Set.of("OC", "DS", "NS", "A", "B", "D");

    private final ImportBatchMapper importBatchMapper;
    private final ImportRecordMapper importRecordMapper;
    private final ImportIssueMapper importIssueMapper;
    private final ShiftDefinitionMapper shiftDefinitionMapper;
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
                TeamEntity team = teamsByName.get(row.getTeam().trim().toLowerCase(Locale.ROOT));
                if (row.getCode() == null || row.getCode().isBlank()) {
                    valid = false;
                    issues.add(buildIssue(batch.getId(), "medium", "Invalid Shift Code", "Shift definition code is missing.", row.getTeam(), null, null));
                }
                if (parseTime(row.getStartTime()) == null || parseTime(row.getEndTime()) == null) {
                    valid = false;
                    issues.add(buildIssue(batch.getId(), "medium", "Invalid Shift Definition", "Shift definition time range is invalid for team '" + row.getTeam() + "'.", row.getTeam(), null, null));
                }
                if (team == null) {
                    valid = false;
                    issues.add(buildIssue(batch.getId(), "medium", "Missing Team", "Team '" + row.getTeam() + "' does not exist.", row.getTeam(), null, null));
                }
                if (team != null) {
                    validShiftKeys.add(team.getId() + "|" + row.getCode());
                }
                Map<String, Object> payload = new HashMap<>();
                payload.put("team", row.getTeam());
                payload.put("code", row.getCode());
                payload.put("meaning", row.getMeaning());
                payload.put("startTime", row.getStartTime());
                payload.put("endTime", row.getEndTime());
                payload.put("timezone", row.getTimezone());
                payload.put("showOnRosterPage", row.getShowOnRosterPage());
                payload.put("remark", row.getRemark());
                payload.put("colorHex", colorHexByCode.get(row.getCode()));
                records.add(buildRecord(batch.getId(), "Shift Definitions", rowIndex++, "SHIFT_DEFINITION", payload, valid));
            }

            for (ShiftDefinitionEntity entity : shiftDefinitionMapper.selectList(Wrappers.lambdaQuery())) {
                if (entity.getTeamId() != null) {
                    validShiftKeys.add(entity.getTeamId() + "|" + entity.getCode());
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
                String teamName = String.valueOf(payload.get("team"));
                TeamEntity team = teamMapper.selectOne(Wrappers.<TeamEntity>lambdaQuery()
                    .eq(TeamEntity::getName, teamName)
                    .last("limit 1"));
                if (team == null) {
                    throw new BadRequestException("Team '" + teamName + "' does not exist.");
                }

                ShiftDefinitionEntity entity = shiftDefinitionMapper.selectOne(Wrappers.<ShiftDefinitionEntity>lambdaQuery()
                    .eq(ShiftDefinitionEntity::getTeamId, team.getId())
                    .eq(ShiftDefinitionEntity::getCode, String.valueOf(payload.get("code")))
                    .last("limit 1"));
                if (entity == null) {
                    entity = new ShiftDefinitionEntity();
                }
                entity.setTeamId(team.getId());
                entity.setRoleGroupId(null);
                entity.setCode(String.valueOf(payload.get("code")));
                entity.setMeaning((String) payload.get("meaning"));
                entity.setStartTime(parseTime((String) payload.get("startTime")));
                entity.setEndTime(parseTime((String) payload.get("endTime")));
                entity.setTimezone((String) payload.get("timezone"));
                entity.setVisible("Y".equalsIgnoreCase(String.valueOf(payload.get("showOnRosterPage"))));
                entity.setPrimaryShift(PRIMARY_CODES.contains(entity.getCode()));
                entity.setRemark((String) payload.get("remark"));
                entity.setColorHex((String) payload.get("colorHex"));
                if (entity.getId() == null) {
                    shiftDefinitionMapper.insert(entity);
                } else {
                    shiftDefinitionMapper.updateById(entity);
                }
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
            staff.setTimezone(lookupService.inferTimezone(row.getRegion(), row.getTeam()));
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
                ShiftDefinitionEntity shiftDefinition = shiftDefinitionMapper.selectOne(Wrappers.<ShiftDefinitionEntity>lambdaQuery()
                    .eq(ShiftDefinitionEntity::getTeamId, team.getId())
                    .eq(ShiftDefinitionEntity::getCode, shiftCode)
                    .last("limit 1"));
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
        List<StaffEntity> staffList = staffMapper.selectList(Wrappers.<StaffEntity>lambdaQuery().orderByAsc(StaffEntity::getStaffCode));
        Map<Long, TeamEntity> teamMap = lookupService.teamMap();
        List<RosterAssignmentEntity> assignments = rosterAssignmentMapper.selectList(Wrappers.<RosterAssignmentEntity>lambdaQuery()
            .between(RosterAssignmentEntity::getAssignmentDate, targetMonth.atDay(1), targetMonth.atEndOfMonth())
            .orderByAsc(RosterAssignmentEntity::getStaffId)
            .orderByAsc(RosterAssignmentEntity::getAssignmentDate));
        Map<String, String> schedule = new HashMap<>();
        for (RosterAssignmentEntity assignment : assignments) {
            schedule.put(assignment.getStaffId() + "|" + assignment.getAssignmentDate().getDayOfMonth(), assignment.getShiftCode());
        }

        StringBuilder csv = new StringBuilder();
        csv.append("name,staff_id,team,region,contact,notes");
        for (int day = 1; day <= targetMonth.lengthOfMonth(); day++) {
            csv.append(',').append(day);
        }
        csv.append('\n');

        for (StaffEntity staff : staffList) {
            TeamEntity team = staff.getTeamId() == null ? null : teamMap.get(staff.getTeamId());
            csv.append(safeCsv(staff.getName())).append(',')
                .append(safeCsv(staff.getStaffCode())).append(',')
                .append(safeCsv(team == null ? "" : team.getName())).append(',')
                .append(safeCsv(staff.getRegion())).append(',')
                .append(safeCsv(staff.getPhone())).append(',')
                .append(safeCsv(staff.getNotes()));
            for (int day = 1; day <= targetMonth.lengthOfMonth(); day++) {
                csv.append(',').append(safeCsv(schedule.get(staff.getId() + "|" + day)));
            }
            csv.append('\n');
        }

        byte[] csvBytes = csv.toString().getBytes(StandardCharsets.UTF_8);
        byte[] body = new byte[UTF_8_BOM.length + csvBytes.length];
        System.arraycopy(UTF_8_BOM, 0, body, 0, UTF_8_BOM.length);
        System.arraycopy(csvBytes, 0, body, UTF_8_BOM.length, csvBytes.length);
        String fileName = "workspace-roster-" + targetMonth.getYear() + "-" + String.format("%02d", targetMonth.getMonthValue()) + ".csv";
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + fileName)
            .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
            .body(body);
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

    private YearMonth resolveMonth(Integer year, Integer month) {
        YearMonth now = YearMonth.now();
        return YearMonth.of(year == null ? now.getYear() : year, month == null ? now.getMonthValue() : month);
    }

    private String safeCsv(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        return '"' + escaped + '"';
    }
}