package com.support.server.supportrosterserver.service.workspace;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceRoleGroupDto;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceTeamDto;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceTeamUpsertRequest;
import com.support.server.supportrosterserver.entity.workspace.RoleGroupEntity;
import com.support.server.supportrosterserver.entity.workspace.TeamEntity;
import com.support.server.supportrosterserver.mapper.TeamMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WorkspaceTeamService {

    private final TeamMapper teamMapper;
    private final WorkspaceLookupService lookupService;

    public List<WorkspaceTeamDto> listTeams() {
        Map<Long, RoleGroupEntity> roleGroupMap = lookupService.roleGroupMap();
        Map<Long, List<Long>> teamRoleGroups = lookupService.teamRoleGroupIdsMap();
        return lookupService.listTeams().stream()
            .map(team -> toDto(team, teamRoleGroups.getOrDefault(team.getId(), List.of()), roleGroupMap))
            .toList();
    }

    @Transactional
    public WorkspaceTeamDto createTeam(WorkspaceTeamUpsertRequest request) {
        TeamEntity entity = new TeamEntity();
        apply(entity, request);
        teamMapper.insert(entity);
        lookupService.replaceTeamRoleGroups(entity.getId(), request.getRoleGroupIds());
        return getTeam(entity.getId());
    }

    @Transactional
    public WorkspaceTeamDto updateTeam(Long id, WorkspaceTeamUpsertRequest request) {
        TeamEntity entity = lookupService.requireTeam(id);
        apply(entity, request);
        teamMapper.updateById(entity);
        lookupService.replaceTeamRoleGroups(entity.getId(), request.getRoleGroupIds());
        return getTeam(id);
    }

    public WorkspaceTeamDto getTeam(Long id) {
        Map<Long, RoleGroupEntity> roleGroupMap = lookupService.roleGroupMap();
        Map<Long, List<Long>> teamRoleGroups = lookupService.teamRoleGroupIdsMap();
        return toDto(lookupService.requireTeam(id), teamRoleGroups.getOrDefault(id, List.of()), roleGroupMap);
    }

    @Transactional
    public void deleteTeam(Long id) {
        lookupService.requireTeam(id);
        lookupService.replaceTeamRoleGroups(id, List.of());
        teamMapper.deleteById(id);
    }

    public List<com.support.server.supportrosterserver.dto.TeamDto> listViewerTeams() {
        return lookupService.listTeams().stream()
            .filter(team -> Boolean.TRUE.equals(team.getVisible()))
            .map(team -> new com.support.server.supportrosterserver.dto.TeamDto(
                team.getTeamCode(),
                team.getName(),
                team.getColor(),
                team.getDisplayOrder()
            ))
            .toList();
    }

    private WorkspaceTeamDto toDto(TeamEntity team, List<Long> roleGroupIds, Map<Long, RoleGroupEntity> roleGroupMap) {
        List<WorkspaceRoleGroupDto> roleGroups = new ArrayList<>();
        for (Long roleGroupId : roleGroupIds) {
            RoleGroupEntity roleGroup = roleGroupMap.get(roleGroupId);
            if (roleGroup != null) {
                roleGroups.add(lookupService.toRoleGroupDto(roleGroup));
            }
        }
        return new WorkspaceTeamDto(
            team.getId(),
            team.getTeamCode(),
            team.getName(),
            team.getColor(),
            team.getDisplayOrder(),
            team.getVisible(),
            team.getDescription(),
            roleGroups
        );
    }

    private void apply(TeamEntity entity, WorkspaceTeamUpsertRequest request) {
        entity.setTeamCode(request.getTeamCode());
        entity.setName(request.getName());
        entity.setColor(request.getColor());
        entity.setDisplayOrder(request.getDisplayOrder());
        entity.setVisible(request.getVisible());
        entity.setDescription(request.getDescription());
    }
}