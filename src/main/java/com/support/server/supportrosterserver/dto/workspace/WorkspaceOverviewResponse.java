package com.support.server.supportrosterserver.dto.workspace;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceOverviewResponse {
    private List<WorkspaceSummaryStatDto> stats;
    private List<WorkspaceActivityDto> activity;
    private List<WorkspaceQuickActionDto> quickActions;
}