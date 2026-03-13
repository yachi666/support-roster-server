package com.support.server.supportrosterserver.controller.workspace;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.support.server.supportrosterserver.dto.workspace.WorkspaceMonthlyRosterResponse;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceRosterSaveRequest;
import com.support.server.supportrosterserver.service.workspace.WorkspaceRosterService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/workspace/roster")
@RequiredArgsConstructor
public class WorkspaceRosterController {

    private final WorkspaceRosterService workspaceRosterService;

    @GetMapping
    public ResponseEntity<WorkspaceMonthlyRosterResponse> getMonthlyRoster(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        return ResponseEntity.ok(workspaceRosterService.getMonthlyRoster(year, month));
    }

    @PostMapping("/save")
    public ResponseEntity<WorkspaceMonthlyRosterResponse> saveMonthlyRoster(@Valid @RequestBody WorkspaceRosterSaveRequest request) {
        return ResponseEntity.ok(workspaceRosterService.saveMonthlyRoster(request));
    }
}