package com.support.server.supportrosterserver.dto.workspace;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class WorkspaceLinuxPasswordSecretRequest {
    @NotBlank(message = "Password access action is required.")
    private String action;
}
