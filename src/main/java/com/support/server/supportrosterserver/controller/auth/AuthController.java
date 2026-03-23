package com.support.server.supportrosterserver.controller.auth;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.support.server.supportrosterserver.dto.auth.AuthChangePasswordRequest;
import com.support.server.supportrosterserver.dto.auth.AuthCurrentUserDto;
import com.support.server.supportrosterserver.dto.auth.AuthLoginRequest;
import com.support.server.supportrosterserver.dto.auth.AuthLoginResponse;
import com.support.server.supportrosterserver.service.auth.AuthService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<AuthLoginResponse> login(@Valid @RequestBody AuthLoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        authService.logout();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<AuthCurrentUserDto> currentUser() {
        return ResponseEntity.ok(authService.getCurrentUser());
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody AuthChangePasswordRequest request) {
        authService.changePassword(request);
        return ResponseEntity.noContent().build();
    }
}
