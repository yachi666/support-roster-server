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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.support.server.supportrosterserver.dto.workspace.WorkspaceShiftDefinitionDto;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceShiftDefinitionReorderRequest;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceShiftDefinitionUpsertRequest;
import com.support.server.supportrosterserver.service.workspace.WorkspaceShiftDefinitionService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/workspace/shift-definitions")
@RequiredArgsConstructor
public class WorkspaceShiftDefinitionController {

    private final WorkspaceShiftDefinitionService workspaceShiftDefinitionService;

    @GetMapping
    public ResponseEntity<List<WorkspaceShiftDefinitionDto>> listShiftDefinitions(@RequestParam(required = false) String keyword) {
        return ResponseEntity.ok(workspaceShiftDefinitionService.listShiftDefinitions(keyword));
    }

    @GetMapping("/{id}")
    public ResponseEntity<WorkspaceShiftDefinitionDto> getShiftDefinition(@PathVariable Long id) {
        return ResponseEntity.ok(workspaceShiftDefinitionService.getShiftDefinition(id));
    }

    @PostMapping
    public ResponseEntity<WorkspaceShiftDefinitionDto> createShiftDefinition(@Valid @RequestBody WorkspaceShiftDefinitionUpsertRequest request) {
        return ResponseEntity.ok(workspaceShiftDefinitionService.createShiftDefinition(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<WorkspaceShiftDefinitionDto> updateShiftDefinition(@PathVariable Long id, @Valid @RequestBody WorkspaceShiftDefinitionUpsertRequest request) {
        return ResponseEntity.ok(workspaceShiftDefinitionService.updateShiftDefinition(id, request));
    }

    @PostMapping("/reorder")
    public ResponseEntity<Void> reorderShiftDefinitions(@Valid @RequestBody WorkspaceShiftDefinitionReorderRequest request) {
        workspaceShiftDefinitionService.reorderShiftDefinitionsForTeam(request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteShiftDefinition(@PathVariable Long id) {
        workspaceShiftDefinitionService.deleteShiftDefinition(id);
        return ResponseEntity.noContent().build();
    }
}
