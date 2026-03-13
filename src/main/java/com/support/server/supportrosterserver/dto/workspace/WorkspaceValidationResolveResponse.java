package com.support.server.supportrosterserver.dto.workspace;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceValidationResolveResponse {
    private int resolvedCount;
    private int skippedCount;
    @JsonSerialize(contentUsing = ToStringSerializer.class)
    private List<Long> resolvedIssueIds;
    @JsonSerialize(contentUsing = ToStringSerializer.class)
    private List<Long> skippedIssueIds;
    private WorkspaceValidationResponse validation;
}