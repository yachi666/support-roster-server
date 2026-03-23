package com.support.server.supportrosterserver.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AuthChangePasswordRequest {
    @NotBlank
    private String currentPassword;

    @NotBlank
    private String newPassword;
}
