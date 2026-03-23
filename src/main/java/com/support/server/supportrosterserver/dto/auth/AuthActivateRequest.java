package com.support.server.supportrosterserver.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AuthActivateRequest {
    @NotBlank
    private String staffId;

    @NotBlank
    private String newPassword;
}
