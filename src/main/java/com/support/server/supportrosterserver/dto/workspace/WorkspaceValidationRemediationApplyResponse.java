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
public class WorkspaceValidationRemediationApplyResponse {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long issueId;
    private String actionKey;
    private int appliedCount;
    @JsonSerialize(contentUsing = ToStringSerializer.class)
    private List<Long> affectedRecordIds;
    private WorkspaceValidationResponse validation;
}
