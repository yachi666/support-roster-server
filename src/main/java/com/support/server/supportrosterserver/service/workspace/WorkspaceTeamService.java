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
import com.support.server.supportrosterserver.entity.auth.WorkspaceAccountTeamScopeEntity;
import com.support.server.supportrosterserver.entity.workspace.RosterAssignmentEntity;
import com.support.server.supportrosterserver.entity.workspace.ShiftDefinitionTeamRelEntity;
import com.support.server.supportrosterserver.entity.workspace.StaffEntity;
import com.support.server.supportrosterserver.entity.workspace.TeamEntity;
import com.support.server.supportrosterserver.exception.BadRequestException;
import com.support.server.supportrosterserver.mapper.RosterAssignmentMapper;
import com.support.server.supportrosterserver.mapper.ShiftDefinitionTeamRelMapper;
import com.support.server.supportrosterserver.mapper.StaffMapper;
import com.support.server.supportrosterserver.mapper.TeamMapper;
import com.support.server.supportrosterserver.mapper.WorkspaceAccountTeamScopeMapper;
import com.support.server.supportrosterserver.service.auth.AuthContextService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WorkspaceTeamService {

    private final TeamMapper teamMapper;
    private final WorkspaceLookupService lookupService;
    private final AuthContextService authContextService;
    private final WorkspaceOperationLogService workspaceOperationLogService;
    private final StaffMapper staffMapper;
    private final WorkspaceAccountTeamScopeMapper workspaceAccountTeamScopeMapper;
    private final ShiftDefinitionTeamRelMapper shiftDefinitionTeamRelMapper;
    private final RosterAssignmentMapper rosterAssignmentMapper;

    public List<WorkspaceTeamDto> listTeams() {
        return lookupService.listTeams().stream()
            .map(this::toDto)
            .toList();
    }

    @Transactional
    public WorkspaceTeamDto createTeam(WorkspaceTeamUpsertRequest request) {
        authContextService.requireAdmin();
        TeamEntity entity = new TeamEntity();
        apply(entity, request);
        teamMapper.insert(entity);
        WorkspaceTeamDto created = getTeam(entity.getId());
        workspaceOperationLogService.log(
            authContextService.currentActor("system"),
            "Create workspace team",
            "workspace_team",
            created.getId(),
            "Team=" + created.getName()
        );
        return created;
    }

    @Transactional
    public WorkspaceTeamDto updateTeam(Long id, WorkspaceTeamUpsertRequest request) {
        authContextService.requireAdmin();
        TeamEntity entity = lookupService.requireTeam(id);
        String previousName = entity.getName();
        apply(entity, request);
        teamMapper.updateById(entity);
        WorkspaceTeamDto updated = getTeam(id);
        String action = previousName != null && !previousName.equals(updated.getName())
            ? "Rename workspace team"
            : "Update workspace team";
        String details = previousName != null && !previousName.equals(updated.getName())
            ? "From=" + previousName + "; To=" + updated.getName()
            : "Team=" + updated.getName();
        workspaceOperationLogService.log(
            authContextService.currentActor("system"),
            action,
            "workspace_team",
            updated.getId(),
            details
        );
        return updated;
    }

    @Transactional
    public List<WorkspaceTeamDto> reorderTeams(List<Long> teamIds) {
        authContextService.requireAdmin();
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

        workspaceOperationLogService.log(
            authContextService.currentActor("system"),
            "Reorder workspace teams",
            "workspace_team",
            null,
            "Updated display order for " + teamIds.size() + " teams"
        );
        return listTeams();
    }

    public WorkspaceTeamDto getTeam(Long id) {
        return toDto(lookupService.requireTeam(id));
    }

    @Transactional
    public void deleteTeam(Long id) {
        authContextService.requireAdmin();
        TeamEntity team = lookupService.requireTeam(id);
        validateDeleteTeamDependencies(id, team.getName());
        teamMapper.deleteById(id);
        workspaceOperationLogService.log(
            authContextService.currentActor("system"),
            "Delete workspace team",
            "workspace_team",
            id,
            "Team=" + team.getName()
        );
    }

    public List<com.support.server.supportrosterserver.dto.TeamDto> listViewerTeams() {
        return lookupService.listTeams().stream()
            .filter(team -> Boolean.TRUE.equals(team.getVisible()))
            .map(team -> new com.support.server.supportrosterserver.dto.TeamDto(
                String.valueOf(team.getId()),
                team.getName(),
                team.getColor(),
                team.getDisplayOrder()
            ))
            .toList();
    }

    private WorkspaceTeamDto toDto(TeamEntity team) {
        return new WorkspaceTeamDto(
            team.getId(),
            team.getName(),
            team.getColor(),
            team.getDisplayOrder(),
            team.getVisible(),
            team.getDescription()
        );
    }

    private void apply(TeamEntity entity, WorkspaceTeamUpsertRequest request) {
        String normalizedName = normalizeTeamName(request.getName());
        ensureUniqueTeamName(entity.getId(), normalizedName);

        entity.setName(normalizedName);
        entity.setColor(request.getColor() == null ? null : request.getColor().trim());
        entity.setDisplayOrder(request.getDisplayOrder());
        entity.setVisible(request.getVisible());
        entity.setDescription(request.getDescription() == null || request.getDescription().isBlank() ? null : request.getDescription().trim());
    }

    private void ensureUniqueTeamName(Long currentTeamId, String normalizedName) {
        boolean exists = lookupService.listTeams().stream()
            .filter(team -> currentTeamId == null || !team.getId().equals(currentTeamId))
            .map(TeamEntity::getName)
            .map(this::normalizeTeamName)
            .anyMatch(normalizedName::equals);
        if (exists) {
            throw new BadRequestException("Team name '" + normalizedName + "' already exists.");
        }
    }

    private String normalizeTeamName(String name) {
        if (name == null || name.isBlank()) {
            throw new BadRequestException("Team name is required.");
        }
        return name.trim().replaceAll("\\s+", " ");
    }

    private void validateDeleteTeamDependencies(Long teamId, String teamName) {
        List<String> blockers = new java.util.ArrayList<>();
        if (staffMapper.selectCount(Wrappers.<StaffEntity>lambdaQuery().eq(StaffEntity::getTeamId, teamId)) > 0) {
            blockers.add("staff");
        }
        if (workspaceAccountTeamScopeMapper.selectCount(Wrappers.<WorkspaceAccountTeamScopeEntity>lambdaQuery()
                .eq(WorkspaceAccountTeamScopeEntity::getTeamId, teamId)) > 0) {
            blockers.add("account scopes");
        }
        if (shiftDefinitionTeamRelMapper.selectCount(Wrappers.<ShiftDefinitionTeamRelEntity>lambdaQuery()
                .eq(ShiftDefinitionTeamRelEntity::getTeamId, teamId)) > 0) {
            blockers.add("shift definitions");
        }
        if (rosterAssignmentMapper.selectCount(Wrappers.<RosterAssignmentEntity>lambdaQuery()
                .eq(RosterAssignmentEntity::getTeamId, teamId)) > 0) {
            blockers.add("roster assignments");
        }
        if (!blockers.isEmpty()) {
            throw new BadRequestException("Team '" + teamName + "' cannot be deleted while linked " + String.join(", ", blockers) + " still exist.");
        }
    }

}
