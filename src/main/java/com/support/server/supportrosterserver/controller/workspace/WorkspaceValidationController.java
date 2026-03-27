package com.support.server.supportrosterserver.controller.workspace;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.support.server.supportrosterserver.dto.workspace.WorkspaceValidationRemediationApplyResponse;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceValidationRemediationPreviewResponse;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceValidationRemediationRequest;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceValidationResponse;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceValidationResolveRequest;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceValidationResolveResponse;
import com.support.server.supportrosterserver.service.workspace.WorkspaceValidationService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/workspace/validation")
@RequiredArgsConstructor
public class WorkspaceValidationController {

    private final WorkspaceValidationService workspaceValidationService;

    @GetMapping
    public ResponseEntity<WorkspaceValidationResponse> getValidation(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false, defaultValue = "false") Boolean summaryOnly) {
        return ResponseEntity.ok(workspaceValidationService.getValidation(year, month, Boolean.TRUE.equals(summaryOnly)));
    }

    @PostMapping("/resolve")
    public ResponseEntity<WorkspaceValidationResolveResponse> resolveIssues(
            @Valid @RequestBody WorkspaceValidationResolveRequest request) {
        return ResponseEntity.ok(workspaceValidationService.resolveIssues(request));
    }

    @PostMapping("/{id}/preview-remediation")
    public ResponseEntity<WorkspaceValidationRemediationPreviewResponse> previewRemediation(
            @PathVariable("id") Long issueId,
            @Valid @RequestBody WorkspaceValidationRemediationRequest request) {
        return ResponseEntity.ok(workspaceValidationService.previewRemediation(issueId, request));
    }

    @PostMapping("/{id}/apply-remediation")
    public ResponseEntity<WorkspaceValidationRemediationApplyResponse> applyRemediation(
            @PathVariable("id") Long issueId,
            @Valid @RequestBody WorkspaceValidationRemediationRequest request) {
        return ResponseEntity.ok(workspaceValidationService.applyRemediation(issueId, request));
    }
}
