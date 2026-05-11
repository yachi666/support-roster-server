package com.support.server.supportrosterserver.service.workspace;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.support.server.supportrosterserver.dto.ShiftCodeDto;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceShiftDefinitionDto;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceShiftDefinitionTeamDto;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceShiftDefinitionUpsertRequest;
import com.support.server.supportrosterserver.entity.workspace.ShiftDefinitionEntity;
import com.support.server.supportrosterserver.entity.workspace.ShiftDefinitionTeamRelEntity;
import com.support.server.supportrosterserver.entity.workspace.RosterAssignmentEntity;
import com.support.server.supportrosterserver.entity.workspace.TeamEntity;
import com.support.server.supportrosterserver.exception.BadRequestException;
import com.support.server.supportrosterserver.exception.ResourceNotFoundException;
import com.support.server.supportrosterserver.mapper.RosterAssignmentMapper;
import com.support.server.supportrosterserver.mapper.ShiftDefinitionMapper;
import com.support.server.supportrosterserver.mapper.ShiftDefinitionTeamRelMapper;
import com.support.server.supportrosterserver.mapper.TeamMapper;
import com.support.server.supportrosterserver.service.auth.AuthContextService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WorkspaceShiftDefinitionService {

    private final ShiftDefinitionMapper shiftDefinitionMapper;
    private final ShiftDefinitionTeamRelMapper shiftDefinitionTeamRelMapper;
    private final RosterAssignmentMapper rosterAssignmentMapper;
    private final WorkspaceLookupService lookupService;
    private final AuthContextService authContextService;
    private final WorkspaceShiftTimeSupport shiftTimeSupport;
    private final TeamMapper teamMapper;

    public List<WorkspaceShiftDefinitionDto> listShiftDefinitions(String keyword) {
        LambdaQueryWrapper<ShiftDefinitionEntity> query = Wrappers.<ShiftDefinitionEntity>lambdaQuery()
            .orderByAsc(ShiftDefinitionEntity::getCode)
            .orderByAsc(ShiftDefinitionEntity::getTeamId);
        if (keyword != null && !keyword.isBlank()) {
            query.and(wrapper -> wrapper
                .like(ShiftDefinitionEntity::getCode, keyword)
                .or().like(ShiftDefinitionEntity::getMeaning, keyword)
                .or().like(ShiftDefinitionEntity::getTimezone, keyword));
        }

        List<ShiftDefinitionEntity> definitions = shiftDefinitionMapper.selectList(query);
        Map<Long, TeamEntity> teamMap = lookupService.teamMap();
        Map<Long, List<WorkspaceShiftDefinitionTeamDto>> teamsByShiftDefinitionId = loadTeamsByShiftDefinitionId(
            definitions.stream().map(ShiftDefinitionEntity::getId).toList(),
            teamMap
        );

        return definitions.stream()
            .filter(definition -> teamsByShiftDefinitionId.getOrDefault(definition.getId(), List.of()).stream()
                .map(WorkspaceShiftDefinitionTeamDto::getId)
                .anyMatch(authContextService::canReadTeam))
            .map(definition -> toDto(definition, teamsByShiftDefinitionId.getOrDefault(definition.getId(), List.of())))
            .toList();
    }

    public WorkspaceShiftDefinitionDto getShiftDefinition(Long id) {
        ShiftDefinitionEntity entity = shiftDefinitionMapper.selectById(id);
        if (entity == null) {
            throw new ResourceNotFoundException("ShiftDefinition", "id", id);
        }

        Map<Long, TeamEntity> teamMap = lookupService.teamMap();
        Map<Long, List<WorkspaceShiftDefinitionTeamDto>> teamsByShiftDefinitionId = loadTeamsByShiftDefinitionId(List.of(id), teamMap);
        authContextService.requireReadableAnyTeam(teamsByShiftDefinitionId.getOrDefault(id, List.of()).stream().map(WorkspaceShiftDefinitionTeamDto::getId).toList());
        return toDto(entity, teamsByShiftDefinitionId.getOrDefault(id, List.of()));
    }

    @Transactional
    public WorkspaceShiftDefinitionDto createShiftDefinition(WorkspaceShiftDefinitionUpsertRequest request) {
        authContextService.requireWritableTeams(request.getTeamIds());
        validateCodeAvailability(null, request.getCode(), request.getTeamIds());

        ShiftDefinitionEntity entity = new ShiftDefinitionEntity();
        apply(entity, request, null);
        shiftDefinitionMapper.insert(entity);
        syncTeamRelations(entity.getId(), request.getTeamIds());
        return getShiftDefinition(entity.getId());
    }

    @Transactional
    public WorkspaceShiftDefinitionDto updateShiftDefinition(Long id, WorkspaceShiftDefinitionUpsertRequest request) {
        ShiftDefinitionEntity entity = shiftDefinitionMapper.selectById(id);
        if (entity == null) {
            throw new ResourceNotFoundException("ShiftDefinition", "id", id);
        }
        String previousCode = entity.getCode();
        List<Long> existingTeamIds = loadTeamsByShiftDefinitionId(List.of(id), lookupService.teamMap()).getOrDefault(id, List.of()).stream()
            .map(WorkspaceShiftDefinitionTeamDto::getId)
            .toList();
        authContextService.requireWritableTeams(existingTeamIds);
        authContextService.requireWritableTeams(request.getTeamIds());

        validateCodeAvailability(id, request.getCode(), request.getTeamIds());
        apply(entity, request, entity.getTeamId());
        shiftDefinitionMapper.updateById(entity);
        syncTeamRelations(id, request.getTeamIds());
        if (!Objects.equals(previousCode, entity.getCode())) {
            syncAssignmentShiftCodes(id, entity.getCode());
        }
        return getShiftDefinition(id);
    }

    @Transactional
    public void reorderShiftDefinitions(Long teamId, List<Long> shiftDefinitionIds) {
        authContextService.requireWritableTeam(teamId);

        List<ShiftDefinitionTeamRelEntity> teamRelations = shiftDefinitionTeamRelMapper.selectList(
            Wrappers.<ShiftDefinitionTeamRelEntity>lambdaQuery()
                .eq(ShiftDefinitionTeamRelEntity::getTeamId, teamId)
                .orderByAsc(ShiftDefinitionTeamRelEntity::getDisplayOrder)
                .orderByAsc(ShiftDefinitionTeamRelEntity::getShiftDefinitionId)
        );

        if (teamRelations.isEmpty()) {
            throw new BadRequestException("Team has no shift definitions to reorder.");
        }
        if (shiftDefinitionIds.size() != teamRelations.size()) {
            throw new BadRequestException("Reorder request must include all shift definitions for the selected team.");
        }

        Set<Long> requestedIds = new LinkedHashSet<>(shiftDefinitionIds);
        if (requestedIds.size() != shiftDefinitionIds.size()) {
            throw new BadRequestException("Reorder request contains duplicate shift definition ids.");
        }

        Map<Long, ShiftDefinitionTeamRelEntity> relationByShiftDefinitionId = teamRelations.stream()
            .collect(Collectors.toMap(
                ShiftDefinitionTeamRelEntity::getShiftDefinitionId,
                Function.identity(),
                (left, right) -> left,
                LinkedHashMap::new
            ));

        if (!relationByShiftDefinitionId.keySet().equals(requestedIds)) {
            throw new BadRequestException("Reorder request does not match existing shift definitions for the selected team.");
        }

        for (int index = 0; index < shiftDefinitionIds.size(); index++) {
            ShiftDefinitionTeamRelEntity relation = relationByShiftDefinitionId.get(shiftDefinitionIds.get(index));
            relation.setDisplayOrder(index);
            shiftDefinitionTeamRelMapper.updateById(relation);
        }
    }

    @Transactional
    public void deleteShiftDefinition(Long id) {
        ShiftDefinitionEntity shiftDefinition = shiftDefinitionMapper.selectById(id);
        if (shiftDefinition == null) {
            throw new ResourceNotFoundException("ShiftDefinition", "id", id);
        }

        List<ShiftDefinitionTeamRelEntity> teamRelations = shiftDefinitionTeamRelMapper.selectList(
            Wrappers.<ShiftDefinitionTeamRelEntity>lambdaQuery()
                .eq(ShiftDefinitionTeamRelEntity::getShiftDefinitionId, id)
        );
        Set<Long> teamIds = new LinkedHashSet<>();
        if (shiftDefinition.getTeamId() != null) {
            teamIds.add(shiftDefinition.getTeamId());
        }
        teamRelations.stream()
            .map(ShiftDefinitionTeamRelEntity::getTeamId)
            .filter(Objects::nonNull)
            .forEach(teamIds::add);
        authContextService.requireWritableTeams(teamIds);

        rosterAssignmentMapper.delete(buildAssignmentCleanupQuery(id));
        shiftDefinitionTeamRelMapper.delete(Wrappers.<ShiftDefinitionTeamRelEntity>lambdaQuery()
            .eq(ShiftDefinitionTeamRelEntity::getShiftDefinitionId, id));
        shiftDefinitionMapper.deleteById(id);
    }

    public List<ShiftCodeDto> listViewerShiftCodes() {
        return shiftDefinitionMapper.selectList(Wrappers.<ShiftDefinitionEntity>lambdaQuery()
                .eq(ShiftDefinitionEntity::getVisible, true)
                .orderByAsc(ShiftDefinitionEntity::getCode))
            .stream()
            .collect(java.util.stream.Collectors.toMap(
                ShiftDefinitionEntity::getCode,
                def -> new ShiftCodeDto(def.getCode(), def.getMeaning(), def.getColorHex()),
                (left, right) -> left,
                java.util.LinkedHashMap::new
            ))
            .values()
            .stream()
            .toList();
    }

    private WorkspaceShiftDefinitionDto toDto(ShiftDefinitionEntity entity, List<WorkspaceShiftDefinitionTeamDto> teams) {
        WorkspaceShiftDefinitionTeamDto primaryTeam = teams.stream()
            .filter(team -> Objects.equals(team.getId(), entity.getTeamId()))
            .findFirst()
            .orElse(teams.isEmpty() ? null : teams.get(0));
        return new WorkspaceShiftDefinitionDto(
            entity.getId(),
            primaryTeam == null ? entity.getTeamId() : primaryTeam.getId(),
            primaryTeam == null ? null : primaryTeam.getName(),
            entity.getCode(),
            entity.getMeaning(),
            entity.getStartTime(),
            shiftTimeSupport.deriveEndTime(entity.getStartTime(), shiftTimeSupport.resolveDurationMinutes(entity)),
            shiftTimeSupport.resolveDurationMinutes(entity),
            lookupService.normalizeWorkspaceTimezone(entity.getTimezone()),
            entity.getPrimaryShift(),
            entity.getVisible(),
            entity.getColorHex(),
            entity.getRemark(),
            teams
        );
    }

    private void apply(ShiftDefinitionEntity entity, WorkspaceShiftDefinitionUpsertRequest request, Long currentPrimaryTeamId) {
        List<Long> normalizedTeamIds = request.getTeamIds().stream().distinct().toList();
        normalizedTeamIds.forEach(lookupService::requireTeam);

        entity.setTeamId(resolvePrimaryTeamId(normalizedTeamIds, currentPrimaryTeamId));
        entity.setRoleGroupId(null);
        entity.setCode(request.getCode());
        entity.setMeaning(request.getMeaning());
        entity.setStartTime(request.getStartTime());
        entity.setDurationMinutes(shiftTimeSupport.requireValidDurationMinutes(request.getDurationMinutes()));
        entity.setEndTime(shiftTimeSupport.deriveEndTime(request.getStartTime(), request.getDurationMinutes()));
        entity.setTimezone(lookupService.normalizeWorkspaceTimezone(request.getTimezone()));
        entity.setPrimaryShift(request.getPrimaryShift());
        entity.setVisible(request.getVisible());
        entity.setColorHex(normalizeOptionalHexColor(request.getColorHex()));
        entity.setRemark(request.getRemark());
    }

    private Long resolvePrimaryTeamId(List<Long> normalizedTeamIds, Long currentPrimaryTeamId) {
        if (currentPrimaryTeamId != null && normalizedTeamIds.contains(currentPrimaryTeamId)) {
            return currentPrimaryTeamId;
        }
        return normalizedTeamIds.get(0);
    }

    private String normalizeOptionalHexColor(String colorHex) {
        if (colorHex == null || colorHex.isBlank()) {
            return null;
        }

        String normalized = colorHex.trim();
        if (!normalized.matches("^#([0-9a-fA-F]{6})$")) {
            throw new BadRequestException("Use a 6-digit hex color.");
        }

        return normalized.toLowerCase();
    }

    private Map<Long, List<WorkspaceShiftDefinitionTeamDto>> loadTeamsByShiftDefinitionId(List<Long> shiftDefinitionIds, Map<Long, TeamEntity> teamMap) {
        if (shiftDefinitionIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, List<WorkspaceShiftDefinitionTeamDto>> teamsByShiftDefinitionId = shiftDefinitionTeamRelMapper.selectList(Wrappers.<ShiftDefinitionTeamRelEntity>lambdaQuery()
                .in(ShiftDefinitionTeamRelEntity::getShiftDefinitionId, shiftDefinitionIds)
                .orderByAsc(ShiftDefinitionTeamRelEntity::getTeamId))
            .stream()
            .collect(Collectors.groupingBy(
                ShiftDefinitionTeamRelEntity::getShiftDefinitionId,
                LinkedHashMap::new,
                Collectors.mapping(relation -> {
                    TeamEntity team = teamMap.get(relation.getTeamId());
                    if (team == null) {
                        return null;
                    }
                    return new WorkspaceShiftDefinitionTeamDto(
                        team.getId(),
                        team.getName(),
                        team.getColor(),
                        relation.getDisplayOrder()
                    );
                }, Collectors.toList())
            ));

        teamsByShiftDefinitionId.replaceAll((ignored, teams) -> teams.stream().filter(Objects::nonNull).toList());
        return teamsByShiftDefinitionId;
    }

    private void syncTeamRelations(Long shiftDefinitionId, List<Long> teamIds) {
        List<Long> normalizedTeamIds = teamIds.stream().distinct().toList();
        List<ShiftDefinitionTeamRelEntity> existingRelations = shiftDefinitionTeamRelMapper.selectList(
            Wrappers.<ShiftDefinitionTeamRelEntity>lambdaQuery()
                .eq(ShiftDefinitionTeamRelEntity::getShiftDefinitionId, shiftDefinitionId)
        );
        Map<Long, ShiftDefinitionTeamRelEntity> existingRelationByTeamId = existingRelations.stream()
            .collect(Collectors.toMap(ShiftDefinitionTeamRelEntity::getTeamId, Function.identity(), (left, right) -> left));

        for (ShiftDefinitionTeamRelEntity relation : existingRelations) {
            if (!normalizedTeamIds.contains(relation.getTeamId())) {
                shiftDefinitionTeamRelMapper.deleteById(relation.getId());
            }
        }

        Map<Long, Integer> nextDisplayOrderByTeamId = prepareNextDisplayOrderByTeamId(normalizedTeamIds);

        for (Long teamId : normalizedTeamIds) {
            ShiftDefinitionTeamRelEntity existingRelation = existingRelationByTeamId.get(teamId);
            if (existingRelation != null) {
                if (existingRelation.getDisplayOrder() == null) {
                    existingRelation.setDisplayOrder(nextDisplayOrderByTeamId.get(teamId));
                    nextDisplayOrderByTeamId.computeIfPresent(teamId, (ignored, currentOrder) -> currentOrder + 1);
                    shiftDefinitionTeamRelMapper.updateById(existingRelation);
                }
                continue;
            }

            ShiftDefinitionTeamRelEntity relation = new ShiftDefinitionTeamRelEntity();
            relation.setShiftDefinitionId(shiftDefinitionId);
            relation.setTeamId(teamId);
            relation.setDisplayOrder(nextDisplayOrderByTeamId.get(teamId));
            nextDisplayOrderByTeamId.computeIfPresent(teamId, (ignored, currentOrder) -> currentOrder + 1);
            shiftDefinitionTeamRelMapper.insert(relation);
        }
    }

    private Map<Long, Integer> prepareNextDisplayOrderByTeamId(List<Long> teamIds) {
        List<Long> normalizedTeamIds = teamIds.stream()
            .filter(Objects::nonNull)
            .distinct()
            .sorted()
            .toList();
        Map<Long, Integer> nextDisplayOrderByTeamId = new LinkedHashMap<>();

        for (Long teamId : normalizedTeamIds) {
            lockTeamForDisplayOrder(teamId);
            nextDisplayOrderByTeamId.put(teamId, resolveNextDisplayOrderUnderLock(teamId));
        }

        return nextDisplayOrderByTeamId;
    }

    private void lockTeamForDisplayOrder(Long teamId) {
        TeamEntity team = teamMapper.selectOne(
            Wrappers.<TeamEntity>lambdaQuery()
                .eq(TeamEntity::getId, teamId)
                .last("for update")
        );
        if (team == null) {
            throw new ResourceNotFoundException("Team", "id", teamId);
        }
    }

    private int resolveNextDisplayOrderUnderLock(Long teamId) {
        ShiftDefinitionTeamRelEntity lastRelation = shiftDefinitionTeamRelMapper.selectOne(
            Wrappers.<ShiftDefinitionTeamRelEntity>lambdaQuery()
                .eq(ShiftDefinitionTeamRelEntity::getTeamId, teamId)
                .orderByDesc(ShiftDefinitionTeamRelEntity::getDisplayOrder)
                .orderByDesc(ShiftDefinitionTeamRelEntity::getId)
                .last("limit 1")
        );
        return lastRelation == null || lastRelation.getDisplayOrder() == null
            ? 0
            : lastRelation.getDisplayOrder() + 1;
    }

    private LambdaQueryWrapper<RosterAssignmentEntity> buildAssignmentCleanupQuery(Long shiftDefinitionId) {
        return Wrappers.<RosterAssignmentEntity>lambdaQuery()
            .eq(RosterAssignmentEntity::getShiftDefinitionId, shiftDefinitionId);
    }

    private void syncAssignmentShiftCodes(Long shiftDefinitionId, String code) {
        List<RosterAssignmentEntity> assignments = rosterAssignmentMapper.selectList(Wrappers.<RosterAssignmentEntity>lambdaQuery()
            .eq(RosterAssignmentEntity::getShiftDefinitionId, shiftDefinitionId));
        for (RosterAssignmentEntity assignment : assignments) {
            assignment.setShiftCode(code);
            rosterAssignmentMapper.updateById(assignment);
        }
    }

    private void validateCodeAvailability(Long currentDefinitionId, String code, List<Long> teamIds) {
        List<Long> requestedTeamIds = teamIds.stream().distinct().toList();
        List<ShiftDefinitionEntity> sameCodeDefinitions = shiftDefinitionMapper.selectList(Wrappers.<ShiftDefinitionEntity>lambdaQuery()
            .eq(ShiftDefinitionEntity::getCode, code));
        if (sameCodeDefinitions.isEmpty()) {
            return;
        }

        Map<Long, Set<Long>> teamIdsByDefinitionId = shiftDefinitionTeamRelMapper.selectList(Wrappers.<ShiftDefinitionTeamRelEntity>lambdaQuery()
                .in(ShiftDefinitionTeamRelEntity::getShiftDefinitionId, sameCodeDefinitions.stream().map(ShiftDefinitionEntity::getId).toList()))
            .stream()
            .collect(Collectors.groupingBy(
                ShiftDefinitionTeamRelEntity::getShiftDefinitionId,
                Collectors.mapping(ShiftDefinitionTeamRelEntity::getTeamId, Collectors.toSet())
            ));

        for (ShiftDefinitionEntity existingDefinition : sameCodeDefinitions) {
            if (existingDefinition.getId().equals(currentDefinitionId)) {
                continue;
            }

            Set<Long> existingTeamIds = teamIdsByDefinitionId.getOrDefault(existingDefinition.getId(), Set.of());
            for (Long requestedTeamId : requestedTeamIds) {
                if (existingTeamIds.contains(requestedTeamId)) {
                    TeamEntity team = lookupService.requireTeam(requestedTeamId);
                    throw new BadRequestException("Shift code '" + code + "' already exists for team '" + team.getName() + "'.");
                }
            }
        }
    }
}
