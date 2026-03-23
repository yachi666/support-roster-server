package com.support.server.supportrosterserver.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AuthLoginRequest {
    @NotBlank
    private String staffId;

    private String password;

    private String newPassword;
}
