package com.support.server.supportrosterserver.dto.workspace;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class WorkspaceLinuxPasswordUpsertRequest {
    @NotBlank(message = "Hostname is required.")
    private String hostname;

    @NotBlank(message = "IP address is required.")
    private String ip;

    @NotBlank(message = "Username is required.")
    private String username;

    @NotBlank(message = "Password is required.")
    private String password;

    private List<String> businessUnits;

    private String status;
}
