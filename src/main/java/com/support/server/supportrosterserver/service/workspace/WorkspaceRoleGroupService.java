package com.support.server.supportrosterserver.service.workspace;

import java.util.List;

import org.springframework.stereotype.Service;

import com.support.server.supportrosterserver.dto.workspace.WorkspaceRoleGroupDto;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WorkspaceRoleGroupService {

    private final WorkspaceLookupService lookupService;

    public List<WorkspaceRoleGroupDto> listRoleGroups() {
        return lookupService.listRoleGroups().stream()
            .map(lookupService::toRoleGroupDto)
            .toList();
    }
}