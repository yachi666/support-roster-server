package com.support.server.supportrosterserver.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.support.server.supportrosterserver.dto.RoleGroupDto;
import com.support.server.supportrosterserver.service.RoleGroupService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/role-groups")
@RequiredArgsConstructor
public class RoleGroupController {

    private final RoleGroupService roleGroupService;

    @GetMapping
    public ResponseEntity<List<RoleGroupDto>> getAllRoleGroups() {
        return ResponseEntity.ok(roleGroupService.getAllRoleGroups());
    }
}
