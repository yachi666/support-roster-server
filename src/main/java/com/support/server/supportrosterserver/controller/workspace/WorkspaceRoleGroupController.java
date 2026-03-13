package com.support.server.supportrosterserver.controller.workspace;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.support.server.supportrosterserver.dto.workspace.WorkspaceRoleGroupDto;
import com.support.server.supportrosterserver.service.workspace.WorkspaceRoleGroupService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/workspace/role-groups")
@RequiredArgsConstructor
public class WorkspaceRoleGroupController {

    private final WorkspaceRoleGroupService workspaceRoleGroupService;

    @GetMapping
    public ResponseEntity<List<WorkspaceRoleGroupDto>> listRoleGroups() {
        return ResponseEntity.ok(workspaceRoleGroupService.listRoleGroups());
    }
}