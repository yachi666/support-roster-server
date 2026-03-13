package com.support.server.supportrosterserver.service.workspace;

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
import com.support.server.supportrosterserver.mapper.OperationLogMapper;
import com.support.server.supportrosterserver.mapper.RosterAssignmentMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WorkspaceOverviewService {

    private final WorkspaceValidationService validationService;
    private final OperationLogMapper operationLogMapper;
    private final RosterAssignmentMapper rosterAssignmentMapper;

    public WorkspaceOverviewResponse getOverview() {
        YearMonth currentMonth = YearMonth.now();
        var validation = validationService.getValidation(currentMonth.getYear(), currentMonth.getMonthValue());
        long totalIssues = validation.getIssues().size();
        long totalAssignments = rosterAssignmentMapper.selectCount(com.baomidou.mybatisplus.core.toolkit.Wrappers.<com.support.server.supportrosterserver.entity.workspace.RosterAssignmentEntity>lambdaQuery()
            .between(com.support.server.supportrosterserver.entity.workspace.RosterAssignmentEntity::getAssignmentDate, currentMonth.atDay(1), currentMonth.atEndOfMonth()));
        int completion = totalAssignments == 0 ? 0 : (int) Math.max(0, 100 - Math.min(100, totalIssues));

        List<WorkspaceSummaryStatDto> stats = List.of(
            new WorkspaceSummaryStatDto("Completion Progress", completion + "%", totalIssues == 0 ? "No issues detected" : totalIssues + " issues detected", completion > 80 ? "good" : "warning", completion),
            new WorkspaceSummaryStatDto("Unresolved Issues", String.valueOf(totalIssues), validation.getSummary().getHigh() + " high severity", totalIssues == 0 ? "good" : "warning", Math.max(0, 100 - (int) totalIssues * 5)),
            new WorkspaceSummaryStatDto("Missing Primary Coverage", String.valueOf(validation.getSummary().getHigh()), "Calculated from live roster", validation.getSummary().getHigh() == 0 ? "good" : "error", Math.max(0, 100 - (int) validation.getSummary().getHigh() * 10)),
            new WorkspaceSummaryStatDto("Draft Shifts", String.valueOf(totalAssignments), "Imported and manual records", "neutral", Math.min(100, (int) totalAssignments))
        );

        List<WorkspaceActivityDto> activity = operationLogMapper.selectList(Wrappers.<OperationLogEntity>lambdaQuery()
                .orderByDesc(OperationLogEntity::getCreateTime)
                .last("limit 8"))
            .stream()
            .map(log -> new WorkspaceActivityDto(log.getActor(), log.getAction(), formatRelative(log.getCreateTime())))
            .toList();

        List<WorkspaceQuickActionDto> quickActions = List.of(
            new WorkspaceQuickActionDto("Export Final Roster", "Download validated schedule", "teal", "export"),
            new WorkspaceQuickActionDto("Review Open Issues", "See validation results", "rose", "validation")
        );
        return new WorkspaceOverviewResponse(stats, activity, quickActions);
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