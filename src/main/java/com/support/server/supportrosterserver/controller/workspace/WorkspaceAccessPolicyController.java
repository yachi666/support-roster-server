package com.support.server.supportrosterserver.controller.workspace;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.support.server.supportrosterserver.dto.workspace.WorkspaceAccessPolicyResponse;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceAccessPolicyUpdateRequest;
import com.support.server.supportrosterserver.service.workspace.WorkspaceAccessPolicyService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/workspace/access-policy")
@RequiredArgsConstructor
public class WorkspaceAccessPolicyController {

    private final WorkspaceAccessPolicyService workspaceAccessPolicyService;

    @GetMapping
    public ResponseEntity<WorkspaceAccessPolicyResponse> getAccessPolicy() {
        return ResponseEntity.ok(workspaceAccessPolicyService.getAccessPolicy());
    }

    @PutMapping
    public ResponseEntity<WorkspaceAccessPolicyResponse> updateAccessPolicy(
            @Valid @RequestBody WorkspaceAccessPolicyUpdateRequest request) {
        return ResponseEntity.ok(workspaceAccessPolicyService.updateAccessPolicy(request));
    }
}
