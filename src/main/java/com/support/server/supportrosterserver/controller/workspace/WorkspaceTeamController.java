package com.support.server.supportrosterserver.controller.workspace;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.support.server.supportrosterserver.dto.workspace.WorkspaceTeamDto;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceTeamUpsertRequest;
import com.support.server.supportrosterserver.service.workspace.WorkspaceTeamService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/workspace/teams")
@RequiredArgsConstructor
public class WorkspaceTeamController {

    private final WorkspaceTeamService workspaceTeamService;

    @GetMapping
    public ResponseEntity<List<WorkspaceTeamDto>> listTeams() {
        return ResponseEntity.ok(workspaceTeamService.listTeams());
    }

    @GetMapping("/{id}")
    public ResponseEntity<WorkspaceTeamDto> getTeam(@PathVariable Long id) {
        return ResponseEntity.ok(workspaceTeamService.getTeam(id));
    }

    @PostMapping
    public ResponseEntity<WorkspaceTeamDto> createTeam(@Valid @RequestBody WorkspaceTeamUpsertRequest request) {
        return ResponseEntity.ok(workspaceTeamService.createTeam(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<WorkspaceTeamDto> updateTeam(@PathVariable Long id, @Valid @RequestBody WorkspaceTeamUpsertRequest request) {
        return ResponseEntity.ok(workspaceTeamService.updateTeam(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTeam(@PathVariable Long id) {
        workspaceTeamService.deleteTeam(id);
        return ResponseEntity.noContent().build();
    }
}