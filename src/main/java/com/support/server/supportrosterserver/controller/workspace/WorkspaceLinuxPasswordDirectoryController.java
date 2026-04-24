package com.support.server.supportrosterserver.controller.workspace;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.support.server.supportrosterserver.service.workspace.WorkspaceLinuxPasswordService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/workspace/linux-password-directories")
@RequiredArgsConstructor
public class WorkspaceLinuxPasswordDirectoryController {

    private final WorkspaceLinuxPasswordService workspaceLinuxPasswordService;

    @GetMapping
    public ResponseEntity<List<String>> listDirectories() {
        return ResponseEntity.ok(workspaceLinuxPasswordService.listDirectories());
    }
}
