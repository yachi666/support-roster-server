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

import com.support.server.supportrosterserver.dto.workspace.WorkspaceLinuxPasswordDto;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceLinuxPasswordListResponse;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceLinuxPasswordUpsertRequest;
import com.support.server.supportrosterserver.service.workspace.WorkspaceLinuxPasswordService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/workspace/linux-passwords")
@RequiredArgsConstructor
public class WorkspaceLinuxPasswordController {

    private final WorkspaceLinuxPasswordService workspaceLinuxPasswordService;

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
}
