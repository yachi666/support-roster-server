package com.support.server.supportrosterserver.dto.workspace;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceValidationResponse {
    private WorkspaceValidationSummaryDto summary;
    private List<WorkspaceValidationIssueDto> issues;
    private WorkspaceValidationIssueDto topIssue;
}
