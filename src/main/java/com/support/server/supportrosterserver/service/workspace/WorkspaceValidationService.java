package com.support.server.supportrosterserver.service.workspace;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceValidationIssueDto;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceValidationResolveRequest;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceValidationResolveResponse;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceValidationResponse;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceValidationSummaryDto;
import com.support.server.supportrosterserver.entity.workspace.ImportIssueEntity;
import com.support.server.supportrosterserver.entity.workspace.ImportBatchEntity;
import com.support.server.supportrosterserver.entity.workspace.RosterAssignmentEntity;
import com.support.server.supportrosterserver.entity.workspace.ShiftDefinitionEntity;
import com.support.server.supportrosterserver.entity.workspace.ShiftDefinitionTeamRelEntity;
import com.support.server.supportrosterserver.entity.workspace.StaffEntity;
import com.support.server.supportrosterserver.entity.workspace.TeamEntity;
import com.support.server.supportrosterserver.mapper.ImportBatchMapper;
import com.support.server.supportrosterserver.mapper.ImportIssueMapper;
import com.support.server.supportrosterserver.mapper.RosterAssignmentMapper;
import com.support.server.supportrosterserver.mapper.ShiftDefinitionMapper;
import com.support.server.supportrosterserver.mapper.ShiftDefinitionTeamRelMapper;
import com.support.server.supportrosterserver.mapper.StaffMapper;
import com.support.server.supportrosterserver.service.auth.AuthContextService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WorkspaceValidationService {

    private static final String RESOLUTION_KIND_IMPORT_ISSUE = "import-issue";
    private static final String RESOLUTION_KIND_MANUAL = "manual";

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MMM dd", Locale.ENGLISH);

    private final StaffMapper staffMapper;
    private final ShiftDefinitionMapper shiftDefinitionMapper;
    private final RosterAssignmentMapper rosterAssignmentMapper;
    private final ImportBatchMapper importBatchMapper;
    private final ImportIssueMapper importIssueMapper;
    private final WorkspaceLookupService lookupService;
    private final ShiftDefinitionTeamRelMapper shiftDefinitionTeamRelMapper;
    private final AuthContextService authContextService;
    private final WorkspaceShiftTimeSupport shiftTimeSupport;

    public WorkspaceValidationResponse getValidation(Integer year, Integer month) {
        return getValidation(year, month, false);
    }

    public WorkspaceValidationResponse getValidation(Integer year, Integer month, boolean summaryOnly) {
        YearMonth targetMonth = resolveMonth(year, month);
        Map<Long, TeamEntity> teamMap = lookupService.teamMap();
        Set<Long> readableTeamIds = new LinkedHashSet<>(authContextService.readableTeamIds());
        Map<String, Long> teamIdByName = buildTeamIdByName(teamMap);
        ValidationAccumulator accumulator = new ValidationAccumulator(!summaryOnly, readableTeamIds, teamIdByName);
        loadImportIssues(targetMonth, accumulator);
        validateLiveData(targetMonth, accumulator, teamMap);
        return accumulator.toResponse();
    }

    public List<WorkspaceValidationIssueDto> validateLiveData(YearMonth targetMonth) {
        Map<Long, TeamEntity> teamMap = lookupService.teamMap();
        Set<Long> readableTeamIds = new LinkedHashSet<>(authContextService.readableTeamIds());
        Map<String, Long> teamIdByName = buildTeamIdByName(teamMap);
        ValidationAccumulator accumulator = new ValidationAccumulator(true, readableTeamIds, teamIdByName);
        validateLiveData(targetMonth, accumulator, teamMap);
        return accumulator.issues;
    }

    private void validateLiveData(YearMonth targetMonth, ValidationAccumulator accumulator, Map<Long, TeamEntity> teamMap) {
        List<ShiftDefinitionEntity> definitions = shiftDefinitionMapper.selectList(Wrappers.<ShiftDefinitionEntity>lambdaQuery());
        Map<Long, Set<Long>> teamIdsByDefinitionId = shiftDefinitionTeamRelMapper.selectList(Wrappers.<ShiftDefinitionTeamRelEntity>lambdaQuery())
            .stream()
            .collect(Collectors.groupingBy(
                ShiftDefinitionTeamRelEntity::getShiftDefinitionId,
                Collectors.mapping(ShiftDefinitionTeamRelEntity::getTeamId, Collectors.toSet())
            ));

        Map<Long, ShiftDefinitionEntity> shiftDefinitionById = new HashMap<>();
        for (ShiftDefinitionEntity def : definitions) {
            shiftDefinitionById.put(def.getId(), def);
            if (def.getStartTime() == null || shiftTimeSupport.resolveDurationMinutes(def) == 0) {
                Long firstTeamId = teamIdsByDefinitionId.getOrDefault(def.getId(), Set.of()).stream().findFirst().orElse(def.getTeamId());
                TeamEntity team = firstTeamId == null ? null : teamMap.get(firstTeamId);
                accumulator.record(
                    accumulator.nextSyntheticId(),
                    "medium",
                    "Invalid Shift Definition",
                    "Shift definition time range is incomplete for code '" + def.getCode() + "'.",
                    team == null ? "-" : team.getName(),
                    "-",
                    false,
                    RESOLUTION_KIND_MANUAL
                );
            }
        }

        for (StaffEntity staff : staffMapper.selectList(Wrappers.<StaffEntity>lambdaQuery()
                .orderByAsc(StaffEntity::getName))) {
            if (staff.getTimezone() == null || staff.getTimezone().isBlank()) {
                TeamEntity team = staff.getTeamId() == null ? null : teamMap.get(staff.getTeamId());
                accumulator.record(
                    accumulator.nextSyntheticId(),
                    "low",
                    "Time Zone Ambiguity",
                    "Staff " + staff.getName() + " has no timezone assigned.",
                    team == null ? "-" : team.getName(),
                    "-",
                    false,
                    RESOLUTION_KIND_MANUAL
                );
            }
            if (staff.getTeamId() == null || teamMap.get(staff.getTeamId()) == null) {
                accumulator.record(
                    accumulator.nextSyntheticId(),
                    "medium",
                    "Missing Team",
                    "Staff " + staff.getName() + " references a team that does not exist.",
                    "-",
                    "-",
                    false,
                    RESOLUTION_KIND_MANUAL
                );
            }
        }

        LocalDate start = targetMonth.atDay(1);
        LocalDate end = targetMonth.atEndOfMonth();
        List<RosterAssignmentEntity> assignments = rosterAssignmentMapper.selectList(Wrappers.<RosterAssignmentEntity>lambdaQuery()
            .between(RosterAssignmentEntity::getAssignmentDate, start, end)
            .orderByAsc(RosterAssignmentEntity::getAssignmentDate));

        Map<String, List<RosterAssignmentEntity>> assignmentsByStaffDay = new HashMap<>();
        for (RosterAssignmentEntity assignment : assignments) {
            String staffDayKey = assignment.getStaffId() + "|" + assignment.getAssignmentDate();
            assignmentsByStaffDay.computeIfAbsent(staffDayKey, ignored -> new ArrayList<>()).add(assignment);

            ShiftDefinitionEntity shiftDefinition = shiftDefinitionById.get(assignment.getShiftDefinitionId());
            Set<Long> shiftDefinitionTeamIds = shiftDefinition == null
                ? Set.of()
                : teamIdsByDefinitionId.getOrDefault(assignment.getShiftDefinitionId(), Set.of());
            if (shiftDefinition == null || !shiftDefinitionTeamIds.contains(assignment.getTeamId())) {
                TeamEntity team = assignment.getTeamId() == null ? null : teamMap.get(assignment.getTeamId());
                accumulator.record(
                    accumulator.nextSyntheticId(),
                    "medium",
                    "Invalid Shift Definition",
                    "Assignment references a shift definition that is missing or no longer available for the team.",
                    team == null ? "-" : team.getName(),
                    assignment.getAssignmentDate().format(DATE_FORMATTER),
                    false,
                    RESOLUTION_KIND_MANUAL
                );
            }
        }

        for (List<RosterAssignmentEntity> sameDayAssignments : assignmentsByStaffDay.values()) {
            if (sameDayAssignments.size() > 1) {
                RosterAssignmentEntity sample = sameDayAssignments.get(0);
                TeamEntity team = sample.getTeamId() == null ? null : teamMap.get(sample.getTeamId());
                accumulator.record(
                    accumulator.nextSyntheticId(),
                    "high",
                    "Overlapping Assignment",
                    "Staff " + sample.getStaffId() + " has multiple assignments on the same day.",
                    team == null ? "-" : team.getName(),
                    sample.getAssignmentDate().format(DATE_FORMATTER),
                    false,
                    RESOLUTION_KIND_MANUAL
                );
            }
        }
    }

    public WorkspaceValidationResolveResponse resolveIssues(WorkspaceValidationResolveRequest request) {
        List<Long> requestedIds = request.getIssueIds().stream()
            .filter(Objects::nonNull)
            .distinct()
            .toList();

        if (requestedIds.isEmpty()) {
            return new WorkspaceValidationResolveResponse(0, 0, List.of(), List.of(), getValidation(request.getYear(), request.getMonth()));
        }

        List<ImportIssueEntity> importIssues = importIssueMapper.selectList(Wrappers.<ImportIssueEntity>lambdaQuery()
            .in(ImportIssueEntity::getId, requestedIds)
            .eq(ImportIssueEntity::getResolved, false));
        for (ImportIssueEntity issue : importIssues) {
            Long issueTeamId = issue.getTeamName() == null ? null : mapTeamNameToId(issue.getTeamName());
            authContextService.requireWritableTeam(issueTeamId);
        }

        List<Long> resolvedIds = new ArrayList<>();
        for (ImportIssueEntity issue : importIssues) {
            issue.setResolved(true);
            importIssueMapper.updateById(issue);
            resolvedIds.add(issue.getId());
        }

        List<Long> skippedIds = requestedIds.stream()
            .filter(id -> !resolvedIds.contains(id))
            .toList();

        return new WorkspaceValidationResolveResponse(
            resolvedIds.size(),
            skippedIds.size(),
            resolvedIds,
            skippedIds,
            getValidation(request.getYear(), request.getMonth())
        );
    }

    private void loadImportIssues(YearMonth targetMonth, ValidationAccumulator accumulator) {
        List<Long> batchIds = importBatchMapper.selectList(Wrappers.<ImportBatchEntity>lambdaQuery()
                .eq(ImportBatchEntity::getRosterYear, targetMonth.getYear())
                .eq(ImportBatchEntity::getRosterMonth, targetMonth.getMonthValue())
                .ne(ImportBatchEntity::getStatus, "APPLIED"))
            .stream()
            .map(ImportBatchEntity::getId)
            .toList();

        if (batchIds.isEmpty()) {
            return;
        }

        importIssueMapper.selectList(Wrappers.<ImportIssueEntity>lambdaQuery()
                .in(ImportIssueEntity::getBatchId, batchIds)
                .eq(ImportIssueEntity::getResolved, false)
                .orderByDesc(ImportIssueEntity::getCreateTime)
                .last("limit 100"))
            .stream()
            .filter(issue -> !"Missing Primary Coverage".equals(issue.getIssueType()))
            .forEach(issue -> accumulator.record(
                issue.getId(),
                issue.getSeverity(),
                issue.getIssueType(),
                issue.getDescription(),
                issue.getTeamName(),
                issue.getIssueDate() == null ? "-" : issue.getIssueDate().format(DATE_FORMATTER),
                true,
                RESOLUTION_KIND_IMPORT_ISSUE
            ));
    }

    private Map<String, Long> buildTeamIdByName(Map<Long, TeamEntity> teamMap) {
        Map<String, Long> normalizedMap = new HashMap<>();
        for (TeamEntity team : teamMap.values()) {
            if (team.getName() != null && !team.getName().isBlank()) {
                normalizedMap.put(normalizeTeamName(team.getName()), team.getId());
            }
        }
        return normalizedMap;
    }

    private Long mapTeamNameToId(String teamName) {
        TeamEntity team = lookupService.findTeamByName(teamName);
        return team == null ? null : team.getId();
    }

    private String normalizeTeamName(String teamName) {
        return teamName == null ? "" : teamName.trim().toLowerCase(Locale.ROOT);
    }

    private YearMonth resolveMonth(Integer year, Integer month) {
        YearMonth now = YearMonth.now();
        return YearMonth.of(year == null ? now.getYear() : year, month == null ? now.getMonthValue() : month);
    }

    private final class ValidationAccumulator {
        private final boolean collectIssues;
        private final Set<Long> readableTeamIds;
        private final Map<String, Long> teamIdByName;
        private final AtomicLong syntheticIdGenerator = new AtomicLong(1_000_000L);
        private final List<WorkspaceValidationIssueDto> issues;
        private long high;
        private long medium;
        private long low;
        private WorkspaceValidationIssueDto topIssue;

        private ValidationAccumulator(boolean collectIssues, Set<Long> readableTeamIds, Map<String, Long> teamIdByName) {
            this.collectIssues = collectIssues;
            this.readableTeamIds = readableTeamIds;
            this.teamIdByName = teamIdByName;
            this.issues = collectIssues ? new ArrayList<>() : List.of();
        }

        private long nextSyntheticId() {
            return syntheticIdGenerator.getAndIncrement();
        }

        private void record(Long id, String severity, String type, String description, String team, String date, boolean resolvable, String resolutionKind) {
            if (!isReadableTeam(team)) {
                return;
            }

            incrementSummary(severity);

            WorkspaceValidationIssueDto issueForTop = null;
            if (collectIssues || (topIssue == null && "high".equalsIgnoreCase(severity))) {
                issueForTop = new WorkspaceValidationIssueDto(id, severity, type, description, team, date, resolvable, resolutionKind);
            }

            if (collectIssues) {
                issues.add(issueForTop);
            }

            if (topIssue == null && "high".equalsIgnoreCase(severity)) {
                topIssue = issueForTop;
            }
        }

        private boolean isReadableTeam(String team) {
            if (team == null || team.isBlank() || "-".equals(team)) {
                return true;
            }

            Long teamId = teamIdByName.get(normalizeTeamName(team));
            return teamId == null || readableTeamIds.contains(teamId);
        }

        private void incrementSummary(String severity) {
            if ("high".equalsIgnoreCase(severity)) {
                high += 1;
            } else if ("medium".equalsIgnoreCase(severity)) {
                medium += 1;
            } else if ("low".equalsIgnoreCase(severity)) {
                low += 1;
            }
        }

        private WorkspaceValidationResponse toResponse() {
            return new WorkspaceValidationResponse(
                new WorkspaceValidationSummaryDto(high, medium, low),
                issues,
                topIssue
            );
        }
    }
}
