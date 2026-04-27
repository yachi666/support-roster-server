package com.support.server.supportrosterserver.dto.workspace;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

@Data
public class WorkspaceLinuxPasswordUpsertRequest {
    @NotBlank(message = "Hostname is required.")
    private String hostname;

    @NotBlank(message = "IP address is required.")
    private String ip;

    private String username;

    private String password;

    private List<CredentialRequest> credentials;

    private List<String> businessUnits;

    private String status;

    @Data
    public static class CredentialRequest {
        @JsonSerialize(using = ToStringSerializer.class)
        private Long id;

        @NotBlank(message = "Username is required.")
        private String username;

        @NotBlank(message = "Password is required.")
        private String password;

        private String notes;
    }
}
