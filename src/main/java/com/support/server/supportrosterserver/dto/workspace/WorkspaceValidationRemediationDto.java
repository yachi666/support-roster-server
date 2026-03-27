package com.support.server.supportrosterserver.dto.workspace;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceValidationRemediationDto {
    private String type;
    private String actionKey;
    private String label;
    private String requiresRole;
    private Boolean previewable;
    private Boolean destructive;
}
