package com.support.server.supportrosterserver.dto.workspace;

import jakarta.validation.constraints.NotBlank;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class WorkspaceValidationRemediationRequest {
    private Integer year;
    private Integer month;

    @NotBlank
    private String actionKey;

    private Long recordId;

    public WorkspaceValidationRemediationRequest(Integer year, Integer month, String actionKey) {
        this(year, month, actionKey, null);
    }

    public WorkspaceValidationRemediationRequest(Integer year, Integer month, String actionKey, Long recordId) {
        this.year = year;
        this.month = month;
        this.actionKey = actionKey;
        this.recordId = recordId;
    }
}
