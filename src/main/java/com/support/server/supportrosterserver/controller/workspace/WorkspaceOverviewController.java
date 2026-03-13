package com.support.server.supportrosterserver.controller.workspace;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.support.server.supportrosterserver.dto.workspace.WorkspaceOverviewResponse;
import com.support.server.supportrosterserver.service.workspace.WorkspaceOverviewService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/workspace/overview")
@RequiredArgsConstructor
public class WorkspaceOverviewController {

    private final WorkspaceOverviewService workspaceOverviewService;

    @GetMapping
    public ResponseEntity<WorkspaceOverviewResponse> getOverview() {
        return ResponseEntity.ok(workspaceOverviewService.getOverview());
    }
}