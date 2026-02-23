package com.support.server.supportrosterserver.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.support.server.supportrosterserver.dto.RoleGroupDto;
import com.support.server.supportrosterserver.entity.RoleGroup;
import com.support.server.supportrosterserver.repository.RosterRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RoleGroupService {

    private final RosterRepository rosterRepository;

    public List<RoleGroupDto> getAllRoleGroups() {
        return rosterRepository.findAllRoleGroups().stream()
            .map(this::convertToDto)
            .collect(Collectors.toList());
    }

    public RoleGroupDto getRoleGroupById(String id) {
        RoleGroup roleGroup = rosterRepository.findRoleGroupById(id);
        if (roleGroup == null) {
            return null;
        }
        return convertToDto(roleGroup);
    }

    private RoleGroupDto convertToDto(RoleGroup roleGroup) {
        return new RoleGroupDto(
            roleGroup.getId(),
            roleGroup.getName(),
            roleGroup.getCategory(),
            roleGroup.getRegion()
        );
    }
}
