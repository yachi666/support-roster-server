package com.support.server.supportrosterserver.service.workspace;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceActivityDto;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceOverviewResponse;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceQuickActionDto;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceSummaryStatDto;
import com.support.server.supportrosterserver.entity.workspace.OperationLogEntity;
import com.support.server.supportrosterserver.exception.BadRequestException;
import com.support.server.supportrosterserver.mapper.OperationLogMapper;
import com.support.server.supportrosterserver.mapper.RosterAssignmentMapper;
import com.support.server.supportrosterserver.service.auth.AuthContextService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WorkspaceOverviewService {

    private final WorkspaceValidationService validationService;
    private final OperationLogMapper operationLogMapper;
    private final RosterAssignmentMapper rosterAssignmentMapper;
    private final AuthContextService authContextService;

    public WorkspaceOverviewResponse getOverview(Integer year, Integer month) {
        YearMonth targetMonth = resolveMonth(year, month);
        var validation = validationService.getValidation(targetMonth.getYear(), targetMonth.getMonthValue());
        long totalIssues = validation.getSummary().getTotal();
        long blockingIssues = validation.getSummary().getBlocking();
        var assignmentQuery = com.baomidou.mybatisplus.core.toolkit.Wrappers.<com.support.server.supportrosterserver.entity.workspace.RosterAssignmentEntity>lambdaQuery()
            .between(com.support.server.supportrosterserver.entity.workspace.RosterAssignmentEntity::getAssignmentDate, targetMonth.atDay(1), targetMonth.atEndOfMonth());
        List<Long> readableTeamIds = authContextService.readableTeamIds();
        if (!readableTeamIds.isEmpty()) {
            assignmentQuery.in(com.support.server.supportrosterserver.entity.workspace.RosterAssignmentEntity::getTeamId, readableTeamIds);
        }
        long totalAssignments = rosterAssignmentMapper.selectCount(assignmentQuery);
        int completion = totalAssignments == 0
            ? 0
            : calculateReadinessScore(validation.getSummary().getHigh(), validation.getSummary().getMedium(), validation.getSummary().getLow(), blockingIssues);

        List<WorkspaceSummaryStatDto> stats = List.of(
            new WorkspaceSummaryStatDto(
                "Roster Completion",
                completion + "%",
                blockingIssues > 0
                    ? blockingIssues + " blocking roster risk(s) are reducing readiness"
                    : totalIssues == 0
                        ? "No blocking issues detected for this month"
                        : "No blocking roster risks; follow-up issues remain for admins",
                blockingIssues == 0 ? "good" : "warning",
                completion
            ),
            new WorkspaceSummaryStatDto(
                "Validation Watch",
                String.valueOf(blockingIssues),
                blockingIssues > 0
                    ? blockingIssues + " blocking roster issue(s) remain open"
                    : totalIssues + " non-blocking issue(s) remain open",
                blockingIssues == 0 ? "good" : "warning",
                Math.max(0, 100 - (int) Math.min(100, blockingIssues * 20))
            ),
            new WorkspaceSummaryStatDto("Scheduled Assignments", String.valueOf(totalAssignments), "Imported and manual roster entries captured for the selected month", "neutral", Math.min(100, (int) totalAssignments))
        );

        List<WorkspaceActivityDto> activity = authContextService.isLoggedIn()
            ? operationLogMapper.selectList(Wrappers.<OperationLogEntity>lambdaQuery()
                    .orderByDesc(OperationLogEntity::getCreateTime)
                    .last("limit 8"))
                .stream()
                .map(log -> new WorkspaceActivityDto(log.getActor(), log.getAction(), formatRelative(log.getCreateTime())))
                .toList()
            : List.of();

        List<WorkspaceQuickActionDto> quickActions = List.of(
            new WorkspaceQuickActionDto("Open Monthly Roster", "Continue editing the selected month", "teal", "roster"),
            new WorkspaceQuickActionDto("Import Monthly Updates", "Preview workbook changes for this month", "teal", "import"),
            new WorkspaceQuickActionDto("Export Final Roster", "Download the current month schedule", "teal", "export"),
            new WorkspaceQuickActionDto("Review Open Issues", "Escalate only the items that need validation follow-up", "rose", "validation")
        );
        return new WorkspaceOverviewResponse(stats, activity, quickActions);
    }

    private int calculateReadinessScore(long high, long medium, long low, long blocking) {
        int score = 100;
        score -= Math.min(80, (int) blocking * 25);
        score -= Math.min(15, (int) medium * 3);
        score -= Math.min(5, (int) low);
        if (high > blocking) {
            score -= Math.min(10, (int) (high - blocking) * 5);
        }
        return Math.max(0, score);
    }

    private YearMonth resolveMonth(Integer year, Integer month) {
        if (year == null || month == null) {
            return YearMonth.now();
        }

        try {
            return YearMonth.of(year, month);
        } catch (DateTimeException ex) {
            throw new BadRequestException("Invalid overview month.");
        }
    }

    private String formatRelative(LocalDateTime time) {
        if (time == null) {
            return "-";
        }
        Duration duration = Duration.between(time, LocalDateTime.now());
        long minutes = Math.max(1, duration.toMinutes());
        if (minutes < 60) {
            return minutes + " mins ago";
        }
        long hours = duration.toHours();
        if (hours < 24) {
            return hours + " hrs ago";
        }
        return duration.toDays() + " days ago";
    }
}
