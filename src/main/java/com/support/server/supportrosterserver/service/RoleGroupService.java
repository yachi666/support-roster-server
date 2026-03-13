package com.support.server.supportrosterserver.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.support.server.supportrosterserver.dto.RoleGroupDto;
import com.support.server.supportrosterserver.entity.workspace.RoleGroupEntity;
import com.support.server.supportrosterserver.service.workspace.WorkspaceLookupService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RoleGroupService {

    private final WorkspaceLookupService lookupService;

    public List<RoleGroupDto> getAllRoleGroups() {
        return lookupService.listRoleGroups().stream()
            .map(this::convertToDto)
            .toList();
    }

    public RoleGroupDto getRoleGroupById(String id) {
        RoleGroupEntity roleGroup = lookupService.listRoleGroups().stream()
            .filter(item -> id.equals(item.getCode()))
            .findFirst()
            .orElse(null);
        if (roleGroup == null) {
            return null;
        }
        return convertToDto(roleGroup);
    }

    private RoleGroupDto convertToDto(RoleGroupEntity roleGroup) {
        return new RoleGroupDto(
            roleGroup.getCode(),
            roleGroup.getName(),
            roleGroup.getCategory(),
            roleGroup.getRegion()
        );
    }
}
