package com.support.server.supportrosterserver.service.workspace;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceTeamDto;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceTeamUpsertRequest;
import com.support.server.supportrosterserver.entity.workspace.TeamEntity;
import com.support.server.supportrosterserver.exception.BadRequestException;
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

    @Transactional
    public List<WorkspaceTeamDto> reorderTeams(List<Long> teamIds) {
        List<TeamEntity> currentTeams = lookupService.listTeams();

        if (teamIds.size() != currentTeams.size()) {
            throw new BadRequestException("Reorder request must include all teams.");
        }

        Set<Long> requestedIds = new HashSet<>(teamIds);
        if (requestedIds.size() != teamIds.size()) {
            throw new BadRequestException("Reorder request contains duplicate team ids.");
        }

        Map<Long, TeamEntity> teamById = currentTeams.stream()
            .collect(Collectors.toMap(TeamEntity::getId, Function.identity()));

        if (!teamById.keySet().equals(requestedIds)) {
            throw new BadRequestException("Reorder request does not match existing teams.");
        }

        for (int index = 0; index < teamIds.size(); index++) {
            TeamEntity team = teamById.get(teamIds.get(index));
            team.setDisplayOrder(index);
            teamMapper.updateById(team);
        }

        return listTeams();
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