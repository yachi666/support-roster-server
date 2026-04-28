package com.support.server.supportrosterserver.controller.workspace;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.support.server.supportrosterserver.config.TrustedProxyProperties;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceLinuxPasswordDto;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceLinuxPasswordAccessAuditListResponse;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceLinuxPasswordListResponse;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceLinuxPasswordSecretRequest;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceLinuxPasswordSecretResponse;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceLinuxPasswordUpsertRequest;
import com.support.server.supportrosterserver.service.workspace.WorkspaceLinuxPasswordService;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/workspace/linux-passwords")
@RequiredArgsConstructor
public class WorkspaceLinuxPasswordController {

    private final WorkspaceLinuxPasswordService workspaceLinuxPasswordService;
    private final TrustedProxyProperties trustedProxyProperties;

    @GetMapping
    public ResponseEntity<WorkspaceLinuxPasswordListResponse> listServers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String businessUnit) {
        return ResponseEntity.ok(workspaceLinuxPasswordService.listServers(search, businessUnit));
    }

    @GetMapping("/{id}")
    public ResponseEntity<WorkspaceLinuxPasswordDto> getServer(@PathVariable Long id) {
        return ResponseEntity.ok(workspaceLinuxPasswordService.getServer(id));
    }

    @GetMapping("/access-audits")
    public ResponseEntity<WorkspaceLinuxPasswordAccessAuditListResponse> listAccessAudits(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String staffId,
            @RequestParam(required = false) String staffName,
            @RequestParam(required = false) String hostname,
            @RequestParam(required = false) String ip,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long pageSize) {
        return ResponseEntity.ok(workspaceLinuxPasswordService.listAccessAudits(
            keyword,
            staffId,
            staffName,
            hostname,
            ip,
            username,
            action,
            result,
            from,
            to,
            page,
            pageSize
        ));
    }

    @PostMapping("/credentials/{credentialId}/secret")
    public ResponseEntity<WorkspaceLinuxPasswordSecretResponse> revealCredentialSecret(
            @PathVariable Long credentialId,
            @Valid @RequestBody WorkspaceLinuxPasswordSecretRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(workspaceLinuxPasswordService.revealCredentialSecret(
            credentialId,
            request.getAction(),
            resolveClientIp(httpRequest),
            httpRequest.getHeader("User-Agent")
        ));
    }

    @PostMapping
    public ResponseEntity<WorkspaceLinuxPasswordDto> createServer(@Valid @RequestBody WorkspaceLinuxPasswordUpsertRequest request) {
        return ResponseEntity.ok(workspaceLinuxPasswordService.createServer(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<WorkspaceLinuxPasswordDto> updateServer(@PathVariable Long id, @Valid @RequestBody WorkspaceLinuxPasswordUpsertRequest request) {
        return ResponseEntity.ok(workspaceLinuxPasswordService.updateServer(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteServer(@PathVariable Long id) {
        workspaceLinuxPasswordService.deleteServer(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Resolves the real client IP address. X-Forwarded-For is only trusted when
     * the direct connection comes from a configured trusted proxy IP.
     */
    String resolveClientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        if (isTrustedProxy(remoteAddr)) {
            String forwardedFor = request.getHeader("X-Forwarded-For");
            if (forwardedFor != null && !forwardedFor.isBlank()) {
                return forwardedFor.split(",")[0].trim();
            }
        }
        return remoteAddr;
    }

    private boolean isTrustedProxy(String remoteAddr) {
        if (remoteAddr == null) {
            return false;
        }
        return trustedProxyProperties.getIps().contains(remoteAddr.trim());
    }
}
