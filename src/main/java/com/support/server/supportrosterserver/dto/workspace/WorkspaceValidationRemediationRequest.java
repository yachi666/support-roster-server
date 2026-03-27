package com.support.server.supportrosterserver.dto.workspace;

import jakarta.validation.constraints.NotBlank;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceValidationRemediationRequest {
    private Integer year;
    private Integer month;

    @NotBlank
    private String actionKey;
}
