package com.support.server.supportrosterserver.service.workspace;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
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
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceValidationRemediationApplyResponse;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceValidationRemediationDto;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceValidationRemediationPreviewResponse;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceValidationRemediationRequest;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceValidationIssueDto;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceValidationResolveRequest;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceValidationResolveResponse;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceValidationResponse;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceValidationSummaryDto;
import com.support.server.supportrosterserver.entity.auth.WorkspaceAccountEntity;
import com.support.server.supportrosterserver.entity.auth.WorkspaceAccountTeamScopeEntity;
import com.support.server.supportrosterserver.entity.workspace.ImportIssueEntity;
import com.support.server.supportrosterserver.entity.workspace.ImportBatchEntity;
import com.support.server.supportrosterserver.entity.workspace.RosterAssignmentEntity;
import com.support.server.supportrosterserver.entity.workspace.ShiftDefinitionEntity;
import com.support.server.supportrosterserver.entity.workspace.ShiftDefinitionTeamRelEntity;
import com.support.server.supportrosterserver.entity.workspace.StaffEntity;
import com.support.server.supportrosterserver.entity.workspace.TeamEntity;
import com.support.server.supportrosterserver.exception.BadRequestException;
import com.support.server.supportrosterserver.mapper.ImportBatchMapper;
import com.support.server.supportrosterserver.mapper.ImportIssueMapper;
import com.support.server.supportrosterserver.mapper.RosterAssignmentMapper;
import com.support.server.supportrosterserver.mapper.ShiftDefinitionMapper;
import com.support.server.supportrosterserver.mapper.ShiftDefinitionTeamRelMapper;
import com.support.server.supportrosterserver.mapper.StaffMapper;
import com.support.server.supportrosterserver.mapper.WorkspaceAccountMapper;
import com.support.server.supportrosterserver.mapper.WorkspaceAccountTeamScopeMapper;
import com.support.server.supportrosterserver.service.auth.AuthContextService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WorkspaceValidationService {

    private static final String RESOLUTION_KIND_IMPORT_ISSUE = "import-issue";
    private static final String RESOLUTION_KIND_MANUAL = "manual";
    private static final String RESOLUTION_KIND_SYSTEM_CLEANUP = "system-cleanup";
    private static final String DOMAIN_IMPORT = "import";
    private static final String DOMAIN_CONFIGURATION = "configuration";
    private static final String DOMAIN_ROSTER = "roster";
    private static final String TARGET_PAGE_IMPORT = "/workspace/import-export";
    private static final String TARGET_PAGE_ROSTER = "/workspace/roster";
    private static final String TARGET_PAGE_STAFF = "/workspace/staff";
    private static final String TARGET_PAGE_SHIFTS = "/workspace/shifts";
    private static final String TARGET_PAGE_TEAMS = "/workspace/teams";
    private static final String TARGET_PAGE_ACCOUNTS = "/workspace/accounts";
    private static final String REMEDIATION_TYPE_DELETE_RECORDS = "delete-records";
    private static final String REMEDIATION_ROLE_ADMIN = "admin";
    private static final String ACTION_DELETE_INVALID_TEAM_SCOPE = "delete_invalid_team_scope";
    private static final String ACTION_DELETE_ORPHAN_ASSIGNMENT = "delete_orphan_assignment";

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MMM dd", Locale.ENGLISH);

    private final StaffMapper staffMapper;
    private final ShiftDefinitionMapper shiftDefinitionMapper;
    private final RosterAssignmentMapper rosterAssignmentMapper;
    private final ImportBatchMapper importBatchMapper;
    private final ImportIssueMapper importIssueMapper;
    private final WorkspaceLookupService lookupService;
    private final ShiftDefinitionTeamRelMapper shiftDefinitionTeamRelMapper;
    private final WorkspaceAccountMapper workspaceAccountMapper;
    private final WorkspaceAccountTeamScopeMapper workspaceAccountTeamScopeMapper;
    private final AuthContextService authContextService;
    private final WorkspaceShiftTimeSupport shiftTimeSupport;
    private final WorkspaceOperationLogService workspaceOperationLogService;

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
        if (shouldExposeAdminCleanupIssues()) {
            List<WorkspaceAccountTeamScopeEntity> accountTeamScopes = workspaceAccountTeamScopeMapper.selectList(Wrappers.<WorkspaceAccountTeamScopeEntity>lambdaQuery()
                .orderByAsc(WorkspaceAccountTeamScopeEntity::getAccountId)
                .orderByAsc(WorkspaceAccountTeamScopeEntity::getTeamId));
            List<Long> accountIds = accountTeamScopes.stream()
                .map(WorkspaceAccountTeamScopeEntity::getAccountId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
            Map<Long, WorkspaceAccountEntity> accountsById = accountIds.isEmpty()
                ? Map.of()
                : workspaceAccountMapper.selectBatchIds(accountIds).stream()
                    .collect(Collectors.toMap(WorkspaceAccountEntity::getId, account -> account));
            for (WorkspaceAccountTeamScopeEntity scope : accountTeamScopes) {
                if (scope.getTeamId() != null && teamMap.containsKey(scope.getTeamId())) {
                    continue;
                }
                WorkspaceAccountEntity account = accountsById.get(scope.getAccountId());
                String accountLabel = account == null || account.getStaffId() == null || account.getStaffId().isBlank()
                    ? "unknown account"
                    : account.getStaffId();
                accumulator.record(
                    accumulator.nextSyntheticId(),
                    "medium",
                    "config.account.team-scope-invalid",
                    DOMAIN_CONFIGURATION,
                    false,
                    "Invalid Team Scope",
                    "Workspace account " + accountLabel + " references a team scope that no longer exists.",
                    "-",
                    "-",
                    TARGET_PAGE_ACCOUNTS,
                    true,
                    RESOLUTION_KIND_SYSTEM_CLEANUP,
                    ACTION_DELETE_INVALID_TEAM_SCOPE,
                    scope.getId()
                );
            }
        }

        List<ShiftDefinitionEntity> definitions = shiftDefinitionMapper.selectList(Wrappers.<ShiftDefinitionEntity>lambdaQuery());
        Map<Long, Set<Long>> teamIdsByDefinitionId = shiftDefinitionTeamRelMapper.selectList(Wrappers.<ShiftDefinitionTeamRelEntity>lambdaQuery())
            .stream()
            .collect(Collectors.groupingBy(
                ShiftDefinitionTeamRelEntity::getShiftDefinitionId,
                Collectors.mapping(ShiftDefinitionTeamRelEntity::getTeamId, Collectors.toSet())
            ));
        List<StaffEntity> staffMembers = staffMapper.selectList(Wrappers.<StaffEntity>lambdaQuery()
            .orderByAsc(StaffEntity::getName));

        Map<Long, ShiftDefinitionEntity> shiftDefinitionById = new HashMap<>();
        Map<Long, Set<Long>> primaryShiftDefinitionIdsByTeam = new HashMap<>();
        for (ShiftDefinitionEntity def : definitions) {
            shiftDefinitionById.put(def.getId(), def);
            Set<Long> definitionTeamIds = resolveDefinitionTeamIds(def, teamIdsByDefinitionId);
            if (Boolean.TRUE.equals(def.getVisible()) && Boolean.TRUE.equals(def.getPrimaryShift())) {
                for (Long teamId : definitionTeamIds) {
                    if (teamId != null) {
                        primaryShiftDefinitionIdsByTeam.computeIfAbsent(teamId, ignored -> new LinkedHashSet<>()).add(def.getId());
                    }
                }
            }

            if (Boolean.TRUE.equals(def.getVisible()) && (def.getStartTime() == null || shiftTimeSupport.resolveDurationMinutes(def) == 0)) {
                Long firstTeamId = definitionTeamIds.stream().findFirst().orElse(def.getTeamId());
                TeamEntity team = firstTeamId == null ? null : teamMap.get(firstTeamId);
                accumulator.record(
                    accumulator.nextSyntheticId(),
                    "medium",
                    "config.shift-definition.time-range-incomplete",
                    DOMAIN_CONFIGURATION,
                    false,
                    "Invalid Shift Definition",
                    "Shift definition time range is incomplete for code '" + def.getCode() + "'.",
                    team == null ? "-" : team.getName(),
                    "-",
                    TARGET_PAGE_SHIFTS,
                    false,
                    RESOLUTION_KIND_MANUAL
                );
            }
        }

        Map<Long, StaffEntity> staffById = new HashMap<>();
        Map<Long, Integer> activeStaffCountByTeam = new HashMap<>();
        for (StaffEntity staff : staffMembers) {
            staffById.put(staff.getId(), staff);
            if (staff.getTeamId() != null && isActiveStaff(staff) && teamMap.containsKey(staff.getTeamId())) {
                activeStaffCountByTeam.merge(staff.getTeamId(), 1, Integer::sum);
            }

            if (isActiveStaff(staff) && (staff.getTimezone() == null || staff.getTimezone().isBlank())) {
                TeamEntity team = staff.getTeamId() == null ? null : teamMap.get(staff.getTeamId());
                accumulator.record(
                    accumulator.nextSyntheticId(),
                    "low",
                    "config.staff.timezone-missing",
                    DOMAIN_CONFIGURATION,
                    false,
                    "Time Zone Ambiguity",
                    "Staff " + staff.getName() + " has no timezone assigned.",
                    team == null ? "-" : team.getName(),
                    "-",
                    TARGET_PAGE_STAFF,
                    false,
                    RESOLUTION_KIND_MANUAL
                );
            }
            if (staff.getTeamId() == null || teamMap.get(staff.getTeamId()) == null) {
                accumulator.record(
                    accumulator.nextSyntheticId(),
                    "medium",
                    "config.staff.team-missing",
                    DOMAIN_CONFIGURATION,
                    false,
                    "Missing Team",
                    "Staff " + staff.getName() + " references a team that does not exist.",
                    "-",
                    "-",
                    TARGET_PAGE_TEAMS,
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
        Map<String, Long> teamIdByTeamDayKey = new HashMap<>();
        Map<String, LocalDate> dateByTeamDayKey = new HashMap<>();
        Set<String> teamDaysWithAssignments = new LinkedHashSet<>();
        Set<String> teamDaysWithPrimaryCoverage = new LinkedHashSet<>();
        for (RosterAssignmentEntity assignment : assignments) {
            String staffDayKey = assignment.getStaffId() + "|" + assignment.getAssignmentDate();
            assignmentsByStaffDay.computeIfAbsent(staffDayKey, ignored -> new ArrayList<>()).add(assignment);
            if (assignment.getTeamId() != null) {
                String teamDayKey = assignment.getTeamId() + "|" + assignment.getAssignmentDate();
                teamDaysWithAssignments.add(teamDayKey);
                teamIdByTeamDayKey.putIfAbsent(teamDayKey, assignment.getTeamId());
                dateByTeamDayKey.putIfAbsent(teamDayKey, assignment.getAssignmentDate());
                if (primaryShiftDefinitionIdsByTeam.getOrDefault(assignment.getTeamId(), Set.of()).contains(assignment.getShiftDefinitionId())) {
                    teamDaysWithPrimaryCoverage.add(teamDayKey);
                }
            }

            ShiftDefinitionEntity shiftDefinition = shiftDefinitionById.get(assignment.getShiftDefinitionId());
            Set<Long> shiftDefinitionTeamIds = shiftDefinition == null
                ? Set.of()
                : resolveDefinitionTeamIds(shiftDefinition, teamIdsByDefinitionId);
            StaffEntity staff = staffById.get(assignment.getStaffId());
            TeamEntity team = assignment.getTeamId() == null ? null : teamMap.get(assignment.getTeamId());
            boolean invalidShiftTeamMapping = shiftDefinition != null
                && assignment.getTeamId() != null
                && !shiftDefinitionTeamIds.contains(assignment.getTeamId());
            boolean orphanAssignment = staff == null
                || team == null
                || shiftDefinition == null
                || assignment.getTeamId() == null
                || invalidShiftTeamMapping;
            if (orphanAssignment) {
                accumulator.record(
                    accumulator.nextSyntheticId(),
                    "high",
                    "roster.assignment.orphaned",
                    DOMAIN_ROSTER,
                    true,
                    "Orphan Assignment",
                    buildOrphanAssignmentDescription(assignment, team, staff, shiftDefinition, invalidShiftTeamMapping),
                    team == null ? "-" : team.getName(),
                    assignment.getAssignmentDate().format(DATE_FORMATTER),
                    TARGET_PAGE_ROSTER,
                    true,
                    RESOLUTION_KIND_SYSTEM_CLEANUP,
                    ACTION_DELETE_ORPHAN_ASSIGNMENT,
                    assignment.getId()
                );
                continue;
            }
            if (!isActiveStaff(staff)) {
                accumulator.record(
                    accumulator.nextSyntheticId(),
                    "medium",
                    "roster.assignment.inactive-staff-scheduled",
                    DOMAIN_ROSTER,
                    true,
                    "Inactive Staff Scheduled",
                    "Staff " + staff.getName() + " is scheduled while marked as " + safeStatus(staff.getStatus()) + ".",
                    team == null ? "-" : team.getName(),
                    assignment.getAssignmentDate().format(DATE_FORMATTER),
                    TARGET_PAGE_STAFF,
                    false,
                    RESOLUTION_KIND_MANUAL
                );
            }

        }

        for (String teamDayKey : teamDaysWithAssignments) {
            Long teamId = teamIdByTeamDayKey.get(teamDayKey);
            if (teamId == null || !primaryShiftDefinitionIdsByTeam.containsKey(teamId)) {
                continue;
            }
            if (activeStaffCountByTeam.getOrDefault(teamId, 0) == 0 || teamDaysWithPrimaryCoverage.contains(teamDayKey)) {
                continue;
            }

            TeamEntity team = teamMap.get(teamId);
            LocalDate date = dateByTeamDayKey.get(teamDayKey);
            accumulator.record(
                accumulator.nextSyntheticId(),
                "high",
                "roster.team-day.primary-coverage-missing",
                DOMAIN_ROSTER,
                true,
                "Missing Primary Coverage",
                "No primary shift is scheduled for team '" + (team == null ? "-" : team.getName()) + "' on " + date.format(DATE_FORMATTER) + ".",
                team == null ? "-" : team.getName(),
                date == null ? "-" : date.format(DATE_FORMATTER),
                TARGET_PAGE_ROSTER,
                false,
                RESOLUTION_KIND_MANUAL
            );
        }

        for (List<RosterAssignmentEntity> sameDayAssignments : assignmentsByStaffDay.values()) {
            if (sameDayAssignments.size() > 1) {
                RosterAssignmentEntity sample = sameDayAssignments.get(0);
                StaffEntity staff = staffById.get(sample.getStaffId());
                TeamEntity team = sample.getTeamId() == null ? null : teamMap.get(sample.getTeamId());
                accumulator.record(
                    accumulator.nextSyntheticId(),
                    "high",
                    "roster.assignment.overlap-same-day",
                    DOMAIN_ROSTER,
                    true,
                    "Overlapping Assignment",
                    "Staff " + (staff == null ? sample.getStaffId() : staff.getName()) + " has multiple assignments on the same day.",
                    team == null ? "-" : team.getName(),
                    sample.getAssignmentDate().format(DATE_FORMATTER),
                    TARGET_PAGE_ROSTER,
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

    public WorkspaceValidationRemediationPreviewResponse previewRemediation(Long issueId, WorkspaceValidationRemediationRequest request) {
        authContextService.requireAdmin();
        WorkspaceValidationIssueDto issue = requireRemediableIssue(issueId, request);
        return buildRemediationPreview(issue);
    }

    @Transactional
    public WorkspaceValidationRemediationApplyResponse applyRemediation(Long issueId, WorkspaceValidationRemediationRequest request) {
        authContextService.requireAdmin();
        WorkspaceValidationIssueDto issue = requireRemediableIssue(issueId, request);
        List<Long> affectedRecordIds = switch (request.getActionKey()) {
            case ACTION_DELETE_INVALID_TEAM_SCOPE -> applyDeleteInvalidTeamScope(issue);
            case ACTION_DELETE_ORPHAN_ASSIGNMENT -> applyDeleteOrphanAssignment(issue);
            default -> throw new BadRequestException("Unsupported remediation action.");
        };
        return new WorkspaceValidationRemediationApplyResponse(
            issueId,
            request.getActionKey(),
            affectedRecordIds.size(),
            affectedRecordIds,
            getValidation(request.getYear(), request.getMonth())
        );
    }

    private WorkspaceValidationIssueDto requireRemediableIssue(Long issueId, WorkspaceValidationRemediationRequest request) {
        if (issueId == null) {
            throw new BadRequestException("Issue ID is required.");
        }
        Long requestedRecordId = request.getRecordId();
        WorkspaceValidationIssueDto issue = getValidation(request.getYear(), request.getMonth()).getIssues().stream()
            .filter(candidate -> candidate.getRemediation() != null)
            .filter(candidate -> request.getActionKey().equals(candidate.getRemediation().getActionKey()))
            .filter(candidate -> requestedRecordId == null
                ? Objects.equals(candidate.getId(), issueId)
                : Objects.equals(candidate.getRemediation().getRecordId(), requestedRecordId))
            .findFirst()
            .orElseThrow(() -> new BadRequestException("Validation issue is no longer available for remediation."));
        if (!Boolean.TRUE.equals(issue.getRemediation().getPreviewable())) {
            throw new BadRequestException("Preview is not supported for the selected remediation.");
        }
        return issue;
    }

    private WorkspaceValidationRemediationPreviewResponse buildRemediationPreview(WorkspaceValidationIssueDto issue) {
        return switch (issue.getRemediation().getActionKey()) {
            case ACTION_DELETE_INVALID_TEAM_SCOPE -> new WorkspaceValidationRemediationPreviewResponse(
                issue.getId(),
                ACTION_DELETE_INVALID_TEAM_SCOPE,
                "Delete invalid team scope",
                "This removes the orphaned team scope record and keeps the linked workspace account intact.",
                "The account itself will not be deleted. This cleanup is permanent.",
                1,
                List.of(requireRemediationRecordId(issue))
            );
            case ACTION_DELETE_ORPHAN_ASSIGNMENT -> new WorkspaceValidationRemediationPreviewResponse(
                issue.getId(),
                ACTION_DELETE_ORPHAN_ASSIGNMENT,
                "Delete orphan assignment",
                "This removes the roster assignment record whose staff, team, or shift definition reference is no longer valid.",
                "The roster entry will be permanently deleted from the selected month.",
                1,
                List.of(requireRemediationRecordId(issue))
            );
            default -> throw new BadRequestException("Unsupported remediation action.");
        };
    }

    private List<Long> applyDeleteInvalidTeamScope(WorkspaceValidationIssueDto issue) {
        Long scopeId = requireRemediationRecordId(issue);
        WorkspaceAccountTeamScopeEntity scope = workspaceAccountTeamScopeMapper.selectById(scopeId);
        if (scope == null) {
            throw new BadRequestException("The invalid team scope has already been removed.");
        }
        workspaceAccountTeamScopeMapper.deleteById(scope.getId());
        workspaceOperationLogService.log(
            authContextService.currentActor("system"),
            "Cleanup validation issue",
            "workspace_account_team_scope",
            scope.getId(),
            "Action=" + ACTION_DELETE_INVALID_TEAM_SCOPE + ", rule=" + issue.getRuleCode()
        );
        return List.of(scope.getId());
    }

    private List<Long> applyDeleteOrphanAssignment(WorkspaceValidationIssueDto issue) {
        Long assignmentId = requireRemediationRecordId(issue);
        RosterAssignmentEntity assignment = rosterAssignmentMapper.selectById(assignmentId);
        if (assignment == null) {
            throw new BadRequestException("The orphan assignment has already been removed.");
        }
        rosterAssignmentMapper.deleteById(assignment.getId());
        workspaceOperationLogService.log(
            authContextService.currentActor("system"),
            "Cleanup validation issue",
            "workspace_roster_assignment",
            assignment.getId(),
            "Action=" + ACTION_DELETE_ORPHAN_ASSIGNMENT + ", rule=" + issue.getRuleCode()
        );
        return List.of(assignment.getId());
    }

    private Long requireRemediationRecordId(WorkspaceValidationIssueDto issue) {
        Long recordId = issue.getRemediation() == null ? null : issue.getRemediation().getRecordId();
        if (recordId == null) {
            throw new BadRequestException("Validation remediation record is unavailable.");
        }
        return recordId;
    }

    private String buildOrphanAssignmentDescription(
            RosterAssignmentEntity assignment,
            TeamEntity team,
            StaffEntity staff,
            ShiftDefinitionEntity shiftDefinition,
            boolean invalidShiftTeamMapping) {
        List<String> missingReferences = new ArrayList<>();
        if (staff == null) {
            missingReferences.add("staff");
        }
        if (team == null || assignment.getTeamId() == null) {
            missingReferences.add("team");
        }
        if (shiftDefinition == null) {
            missingReferences.add("shift definition");
        } else if (invalidShiftTeamMapping) {
            missingReferences.add("shift-team mapping");
        }
        if (missingReferences.isEmpty()) {
            return "Assignment contains an invalid historical reference and should be removed.";
        }
        return "Assignment contains invalid " + String.join(", ", missingReferences) + " reference(s) and can be safely removed.";
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
            .forEach(issue -> accumulator.record(
                issue.getId(),
                issue.getSeverity(),
                "import.unresolved." + toRuleCodeSegment(issue.getIssueType()),
                DOMAIN_IMPORT,
                false,
                issue.getIssueType(),
                issue.getDescription(),
                issue.getTeamName(),
                issue.getIssueDate() == null ? "-" : issue.getIssueDate().format(DATE_FORMATTER),
                TARGET_PAGE_IMPORT,
                true,
                RESOLUTION_KIND_IMPORT_ISSUE,
                null
            ));
    }

    private Set<Long> resolveDefinitionTeamIds(ShiftDefinitionEntity definition, Map<Long, Set<Long>> teamIdsByDefinitionId) {
        Set<Long> teamIds = new LinkedHashSet<>(teamIdsByDefinitionId.getOrDefault(definition.getId(), Set.of()));
        if (definition.getTeamId() != null) {
            teamIds.add(definition.getTeamId());
        }
        return teamIds;
    }

    private boolean isActiveStaff(StaffEntity staff) {
        return staff != null && (staff.getStatus() == null || staff.getStatus().isBlank() || "active".equalsIgnoreCase(staff.getStatus()));
    }

    private boolean shouldExposeAdminCleanupIssues() {
        return authContextService.isLoggedIn() && authContextService.requireLogin().isAdmin();
    }

    private String safeStatus(String status) {
        return status == null || status.isBlank() ? "inactive" : status;
    }

    private String toRuleCodeSegment(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return "unknown";
        }
        return rawValue.trim()
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("(^-+|-+$)", "");
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
        private final AtomicLong syntheticIdGenerator = new AtomicLong(-1L);
        private final List<WorkspaceValidationIssueDto> issues;
        private long high;
        private long medium;
        private long low;
        private long total;
        private long blocking;
        private WorkspaceValidationIssueDto topIssue;

        private ValidationAccumulator(boolean collectIssues, Set<Long> readableTeamIds, Map<String, Long> teamIdByName) {
            this.collectIssues = collectIssues;
            this.readableTeamIds = readableTeamIds;
            this.teamIdByName = teamIdByName;
            this.issues = collectIssues ? new ArrayList<>() : List.of();
        }

        private long nextSyntheticId() {
            return syntheticIdGenerator.getAndDecrement();
        }

        private void record(Long id,
                String severity,
                String ruleCode,
                String domain,
                boolean blocking,
                String type,
                String description,
                String team,
                String date,
                String targetPage,
                boolean resolvable,
                String resolutionKind) {
            record(id, severity, ruleCode, domain, blocking, type, description, team, date, targetPage, resolvable, resolutionKind, null);
        }

        private void record(Long id,
                String severity,
                String ruleCode,
                String domain,
                boolean blocking,
                String type,
                String description,
                String team,
                String date,
                String targetPage,
                boolean resolvable,
                String resolutionKind,
                String remediationActionKey) {
            record(id, severity, ruleCode, domain, blocking, type, description, team, date, targetPage, resolvable, resolutionKind, remediationActionKey, null);
        }

        private void record(Long id,
                String severity,
                String ruleCode,
                String domain,
                boolean blocking,
                String type,
                String description,
                String team,
                String date,
                String targetPage,
                boolean resolvable,
                String resolutionKind,
                String remediationActionKey,
                Long remediationRecordId) {
            if (!isReadableTeam(team)) {
                return;
            }

            incrementSummary(severity);
            total += 1;
            if (blocking) {
                this.blocking += 1;
            }
            Long teamId = team == null || team.isBlank() || "-".equals(team)
                ? null
                : teamIdByName.get(normalizeTeamName(team));

            WorkspaceValidationIssueDto issue = new WorkspaceValidationIssueDto(
                id,
                teamId,
                severity,
                ruleCode,
                domain,
                blocking,
                type,
                description,
                team,
                date,
                targetPage,
                resolvable,
                resolutionKind,
                buildRemediation(remediationActionKey, remediationRecordId)
            );

            if (collectIssues) {
                issues.add(issue);
            }

            if (shouldPromoteToTopIssue(issue) && isHigherPriority(issue, topIssue)) {
                topIssue = issue;
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

        private boolean shouldPromoteToTopIssue(WorkspaceValidationIssueDto issue) {
            return DOMAIN_ROSTER.equals(issue.getDomain()) && Boolean.TRUE.equals(issue.getBlocking());
        }

        private boolean isHigherPriority(WorkspaceValidationIssueDto candidate, WorkspaceValidationIssueDto current) {
            if (current == null) {
                return true;
            }

            int severityCompare = Integer.compare(severityWeight(candidate.getSeverity()), severityWeight(current.getSeverity()));
            if (severityCompare != 0) {
                return severityCompare < 0;
            }

            return String.valueOf(candidate.getId()).compareTo(String.valueOf(current.getId())) < 0;
        }

        private int severityWeight(String severity) {
            if ("high".equalsIgnoreCase(severity)) {
                return 0;
            }
            if ("medium".equalsIgnoreCase(severity)) {
                return 1;
            }
            return 2;
        }

        private WorkspaceValidationResponse toResponse() {
            return new WorkspaceValidationResponse(
                new WorkspaceValidationSummaryDto(high, medium, low, total, blocking),
                issues,
                topIssue
            );
        }

        private WorkspaceValidationRemediationDto buildRemediation(String remediationActionKey, Long remediationRecordId) {
            if (remediationActionKey == null || remediationActionKey.isBlank()) {
                return null;
            }
            return switch (remediationActionKey) {
                case ACTION_DELETE_INVALID_TEAM_SCOPE -> new WorkspaceValidationRemediationDto(
                    REMEDIATION_TYPE_DELETE_RECORDS,
                    ACTION_DELETE_INVALID_TEAM_SCOPE,
                    "Delete invalid scope",
                    REMEDIATION_ROLE_ADMIN,
                    true,
                    true,
                    remediationRecordId
                );
                case ACTION_DELETE_ORPHAN_ASSIGNMENT -> new WorkspaceValidationRemediationDto(
                    REMEDIATION_TYPE_DELETE_RECORDS,
                    ACTION_DELETE_ORPHAN_ASSIGNMENT,
                    "Delete orphan assignment",
                    REMEDIATION_ROLE_ADMIN,
                    true,
                    true,
                    remediationRecordId
                );
                default -> null;
            };
        }
    }
}
