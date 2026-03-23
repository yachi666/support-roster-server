package com.support.server.supportrosterserver.service.workspace;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
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

    public WorkspaceValidationResponse getValidation(Integer year, Integer month) {
        YearMonth targetMonth = resolveMonth(year, month);
        List<WorkspaceValidationIssueDto> issues = new ArrayList<>();
        issues.addAll(loadImportIssues(targetMonth));
        issues.addAll(validateLiveData(targetMonth));

        long high = issues.stream().filter(issue -> "high".equalsIgnoreCase(issue.getSeverity())).count();
        long medium = issues.stream().filter(issue -> "medium".equalsIgnoreCase(issue.getSeverity())).count();
        long low = issues.stream().filter(issue -> "low".equalsIgnoreCase(issue.getSeverity())).count();

        return new WorkspaceValidationResponse(new WorkspaceValidationSummaryDto(high, medium, low), issues);
    }

    public List<WorkspaceValidationIssueDto> validateLiveData(YearMonth targetMonth) {
        AtomicLong idGenerator = new AtomicLong(1_000_000L);
        List<WorkspaceValidationIssueDto> issues = new ArrayList<>();

        Map<Long, TeamEntity> teamMap = lookupService.teamMap();
        List<ShiftDefinitionEntity> definitions = shiftDefinitionMapper.selectList(Wrappers.<ShiftDefinitionEntity>lambdaQuery());
        Map<Long, Set<Long>> teamIdsByDefinitionId = shiftDefinitionTeamRelMapper.selectList(Wrappers.<ShiftDefinitionTeamRelEntity>lambdaQuery())
            .stream()
            .collect(Collectors.groupingBy(
                ShiftDefinitionTeamRelEntity::getShiftDefinitionId,
                Collectors.mapping(ShiftDefinitionTeamRelEntity::getTeamId, Collectors.toSet())
            ));

        Map<String, ShiftDefinitionEntity> shiftDefinitionByTeamAndCode = new HashMap<>();
        for (ShiftDefinitionEntity def : definitions) {
            for (Long teamId : teamIdsByDefinitionId.getOrDefault(def.getId(), Set.of())) {
                shiftDefinitionByTeamAndCode.put(teamId + "|" + def.getCode(), def);
            }
            if (def.getStartTime() == null || def.getEndTime() == null) {
                Long firstTeamId = teamIdsByDefinitionId.getOrDefault(def.getId(), Set.of()).stream().findFirst().orElse(def.getTeamId());
                TeamEntity team = firstTeamId == null ? null : teamMap.get(firstTeamId);
                issues.add(new WorkspaceValidationIssueDto(
                    idGenerator.getAndIncrement(),
                    "medium",
                    "Invalid Shift Definition",
                    "Shift definition time range is incomplete for code '" + def.getCode() + "'.",
                    team == null ? "-" : team.getName(),
                    "-",
                    false,
                    RESOLUTION_KIND_MANUAL
                ));
            }
        }

        for (StaffEntity staff : staffMapper.selectList(Wrappers.<StaffEntity>lambdaQuery()
                .orderByAsc(StaffEntity::getName))) {
            if (staff.getTimezone() == null || staff.getTimezone().isBlank()) {
                TeamEntity team = staff.getTeamId() == null ? null : teamMap.get(staff.getTeamId());
                issues.add(new WorkspaceValidationIssueDto(
                    idGenerator.getAndIncrement(),
                    "low",
                    "Time Zone Ambiguity",
                    "Staff " + staff.getName() + " has no timezone assigned.",
                    team == null ? "-" : team.getName(),
                    "-",
                    false,
                    RESOLUTION_KIND_MANUAL
                ));
            }
            if (staff.getTeamId() == null || teamMap.get(staff.getTeamId()) == null) {
                issues.add(new WorkspaceValidationIssueDto(
                    idGenerator.getAndIncrement(),
                    "medium",
                    "Missing Team",
                    "Staff " + staff.getName() + " references a team that does not exist.",
                    "-",
                    "-",
                    false,
                    RESOLUTION_KIND_MANUAL
                ));
            }
        }

        LocalDate start = targetMonth.atDay(1);
        LocalDate end = targetMonth.atEndOfMonth();
        List<RosterAssignmentEntity> assignments = rosterAssignmentMapper.selectList(Wrappers.<RosterAssignmentEntity>lambdaQuery()
            .between(RosterAssignmentEntity::getAssignmentDate, start, end)
            .orderByAsc(RosterAssignmentEntity::getAssignmentDate));

        Map<String, List<RosterAssignmentEntity>> assignmentsByStaffDay = new HashMap<>();
        Map<String, List<RosterAssignmentEntity>> assignmentsByTeamDay = new HashMap<>();
        for (RosterAssignmentEntity assignment : assignments) {
            String staffDayKey = assignment.getStaffId() + "|" + assignment.getAssignmentDate();
            assignmentsByStaffDay.computeIfAbsent(staffDayKey, ignored -> new ArrayList<>()).add(assignment);

            String teamDayKey = assignment.getTeamId() + "|" + assignment.getAssignmentDate();
            assignmentsByTeamDay.computeIfAbsent(teamDayKey, ignored -> new ArrayList<>()).add(assignment);

            if (!shiftDefinitionByTeamAndCode.containsKey(assignment.getTeamId() + "|" + assignment.getShiftCode())) {
                TeamEntity team = assignment.getTeamId() == null ? null : lookupService.teamMap().get(assignment.getTeamId());
                issues.add(new WorkspaceValidationIssueDto(
                    idGenerator.getAndIncrement(),
                    "medium",
                    "Invalid Shift Code",
                    "Code '" + assignment.getShiftCode() + "' not found in shift definitions.",
                    team == null ? "-" : team.getName(),
                    assignment.getAssignmentDate().format(DATE_FORMATTER),
                    false,
                    RESOLUTION_KIND_MANUAL
                ));
            }
        }

        for (List<RosterAssignmentEntity> sameDayAssignments : assignmentsByStaffDay.values()) {
            if (sameDayAssignments.size() > 1) {
                RosterAssignmentEntity sample = sameDayAssignments.get(0);
                TeamEntity team = sample.getTeamId() == null ? null : lookupService.teamMap().get(sample.getTeamId());
                issues.add(new WorkspaceValidationIssueDto(
                    idGenerator.getAndIncrement(),
                    "high",
                    "Overlapping Assignment",
                    "Staff " + sample.getStaffId() + " has multiple assignments on the same day.",
                    team == null ? "-" : team.getName(),
                    sample.getAssignmentDate().format(DATE_FORMATTER),
                    false,
                    RESOLUTION_KIND_MANUAL
                ));
            }
        }

        return issues;
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

    private List<WorkspaceValidationIssueDto> loadImportIssues(YearMonth targetMonth) {
        List<Long> batchIds = importBatchMapper.selectList(Wrappers.<ImportBatchEntity>lambdaQuery()
                .eq(ImportBatchEntity::getRosterYear, targetMonth.getYear())
                .eq(ImportBatchEntity::getRosterMonth, targetMonth.getMonthValue())
                .ne(ImportBatchEntity::getStatus, "APPLIED"))
            .stream()
            .map(ImportBatchEntity::getId)
            .toList();

        if (batchIds.isEmpty()) {
            return List.of();
        }

        return importIssueMapper.selectList(Wrappers.<ImportIssueEntity>lambdaQuery()
                .in(ImportIssueEntity::getBatchId, batchIds)
                .eq(ImportIssueEntity::getResolved, false)
                .orderByDesc(ImportIssueEntity::getCreateTime)
                .last("limit 100"))
            .stream()
            .filter(issue -> !"Missing Primary Coverage".equals(issue.getIssueType()))
            .map(issue -> new WorkspaceValidationIssueDto(
                issue.getId(),
                issue.getSeverity(),
                issue.getIssueType(),
                issue.getDescription(),
                issue.getTeamName(),
                issue.getIssueDate() == null ? "-" : issue.getIssueDate().format(DATE_FORMATTER),
                true,
                RESOLUTION_KIND_IMPORT_ISSUE
            ))
            .toList();
    }

    private YearMonth resolveMonth(Integer year, Integer month) {
        YearMonth now = YearMonth.now();
        return YearMonth.of(year == null ? now.getYear() : year, month == null ? now.getMonthValue() : month);
    }
}
