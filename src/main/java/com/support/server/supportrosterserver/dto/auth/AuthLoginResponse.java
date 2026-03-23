package com.support.server.supportrosterserver.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthLoginResponse {
    private String token;
    private AuthCurrentUserDto currentUser;
}
