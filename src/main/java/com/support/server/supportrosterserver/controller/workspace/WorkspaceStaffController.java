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

import com.support.server.supportrosterserver.dto.workspace.WorkspaceStaffDto;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceStaffBatchCreateRequest;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceStaffUpsertRequest;
import com.support.server.supportrosterserver.service.workspace.WorkspaceStaffService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/workspace/staff")
@RequiredArgsConstructor
public class WorkspaceStaffController {

    private final WorkspaceStaffService workspaceStaffService;

    @GetMapping
    public ResponseEntity<List<WorkspaceStaffDto>> listStaff(@RequestParam(required = false) String keyword) {
        return ResponseEntity.ok(workspaceStaffService.listStaff(keyword));
    }

    @GetMapping("/{id}")
    public ResponseEntity<WorkspaceStaffDto> getStaff(@PathVariable Long id) {
        return ResponseEntity.ok(workspaceStaffService.getStaff(id));
    }

    @PostMapping
    public ResponseEntity<WorkspaceStaffDto> createStaff(@Valid @RequestBody WorkspaceStaffUpsertRequest request) {
        return ResponseEntity.ok(workspaceStaffService.createStaff(request));
    }

    @PostMapping("/batch")
    public ResponseEntity<List<WorkspaceStaffDto>> createStaffBatch(@Valid @RequestBody WorkspaceStaffBatchCreateRequest request) {
        return ResponseEntity.ok(workspaceStaffService.createStaffBatch(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<WorkspaceStaffDto> updateStaff(@PathVariable Long id, @Valid @RequestBody WorkspaceStaffUpsertRequest request) {
        return ResponseEntity.ok(workspaceStaffService.updateStaff(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteStaff(@PathVariable Long id) {
        workspaceStaffService.deleteStaff(id);
        return ResponseEntity.noContent().build();
    }
}
