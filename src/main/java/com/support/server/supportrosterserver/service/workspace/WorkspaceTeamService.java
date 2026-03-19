package com.support.server.supportrosterserver.service.workspace;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceTeamDto;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceTeamUpsertRequest;
import com.support.server.supportrosterserver.entity.workspace.TeamEntity;
import com.support.server.supportrosterserver.mapper.TeamMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WorkspaceTeamService {

    private final TeamMapper teamMapper;
    private final WorkspaceLookupService lookupService;

    public List<WorkspaceTeamDto> listTeams() {
        return lookupService.listTeams().stream()
            .map(this::toDto)
            .toList();
    }

    @Transactional
    public WorkspaceTeamDto createTeam(WorkspaceTeamUpsertRequest request) {
        TeamEntity entity = new TeamEntity();
        apply(entity, request);
        teamMapper.insert(entity);
        return getTeam(entity.getId());
    }

    @Transactional
    public WorkspaceTeamDto updateTeam(Long id, WorkspaceTeamUpsertRequest request) {
        TeamEntity entity = lookupService.requireTeam(id);
        apply(entity, request);
        teamMapper.updateById(entity);
        return getTeam(id);
    }

    public WorkspaceTeamDto getTeam(Long id) {
        return toDto(lookupService.requireTeam(id));
    }

    @Transactional
    public void deleteTeam(Long id) {
        lookupService.requireTeam(id);
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

    private WorkspaceTeamDto toDto(TeamEntity team) {
        return new WorkspaceTeamDto(
            team.getId(),
            team.getTeamCode(),
            team.getName(),
            team.getColor(),
            team.getDisplayOrder(),
            team.getVisible(),
            team.getDescription()
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