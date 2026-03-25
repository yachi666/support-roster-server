package com.support.server.supportrosterserver.service.workspace;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceImportPreviewGroupDto;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceImportPreviewPersonDto;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceImportPreviewResponse;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceImportPreviewSaveRequest;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceImportPreviewSaveRowRequest;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceImportSaveResponse;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceRosterShiftDetailDto;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceValidationIssueDto;
import com.support.server.supportrosterserver.entity.workspace.RosterAssignmentEntity;
import com.support.server.supportrosterserver.entity.workspace.ShiftDefinitionEntity;
import com.support.server.supportrosterserver.entity.workspace.ShiftDefinitionTeamRelEntity;
import com.support.server.supportrosterserver.entity.workspace.StaffEntity;
import com.support.server.supportrosterserver.entity.workspace.TeamEntity;
import com.support.server.supportrosterserver.exception.BadRequestException;
import com.support.server.supportrosterserver.mapper.RosterAssignmentMapper;
import com.support.server.supportrosterserver.mapper.ShiftDefinitionMapper;
import com.support.server.supportrosterserver.mapper.ShiftDefinitionTeamRelMapper;
import com.support.server.supportrosterserver.mapper.StaffMapper;
import com.support.server.supportrosterserver.mapper.TeamMapper;
import com.support.server.supportrosterserver.service.auth.AuthContextService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WorkspaceImportService {

    private static final String TEMPLATE_SHEET_NAME = "Monthly Roster";
    private static final String DEFAULT_TEAM_COLOR = "#CBD5E1";
    private static final DateTimeFormatter ISSUE_DATE_FORMATTER = DateTimeFormatter.ofPattern("MMM dd", Locale.ENGLISH);

    private final ShiftDefinitionMapper shiftDefinitionMapper;
    private final ShiftDefinitionTeamRelMapper shiftDefinitionTeamRelMapper;
    private final StaffMapper staffMapper;
    private final RosterAssignmentMapper rosterAssignmentMapper;
    private final TeamMapper teamMapper;
    private final WorkspaceLookupService lookupService;
    private final AuthContextService authContextService;
    private final WorkspaceShiftTimeSupport shiftTimeSupport;

    public WorkspaceImportPreviewResponse previewImport(MultipartFile file, Integer year, Integer month, String operator) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Import file is required.");
        }

        YearMonth targetMonth = resolveMonth(year, month);
        ImportContext importContext = buildImportContext(targetMonth);
        List<ImportedRosterRow> rows = readImportRows(file, targetMonth);
        if (rows.isEmpty()) {
            throw new BadRequestException("Import file does not contain any roster rows.");
        }

        AtomicLong nextPreviewTeamId = new AtomicLong(-1L);
        AtomicLong nextPreviewStaffId = new AtomicLong(-1L);
        AtomicLong nextIssueId = new AtomicLong(-1L);
        Map<String, PreviewTeamState> previewTeamsByName = new LinkedHashMap<>();
        Map<String, PreviewStaffState> previewStaffByCode = new LinkedHashMap<>();
        List<WorkspaceValidationIssueDto> issues = new ArrayList<>();
        Set<String> issueRowKeys = new LinkedHashSet<>();
        Set<String> seenImportedStaffCodes = new LinkedHashSet<>();
        Set<String> newStaffCodes = new LinkedHashSet<>();
        Set<String> newTeamNames = new LinkedHashSet<>();

        for (ImportedRosterRow row : rows) {
            String normalizedStaffCode = normalizeKey(row.staffCode());
            String normalizedTeamName = normalizeKey(row.teamName());
            String rowKey = normalizedStaffCode + "|" + normalizedTeamName;

            if (normalizedStaffCode.isBlank()) {
                issues.add(buildIssue(nextIssueId.getAndDecrement(), null, "high", "Missing Staff ID", "staff_id is required for every imported row.", null, null));
                issueRowKeys.add(rowKey);
                continue;
            }
            if (normalizedTeamName.isBlank()) {
                issues.add(buildIssue(nextIssueId.getAndDecrement(), null, "high", "Missing Team", "team is required for every imported row.", null, null));
                issueRowKeys.add(rowKey);
                continue;
            }
            if (importContext.blockedTeamNames().contains(normalizedTeamName)) {
                issues.add(buildIssue(nextIssueId.getAndDecrement(), null, "high", "Team Out Of Scope", "Team '" + row.teamName() + "' is outside your accessible scope.", row.teamName(), null));
                issueRowKeys.add(rowKey);
                continue;
            }
            if (importContext.blockedStaffCodes().contains(normalizedStaffCode)) {
                issues.add(buildIssue(nextIssueId.getAndDecrement(), null, "high", "Staff Out Of Scope", "Staff ID '" + row.staffCode() + "' is outside your accessible scope.", row.teamName(), null));
                issueRowKeys.add(rowKey);
                continue;
            }
            if (!seenImportedStaffCodes.add(normalizedStaffCode)) {
                issues.add(buildIssue(nextIssueId.getAndDecrement(), null, "high", "Duplicate Staff ID", "Staff ID '" + row.staffCode() + "' appears more than once in the import file.", row.teamName(), null));
                issueRowKeys.add(rowKey);
                continue;
            }

            PreviewTeamState teamState = previewTeamsByName.computeIfAbsent(normalizedTeamName, ignored -> {
                TeamEntity existingTeam = importContext.teamsByName().get(normalizedTeamName);
                if (existingTeam != null) {
                    return new PreviewTeamState(existingTeam.getId(), existingTeam.getName(), existingTeam.getColor(), false);
                }
                newTeamNames.add(row.teamName());
                return new PreviewTeamState(nextPreviewTeamId.getAndDecrement(), row.teamName(), DEFAULT_TEAM_COLOR, true);
            });

            if (teamState.newTeam()) {
                issues.add(buildIssue(nextIssueId.getAndDecrement(), teamState.teamId(), "low", "New Team", "Team '" + teamState.teamName() + "' will be created when you save this import.", teamState.teamName(), null));
            }

            PreviewStaffState staffState = previewStaffByCode.computeIfAbsent(normalizedStaffCode, ignored -> {
                StaffEntity existingStaff = importContext.staffByCode().get(normalizedStaffCode);
                if (existingStaff != null) {
                    return new PreviewStaffState(existingStaff.getId(), existingStaff.getId(), existingStaff.getStaffCode(),
                        coalesce(existingStaff.getName(), existingStaff.getStaffCode()), existingStaff.getRoleName(), false);
                }
                newStaffCodes.add(row.staffCode());
                return new PreviewStaffState(nextPreviewStaffId.getAndDecrement(), null, row.staffCode(), row.staffCode(), "New staff", true);
            });

            if (staffState.newStaff()) {
                issues.add(buildIssue(nextIssueId.getAndDecrement(), teamState.teamId(), "low", "New Staff", "Staff '" + staffState.staffCode() + "' will be created the first time this import is saved.", teamState.teamName(), null));
            }

            Map<Integer, String> schedule = new LinkedHashMap<>();
            Set<String> allowedShiftCodes = new LinkedHashSet<>(importContext.shiftCodeOptionsByTeam().getOrDefault(teamState.teamId(), List.of()));
            for (int day = 1; day <= targetMonth.lengthOfMonth(); day++) {
                String shiftCode = safeCellValue(row.scheduleByDay().get(day));
                if (shiftCode.isBlank()) {
                    schedule.put(day, "");
                    continue;
                }
                if (!allowedShiftCodes.contains(shiftCode)) {
                    issues.add(buildIssue(
                        nextIssueId.getAndDecrement(),
                        teamState.teamId(),
                        "medium",
                        "Invalid Shift Code",
                        "Shift code '" + shiftCode + "' is not valid for team '" + teamState.teamName() + "' on " + targetMonth.atDay(day).format(ISSUE_DATE_FORMATTER) + ". The preview keeps that day blank.",
                        teamState.teamName(),
                        targetMonth.atDay(day)
                    ));
                    issueRowKeys.add(rowKey);
                    schedule.put(day, "");
                    continue;
                }
                schedule.put(day, shiftCode);
            }

            staffState.schedule().putAll(schedule);
            staffState.teamId = teamState.teamId();
            staffState.teamName = teamState.teamName();
        }

        List<WorkspaceImportPreviewGroupDto> groups = previewTeamsByName.values().stream()
            .sorted(Comparator.comparing(PreviewTeamState::teamName, String.CASE_INSENSITIVE_ORDER))
            .map(teamState -> new WorkspaceImportPreviewGroupDto(
                teamState.teamId(),
                teamState.teamName(),
                coalesce(teamState.color(), DEFAULT_TEAM_COLOR),
                teamState.newTeam(),
                previewStaffByCode.values().stream()
                    .filter(staff -> Objects.equals(staff.teamId, teamState.teamId()))
                    .sorted(Comparator.comparing(PreviewStaffState::staffCode, String.CASE_INSENSITIVE_ORDER))
                    .map(staff -> new WorkspaceImportPreviewPersonDto(
                        staff.previewStaffId(),
                        staff.existingStaffId(),
                        staff.staffCode(),
                        staff.staffName(),
                        null,
                        staff.roleName(),
                        staff.teamId,
                        staff.teamName,
                        new LinkedHashMap<>(staff.schedule()),
                        staff.newStaff()
                    ))
                    .toList()
            ))
            .toList();

        int invalidRecords = issueRowKeys.size();
        int totalRecords = rows.size();
        int validRecords = Math.max(totalRecords - invalidRecords, 0);
        String validationWarning = issues.stream()
            .filter(issue -> "high".equals(issue.getSeverity()) || "medium".equals(issue.getSeverity()))
            .map(WorkspaceValidationIssueDto::getDescription)
            .findFirst()
            .orElse("");

        Map<Long, List<String>> previewShiftCodeOptionsByTeam = new LinkedHashMap<>(importContext.shiftCodeOptionsByTeam());
        for (PreviewTeamState teamState : previewTeamsByName.values()) {
            previewShiftCodeOptionsByTeam.putIfAbsent(teamState.teamId(), List.of());
        }

        return new WorkspaceImportPreviewResponse(
            targetMonth.getYear(),
            targetMonth.getMonthValue(),
            totalRecords,
            validRecords,
            invalidRecords,
            groups,
            importContext.shiftCodeOptions(),
            previewShiftCodeOptionsByTeam,
            importContext.shiftCodeColorMap(),
            importContext.shiftDetailsByTeam(),
            issues,
            new ArrayList<>(newStaffCodes),
            new ArrayList<>(newTeamNames),
            validationWarning
        );
    }

    @Transactional
    public WorkspaceImportSaveResponse savePreview(WorkspaceImportPreviewSaveRequest request) {
        YearMonth targetMonth = resolveMonth(request.getYear(), request.getMonth());
        if (request.getRows() == null || request.getRows().isEmpty()) {
            throw new BadRequestException("Imported preview rows are required.");
        }

        List<TeamEntity> existingTeams = teamMapper.selectList(Wrappers.<TeamEntity>lambdaQuery()
            .orderByAsc(TeamEntity::getDisplayOrder)
            .orderByAsc(TeamEntity::getName));
        Map<String, TeamEntity> teamsByName = existingTeams.stream()
            .collect(Collectors.toMap(team -> normalizeKey(team.getName()), team -> team, (left, right) -> left, LinkedHashMap::new));
        Map<String, StaffEntity> staffByCode = staffMapper.selectList(Wrappers.<StaffEntity>lambdaQuery())
            .stream()
            .collect(Collectors.toMap(staff -> normalizeKey(staff.getStaffCode()), staff -> staff, (left, right) -> left, LinkedHashMap::new));

        int nextDisplayOrder = existingTeams.stream()
            .map(TeamEntity::getDisplayOrder)
            .filter(Objects::nonNull)
            .max(Integer::compareTo)
            .orElse(0);
        int createdTeamCount = 0;
        int createdStaffCount = 0;
        Map<String, TeamEntity> resolvedTeamsByName = new LinkedHashMap<>();
        Map<String, StaffEntity> resolvedStaffByCode = new LinkedHashMap<>();
        Set<String> seenImportedStaffCodes = new LinkedHashSet<>();

        for (WorkspaceImportPreviewSaveRowRequest row : request.getRows()) {
            String normalizedTeamName = normalizeKey(row.getTeamName());
            if (normalizedTeamName.isBlank()) {
                throw new BadRequestException("team is required for every imported row.");
            }
            TeamEntity team = resolvedTeamsByName.get(normalizedTeamName);
            if (team == null) {
                team = teamsByName.get(normalizedTeamName);
                if (team == null) {
                    team = new TeamEntity();
                    team.setName(row.getTeamName().trim());
                    team.setColor(DEFAULT_TEAM_COLOR);
                    team.setDisplayOrder(++nextDisplayOrder);
                    team.setVisible(true);
                    team.setDescription(null);
                    teamMapper.insert(team);
                    teamsByName.put(normalizedTeamName, team);
                    createdTeamCount++;
                }
                resolvedTeamsByName.put(normalizedTeamName, team);
            }

            authContextService.requireWritableTeam(team.getId());

            String normalizedStaffCode = normalizeKey(row.getStaffCode());
            if (normalizedStaffCode.isBlank()) {
                throw new BadRequestException("staff_id is required for every imported row.");
            }
            if (!seenImportedStaffCodes.add(normalizedStaffCode)) {
                throw new BadRequestException("Duplicate staff_id '" + row.getStaffCode() + "' is not allowed in import preview save.");
            }
            StaffEntity staff = resolvedStaffByCode.get(normalizedStaffCode);
            if (staff == null) {
                staff = staffByCode.get(normalizedStaffCode);
                if (staff == null) {
                    staff = new StaffEntity();
                    staff.setStaffCode(row.getStaffCode().trim());
                    staff.setName(row.getStaffCode().trim());
                    staff.setEmail(null);
                    staff.setPhone(null);
                    staff.setSlack(null);
                    staff.setRegion(null);
                    String inferredTimezone = lookupService.inferTimezone(null, team.getName());
                    staff.setTimezone(lookupService.normalizeWorkspaceTimezone(inferredTimezone));
                    staff.setRoleName("Imported staff");
                    staff.setTeamId(team.getId());
                    staff.setRoleGroupId(null);
                    staff.setStatus("Active");
                    staff.setAvatar(null);
                    staff.setNotes(null);
                    staffMapper.insert(staff);
                    staffByCode.put(normalizedStaffCode, staff);
                    createdStaffCount++;
                } else {
                    if (staff.getTeamId() != null) {
                        authContextService.requireWritableTeam(staff.getTeamId());
                    }
                    if (!Objects.equals(staff.getTeamId(), team.getId())) {
                        staff.setTeamId(team.getId());
                        staffMapper.updateById(staff);
                    }
                }
                resolvedStaffByCode.put(normalizedStaffCode, staff);
            }
        }

        List<Long> affectedStaffIds = resolvedStaffByCode.values().stream()
            .map(StaffEntity::getId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        if (!affectedStaffIds.isEmpty()) {
            rosterAssignmentMapper.delete(Wrappers.<RosterAssignmentEntity>lambdaQuery()
                .in(RosterAssignmentEntity::getStaffId, affectedStaffIds)
                .between(RosterAssignmentEntity::getAssignmentDate, targetMonth.atDay(1), targetMonth.atEndOfMonth()));
        }

        for (WorkspaceImportPreviewSaveRowRequest row : request.getRows()) {
            TeamEntity team = resolvedTeamsByName.get(normalizeKey(row.getTeamName()));
            StaffEntity staff = resolvedStaffByCode.get(normalizeKey(row.getStaffCode()));
            if (team == null || staff == null) {
                throw new BadRequestException("Imported preview row could not be resolved before save.");
            }

            Map<Integer, String> schedule = row.getSchedule() == null ? Map.of() : row.getSchedule();
            for (int day = 1; day <= targetMonth.lengthOfMonth(); day++) {
                String shiftCode = safeCellValue(schedule.get(day));
                if (shiftCode.isBlank()) {
                    continue;
                }
                ShiftDefinitionEntity shiftDefinition = findShiftDefinitionForTeamAndCode(team.getId(), shiftCode);
                if (shiftDefinition == null) {
                    throw new BadRequestException("Shift code '" + shiftCode + "' does not exist for team '" + team.getName() + "'.");
                }
                RosterAssignmentEntity assignment = new RosterAssignmentEntity();
                assignment.setStaffId(staff.getId());
                assignment.setAssignmentDate(targetMonth.atDay(day));
                assignment.setRoleGroupId(null);
                assignment.setTeamId(team.getId());
                assignment.setShiftDefinitionId(shiftDefinition.getId());
                assignment.setShiftCode(shiftDefinition.getCode());
                assignment.setSourceType("IMPORT");
                assignment.setNotes(null);
                rosterAssignmentMapper.insert(assignment);
            }
        }

        return new WorkspaceImportSaveResponse(
            targetMonth.getYear(),
            targetMonth.getMonthValue(),
            request.getRows().size(),
            createdStaffCount,
            createdTeamCount
        );
    }

    public ResponseEntity<byte[]> exportRoster(Integer year, Integer month) {
        YearMonth targetMonth = resolveMonth(year, month);
        List<Long> readableTeamIds = authContextService.readableTeamIds();
        if (readableTeamIds.isEmpty()) {
            return workbookResponse(createWorkbook(targetMonth, List.of()), targetMonth, "workspace-roster-");
        }

        Map<Long, TeamEntity> teamMap = lookupService.teamMap();
        List<RosterAssignmentEntity> assignments = rosterAssignmentMapper.selectList(Wrappers.<RosterAssignmentEntity>lambdaQuery()
            .between(RosterAssignmentEntity::getAssignmentDate, targetMonth.atDay(1), targetMonth.atEndOfMonth())
            .in(RosterAssignmentEntity::getTeamId, readableTeamIds)
            .orderByAsc(RosterAssignmentEntity::getTeamId)
            .orderByAsc(RosterAssignmentEntity::getStaffId)
            .orderByAsc(RosterAssignmentEntity::getAssignmentDate)).stream()
            .filter(assignment -> readableTeamIds.contains(assignment.getTeamId()))
            .toList();
        if (assignments.isEmpty()) {
            return workbookResponse(createWorkbook(targetMonth, List.of()), targetMonth, "workspace-roster-");
        }

        Set<Long> exportedStaffIds = assignments.stream().map(RosterAssignmentEntity::getStaffId).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, StaffEntity> staffById = staffMapper.selectList(Wrappers.<StaffEntity>lambdaQuery()
                .in(StaffEntity::getId, exportedStaffIds))
            .stream()
            .collect(Collectors.toMap(StaffEntity::getId, staff -> staff, (left, right) -> left, LinkedHashMap::new));
        Map<Long, ShiftDefinitionEntity> shiftDefinitionById = shiftDefinitionMapper.selectList(Wrappers.<ShiftDefinitionEntity>lambdaQuery())
            .stream()
            .collect(Collectors.toMap(ShiftDefinitionEntity::getId, definition -> definition, (left, right) -> left, LinkedHashMap::new));

        Map<Long, Map<Integer, String>> scheduleByStaffId = new LinkedHashMap<>();
        for (RosterAssignmentEntity assignment : assignments) {
            ShiftDefinitionEntity definition = shiftDefinitionById.get(assignment.getShiftDefinitionId());
            String code = definition == null || safeCellValue(definition.getCode()).isBlank()
                ? safeCellValue(assignment.getShiftCode())
                : definition.getCode();
            scheduleByStaffId.computeIfAbsent(assignment.getStaffId(), ignored -> new LinkedHashMap<>())
                .put(assignment.getAssignmentDate().getDayOfMonth(), code);
        }

        List<ImportedRosterRow> rows = exportedStaffIds.stream()
            .map(staffById::get)
            .filter(Objects::nonNull)
            .sorted(Comparator
                .comparing((StaffEntity staff) -> {
                    TeamEntity team = teamMap.get(staff.getTeamId());
                    return team == null || team.getDisplayOrder() == null ? Integer.MAX_VALUE : team.getDisplayOrder();
                })
                .thenComparing(staff -> safeCellValue(staff.getStaffCode()), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(staff -> safeCellValue(staff.getName()), String.CASE_INSENSITIVE_ORDER))
            .map(staff -> new ImportedRosterRow(
                safeCellValue(staff.getStaffCode()),
                staff.getTeamId() == null || teamMap.get(staff.getTeamId()) == null ? "" : safeCellValue(teamMap.get(staff.getTeamId()).getName()),
                scheduleByStaffId.getOrDefault(staff.getId(), Map.of())
            ))
            .toList();

        return workbookResponse(createWorkbook(targetMonth, rows), targetMonth, "workspace-roster-");
    }

    public ResponseEntity<byte[]> downloadTemplate() {
        YearMonth targetMonth = resolveMonth(null, null);
        return workbookResponse(createWorkbook(targetMonth, List.of()), targetMonth, "import-template-");
    }

    private ImportContext buildImportContext(YearMonth targetMonth) {
        List<Long> readableTeamIds = authContextService.readableTeamIds();
        Set<Long> readableTeamIdSet = new LinkedHashSet<>(readableTeamIds);
        List<TeamEntity> allTeams = lookupService.listTeams();
        List<StaffEntity> allStaff = staffMapper.selectList(Wrappers.<StaffEntity>lambdaQuery());
        Map<String, TeamEntity> teamsByName = allTeams.stream()
            .filter(team -> readableTeamIdSet.contains(team.getId()))
            .collect(Collectors.toMap(team -> normalizeKey(team.getName()), team -> team, (left, right) -> left, LinkedHashMap::new));
        Set<String> blockedTeamNames = allTeams.stream()
            .filter(team -> !readableTeamIdSet.contains(team.getId()))
            .map(TeamEntity::getName)
            .filter(Objects::nonNull)
            .map(this::normalizeKey)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, StaffEntity> staffByCode = allStaff.stream()
            .filter(staff -> staff.getTeamId() != null && readableTeamIdSet.contains(staff.getTeamId()))
            .collect(Collectors.toMap(staff -> normalizeKey(staff.getStaffCode()), staff -> staff, (left, right) -> left, LinkedHashMap::new));
        Set<String> blockedStaffCodes = allStaff.stream()
            .filter(staff -> staff.getStaffCode() != null && (staff.getTeamId() == null || !readableTeamIdSet.contains(staff.getTeamId())))
            .map(StaffEntity::getStaffCode)
            .map(this::normalizeKey)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        List<ShiftDefinitionTeamRelEntity> visibleShiftRelations = readableTeamIds.isEmpty()
            ? List.of()
            : shiftDefinitionTeamRelMapper.selectList(Wrappers.<ShiftDefinitionTeamRelEntity>lambdaQuery()
                .in(ShiftDefinitionTeamRelEntity::getTeamId, readableTeamIds)
                .orderByAsc(ShiftDefinitionTeamRelEntity::getTeamId));
        Set<Long> visibleShiftDefinitionIds = visibleShiftRelations.stream()
            .map(ShiftDefinitionTeamRelEntity::getShiftDefinitionId)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, ShiftDefinitionEntity> shiftDefinitionById = visibleShiftDefinitionIds.isEmpty()
            ? Map.of()
            : shiftDefinitionMapper.selectList(Wrappers.<ShiftDefinitionEntity>lambdaQuery()
                    .in(ShiftDefinitionEntity::getId, visibleShiftDefinitionIds))
                .stream()
                .collect(Collectors.toMap(ShiftDefinitionEntity::getId, definition -> definition));

        Map<Long, List<String>> shiftCodeOptionsByTeam = new LinkedHashMap<>();
        Map<String, String> shiftCodeColorMap = new LinkedHashMap<>();
        Map<Long, Map<String, WorkspaceRosterShiftDetailDto>> shiftDetailsByTeam = new LinkedHashMap<>();

        for (ShiftDefinitionTeamRelEntity relation : visibleShiftRelations) {
            ShiftDefinitionEntity definition = shiftDefinitionById.get(relation.getShiftDefinitionId());
            if (definition == null || !Boolean.TRUE.equals(definition.getVisible()) || safeCellValue(definition.getCode()).isBlank()) {
                continue;
            }
            shiftCodeOptionsByTeam.computeIfAbsent(relation.getTeamId(), ignored -> new ArrayList<>());
            if (!shiftCodeOptionsByTeam.get(relation.getTeamId()).contains(definition.getCode())) {
                shiftCodeOptionsByTeam.get(relation.getTeamId()).add(definition.getCode());
            }
            if (safeCellValue(definition.getColorHex()).length() > 0) {
                shiftCodeColorMap.putIfAbsent(definition.getCode(), definition.getColorHex());
            }
            shiftDetailsByTeam.computeIfAbsent(relation.getTeamId(), ignored -> new LinkedHashMap<>())
                .put(definition.getCode(), toShiftDetail(definition));
        }

        List<String> shiftCodeOptions = shiftCodeOptionsByTeam.values().stream()
            .flatMap(List::stream)
            .distinct()
            .toList();

        return new ImportContext(targetMonth, teamsByName, staffByCode, blockedTeamNames, blockedStaffCodes, shiftCodeOptions, shiftCodeOptionsByTeam, shiftCodeColorMap, shiftDetailsByTeam);
    }

    private List<ImportedRosterRow> readImportRows(MultipartFile file, YearMonth targetMonth) {
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheet(TEMPLATE_SHEET_NAME);
            if (sheet == null) {
                sheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
            }
            if (sheet == null) {
                return List.of();
            }

            DataFormatter formatter = new DataFormatter(Locale.ENGLISH);
            List<ImportedRosterRow> rows = new ArrayList<>();
            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null || isBlankRow(row, targetMonth.lengthOfMonth(), formatter)) {
                    continue;
                }
                Map<Integer, String> schedule = new LinkedHashMap<>();
                for (int day = 1; day <= targetMonth.lengthOfMonth(); day++) {
                    schedule.put(day, safeCellValue(formatter.formatCellValue(row.getCell(day + 1))));
                }
                rows.add(new ImportedRosterRow(
                    safeCellValue(formatter.formatCellValue(row.getCell(0))),
                    safeCellValue(formatter.formatCellValue(row.getCell(1))),
                    schedule
                ));
            }
            return rows;
        } catch (IOException ex) {
            throw new BadRequestException("Failed to read import workbook: " + ex.getMessage());
        }
    }

    private boolean isBlankRow(Row row, int totalDays, DataFormatter formatter) {
        for (int index = 0; index <= totalDays + 1; index++) {
            if (!safeCellValue(formatter.formatCellValue(row.getCell(index))).isBlank()) {
                return false;
            }
        }
        return true;
    }

    private byte[] createWorkbook(YearMonth targetMonth, List<ImportedRosterRow> rows) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(TEMPLATE_SHEET_NAME);
            Row headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue("staff_id");
            headerRow.createCell(1).setCellValue("team");
            sheet.setColumnWidth(0, 20 * 256);
            sheet.setColumnWidth(1, 24 * 256);
            for (int day = 1; day <= 31; day++) {
                headerRow.createCell(day + 1).setCellValue(String.valueOf(day));
                sheet.setColumnWidth(day + 1, 10 * 256);
            }

            int rowIndex = 1;
            for (ImportedRosterRow row : rows) {
                Row sheetRow = sheet.createRow(rowIndex++);
                sheetRow.createCell(0).setCellValue(row.staffCode());
                sheetRow.createCell(1).setCellValue(row.teamName());
                for (int day = 1; day <= 31; day++) {
                    String value = day <= targetMonth.lengthOfMonth() ? safeCellValue(row.scheduleByDay().get(day)) : "";
                    sheetRow.createCell(day + 1).setCellValue(value);
                }
            }

            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (IOException ex) {
            throw new BadRequestException("Failed to build workbook: " + ex.getMessage());
        }
    }

    private ResponseEntity<byte[]> workbookResponse(byte[] workbookBytes, YearMonth targetMonth, String prefix) {
        String fileName = prefix + targetMonth.getYear() + "-" + String.format("%02d", targetMonth.getMonthValue()) + ".xlsx";
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + fileName)
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .body(workbookBytes);
    }

    private ShiftDefinitionEntity findShiftDefinitionForTeamAndCode(Long teamId, String shiftCode) {
        if (teamId == null || safeCellValue(shiftCode).isBlank()) {
            return null;
        }

        List<ShiftDefinitionEntity> candidates = shiftDefinitionMapper.selectList(Wrappers.<ShiftDefinitionEntity>lambdaQuery()
            .eq(ShiftDefinitionEntity::getCode, shiftCode)
            .eq(ShiftDefinitionEntity::getVisible, true));
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

        return candidates.stream()
            .filter(candidate -> matchingIds.contains(candidate.getId()))
            .findFirst()
            .orElse(null);
    }

    private WorkspaceRosterShiftDetailDto toShiftDetail(ShiftDefinitionEntity shiftDefinition) {
        LocalTime startTime = shiftDefinition.getStartTime();
        int durationMinutes = shiftTimeSupport.resolveDurationMinutes(shiftDefinition);
        LocalTime endTime = shiftTimeSupport.deriveEndTime(startTime, durationMinutes == 0 ? null : durationMinutes);
        return new WorkspaceRosterShiftDetailDto(
            shiftDefinition.getId(),
            shiftDefinition.getCode(),
            shiftDefinition.getMeaning(),
            startTime,
            endTime,
            durationMinutes == 0 ? null : durationMinutes,
            lookupService.normalizeWorkspaceTimezone(shiftDefinition.getTimezone()),
            shiftDefinition.getPrimaryShift(),
            shiftDefinition.getColorHex(),
            shiftTimeSupport.isOvernight(startTime, durationMinutes == 0 ? null : durationMinutes)
        );
    }

    private WorkspaceValidationIssueDto buildIssue(Long id, Long teamId, String severity, String type, String description, String teamName, LocalDate issueDate) {
        return new WorkspaceValidationIssueDto(
            id,
            teamId,
            severity,
            type,
            description,
            teamName,
            issueDate == null ? "-" : issueDate.format(ISSUE_DATE_FORMATTER),
            false,
            null
        );
    }

    private YearMonth resolveMonth(Integer year, Integer month) {
        YearMonth now = YearMonth.now();
        return YearMonth.of(year == null ? now.getYear() : year, month == null ? now.getMonthValue() : month);
    }

    private String normalizeKey(String value) {
        return safeCellValue(value).trim().toLowerCase(Locale.ROOT);
    }

    private String safeCellValue(String value) {
        return value == null ? "" : value.trim();
    }

    private String coalesce(String primary, String fallback) {
        return safeCellValue(primary).isBlank() ? safeCellValue(fallback) : primary;
    }

    private record ImportedRosterRow(String staffCode, String teamName, Map<Integer, String> scheduleByDay) {
    }

    private record ImportContext(
        YearMonth targetMonth,
        Map<String, TeamEntity> teamsByName,
        Map<String, StaffEntity> staffByCode,
        Set<String> blockedTeamNames,
        Set<String> blockedStaffCodes,
        List<String> shiftCodeOptions,
        Map<Long, List<String>> shiftCodeOptionsByTeam,
        Map<String, String> shiftCodeColorMap,
        Map<Long, Map<String, WorkspaceRosterShiftDetailDto>> shiftDetailsByTeam
    ) {
    }

    private record PreviewTeamState(Long teamId, String teamName, String color, boolean newTeam) {
    }

    private static final class PreviewStaffState {
        private final Long previewStaffId;
        private final Long existingStaffId;
        private final String staffCode;
        private final String staffName;
        private final String roleName;
        private final boolean newStaff;
        private Long teamId;
        private String teamName;
        private final Map<Integer, String> schedule = new LinkedHashMap<>();

        private PreviewStaffState(Long previewStaffId, Long existingStaffId, String staffCode, String staffName, String roleName, boolean newStaff) {
            this.previewStaffId = previewStaffId;
            this.existingStaffId = existingStaffId;
            this.staffCode = staffCode;
            this.staffName = staffName;
            this.roleName = roleName;
            this.newStaff = newStaff;
        }

        private Long previewStaffId() {
            return previewStaffId;
        }

        private Long existingStaffId() {
            return existingStaffId;
        }

        private String staffCode() {
            return staffCode;
        }

        private String staffName() {
            return staffName;
        }

        private String roleName() {
            return roleName;
        }

        private boolean newStaff() {
            return newStaff;
        }

        private Map<Integer, String> schedule() {
            return schedule;
        }
    }
}
