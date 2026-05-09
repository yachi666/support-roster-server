package com.support.server.supportrosterserver.service.workspace;

import java.util.Comparator;
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
import com.support.server.supportrosterserver.dto.workspace.WorkspaceShiftDefinitionReorderRequest;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceShiftDefinitionTeamDto;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceShiftDefinitionUpsertRequest;
import com.support.server.supportrosterserver.entity.workspace.ShiftDefinitionEntity;
import com.support.server.supportrosterserver.entity.workspace.RosterAssignmentEntity;
import com.support.server.supportrosterserver.entity.workspace.ShiftDefinitionTeamRelEntity;
import com.support.server.supportrosterserver.entity.workspace.TeamEntity;
import com.support.server.supportrosterserver.exception.BadRequestException;
import com.support.server.supportrosterserver.exception.ResourceNotFoundException;
import com.support.server.supportrosterserver.mapper.RosterAssignmentMapper;
import com.support.server.supportrosterserver.mapper.ShiftDefinitionMapper;
import com.support.server.supportrosterserver.mapper.ShiftDefinitionTeamRelMapper;
import com.support.server.supportrosterserver.service.auth.AuthContextService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WorkspaceShiftDefinitionService {

    private static final Long DEFAULT_RELATION_TEAM_ORDER = Long.MAX_VALUE;
    private static final Integer DEFAULT_RELATION_DISPLAY_ORDER = Integer.MAX_VALUE;

    private final ShiftDefinitionMapper shiftDefinitionMapper;
    private final ShiftDefinitionTeamRelMapper shiftDefinitionTeamRelMapper;
    private final RosterAssignmentMapper rosterAssignmentMapper;
    private final WorkspaceLookupService lookupService;
    private final AuthContextService authContextService;
    private final WorkspaceShiftTimeSupport shiftTimeSupport;

    public List<WorkspaceShiftDefinitionDto> listShiftDefinitions(String keyword) {
        LambdaQueryWrapper<ShiftDefinitionEntity> query = Wrappers.<ShiftDefinitionEntity>lambdaQuery();
        if (keyword != null && !keyword.isBlank()) {
            query.and(wrapper -> wrapper
                .like(ShiftDefinitionEntity::getCode, keyword)
                .or().like(ShiftDefinitionEntity::getMeaning, keyword)
                .or().like(ShiftDefinitionEntity::getTimezone, keyword));
        }

        List<ShiftDefinitionEntity> definitions = shiftDefinitionMapper.selectList(query);
        Map<Long, TeamEntity> teamMap = lookupService.teamMap();
        List<ShiftDefinitionTeamRelEntity> relations = loadOrderedTeamRelations(definitions.stream().map(ShiftDefinitionEntity::getId).toList());
        Map<Long, List<TeamEntity>> teamsByShiftDefinitionId = mapTeamsByShiftDefinitionId(relations, teamMap);
        Comparator<ShiftDefinitionEntity> comparator = shiftDefinitionOrderComparator(buildShiftDefinitionOrderKeys(definitions, relations));

        return definitions.stream()
            .sorted(comparator)
            .filter(definition -> teamsByShiftDefinitionId.getOrDefault(definition.getId(), List.of()).stream()
                .map(TeamEntity::getId)
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
        Map<Long, List<TeamEntity>> teamsByShiftDefinitionId = loadTeamsByShiftDefinitionId(List.of(id), teamMap);
        authContextService.requireReadableAnyTeam(teamsByShiftDefinitionId.getOrDefault(id, List.of()).stream().map(TeamEntity::getId).toList());
        return toDto(entity, teamsByShiftDefinitionId.getOrDefault(id, List.of()));
    }

    @Transactional
    public WorkspaceShiftDefinitionDto createShiftDefinition(WorkspaceShiftDefinitionUpsertRequest request) {
        authContextService.requireWritableTeams(request.getTeamIds());
        validateCodeAvailability(null, request.getCode(), request.getTeamIds());

        ShiftDefinitionEntity entity = new ShiftDefinitionEntity();
        apply(entity, request, null);
        shiftDefinitionMapper.insert(entity);
        replaceTeamRelations(entity.getId(), request.getTeamIds());
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
            .map(TeamEntity::getId)
            .toList();
        authContextService.requireWritableTeams(existingTeamIds);
        authContextService.requireWritableTeams(request.getTeamIds());

        validateCodeAvailability(id, request.getCode(), request.getTeamIds());
        apply(entity, request, entity.getTeamId());
        shiftDefinitionMapper.updateById(entity);
        replaceTeamRelations(id, request.getTeamIds());
        if (!Objects.equals(previousCode, entity.getCode())) {
            syncAssignmentShiftCodes(id, entity.getCode());
        }
        return getShiftDefinition(id);
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

    @Transactional
    public void reorderShiftDefinitionsForTeam(WorkspaceShiftDefinitionReorderRequest request) {
        authContextService.requireWritableTeams(List.of(request.getTeamId()));
        List<ShiftDefinitionTeamRelEntity> relations = shiftDefinitionTeamRelMapper.selectList(
            Wrappers.<ShiftDefinitionTeamRelEntity>lambdaQuery()
                .eq(ShiftDefinitionTeamRelEntity::getTeamId, request.getTeamId())
                .orderByAsc(ShiftDefinitionTeamRelEntity::getDisplayOrder)
                .orderByAsc(ShiftDefinitionTeamRelEntity::getShiftDefinitionId)
        );

        Map<Long, ShiftDefinitionTeamRelEntity> relationByShiftId = relations.stream()
            .collect(Collectors.toMap(
                ShiftDefinitionTeamRelEntity::getShiftDefinitionId,
                Function.identity(),
                (left, right) -> left,
                LinkedHashMap::new
            ));
        LinkedHashSet<Long> requestedShiftIds = new LinkedHashSet<>(request.getShiftDefinitionIds());
        if (requestedShiftIds.size() != request.getShiftDefinitionIds().size()
            || !relationByShiftId.keySet().equals(requestedShiftIds)) {
            throw new BadRequestException("Reorder payload must contain exactly the shifts currently linked to the selected team.");
        }

        int index = 0;
        for (Long shiftDefinitionId : request.getShiftDefinitionIds()) {
            ShiftDefinitionTeamRelEntity relation = relationByShiftId.get(shiftDefinitionId);
            relation.setDisplayOrder(index++);
            shiftDefinitionTeamRelMapper.updateById(relation);
        }
    }

    public List<ShiftCodeDto> listViewerShiftCodes() {
        List<ShiftDefinitionEntity> definitions = shiftDefinitionMapper.selectList(Wrappers.<ShiftDefinitionEntity>lambdaQuery()
            .eq(ShiftDefinitionEntity::getVisible, true));
        List<ShiftDefinitionTeamRelEntity> relations = loadOrderedTeamRelations(definitions.stream().map(ShiftDefinitionEntity::getId).toList());
        Comparator<ShiftDefinitionEntity> comparator = shiftDefinitionOrderComparator(buildShiftDefinitionOrderKeys(definitions, relations));

        return definitions.stream()
            .sorted(comparator)
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

    private WorkspaceShiftDefinitionDto toDto(ShiftDefinitionEntity entity, List<TeamEntity> teams) {
        TeamEntity primaryTeam = teams.stream()
            .filter(team -> Objects.equals(team.getId(), entity.getTeamId()))
            .findFirst()
            .orElseGet(() -> teams.isEmpty() ? null : teams.get(0));
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
            teams.stream()
                .map(team -> new WorkspaceShiftDefinitionTeamDto(team.getId(), team.getName(), team.getColor()))
                .toList()
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

    private Map<Long, List<TeamEntity>> loadTeamsByShiftDefinitionId(List<Long> shiftDefinitionIds, Map<Long, TeamEntity> teamMap) {
        return mapTeamsByShiftDefinitionId(loadOrderedTeamRelations(shiftDefinitionIds), teamMap);
    }

    private Map<Long, List<TeamEntity>> mapTeamsByShiftDefinitionId(List<ShiftDefinitionTeamRelEntity> relations, Map<Long, TeamEntity> teamMap) {
        if (relations.isEmpty()) {
            return Map.of();
        }

        Map<Long, List<TeamEntity>> teamsByShiftDefinitionId = relations.stream()
            .collect(Collectors.groupingBy(
                ShiftDefinitionTeamRelEntity::getShiftDefinitionId,
                LinkedHashMap::new,
                Collectors.mapping(relation -> teamMap.get(relation.getTeamId()), Collectors.toList())
            ));

        teamsByShiftDefinitionId.replaceAll((ignored, teams) -> teams.stream().filter(Objects::nonNull).toList());
        return teamsByShiftDefinitionId;
    }

    private List<ShiftDefinitionTeamRelEntity> loadOrderedTeamRelations(List<Long> shiftDefinitionIds) {
        if (shiftDefinitionIds.isEmpty()) {
            return List.of();
        }

        return shiftDefinitionTeamRelMapper.selectList(Wrappers.<ShiftDefinitionTeamRelEntity>lambdaQuery()
            .in(ShiftDefinitionTeamRelEntity::getShiftDefinitionId, shiftDefinitionIds)
            .orderByAsc(ShiftDefinitionTeamRelEntity::getTeamId)
            .orderByAsc(ShiftDefinitionTeamRelEntity::getDisplayOrder)
            .orderByAsc(ShiftDefinitionTeamRelEntity::getShiftDefinitionId));
    }

    private Map<Long, ShiftDefinitionOrderKey> buildShiftDefinitionOrderKeys(
        List<ShiftDefinitionEntity> definitions,
        List<ShiftDefinitionTeamRelEntity> relations
    ) {
        Map<Long, List<ShiftDefinitionTeamRelEntity>> relationsByShiftDefinitionId = relations.stream()
            .collect(Collectors.groupingBy(
                ShiftDefinitionTeamRelEntity::getShiftDefinitionId,
                LinkedHashMap::new,
                Collectors.toList()
            ));
        Map<Long, ShiftDefinitionOrderKey> orderKeys = new LinkedHashMap<>();
        for (ShiftDefinitionEntity definition : definitions) {
            List<ShiftDefinitionTeamRelEntity> definitionRelations = relationsByShiftDefinitionId.getOrDefault(definition.getId(), List.of());
            ShiftDefinitionTeamRelEntity orderRelation = definitionRelations.stream()
                .filter(relation -> Objects.equals(relation.getTeamId(), definition.getTeamId()))
                .findFirst()
                .orElseGet(() -> definitionRelations.isEmpty() ? null : definitionRelations.get(0));
            if (orderRelation == null) {
                continue;
            }

            orderKeys.put(
                definition.getId(),
                new ShiftDefinitionOrderKey(
                    orderRelation.getTeamId() == null ? DEFAULT_RELATION_TEAM_ORDER : orderRelation.getTeamId(),
                    orderRelation.getDisplayOrder() == null ? DEFAULT_RELATION_DISPLAY_ORDER : orderRelation.getDisplayOrder()
                )
            );
        }
        return orderKeys;
    }

    private Comparator<ShiftDefinitionEntity> shiftDefinitionOrderComparator(Map<Long, ShiftDefinitionOrderKey> orderKeys) {
        return Comparator
            .comparing((ShiftDefinitionEntity definition) -> orderKeys.getOrDefault(definition.getId(), ShiftDefinitionOrderKey.DEFAULT).teamId())
            .thenComparing(definition -> orderKeys.getOrDefault(definition.getId(), ShiftDefinitionOrderKey.DEFAULT).displayOrder())
            .thenComparing(ShiftDefinitionEntity::getCode, Comparator.nullsLast(String::compareTo))
            .thenComparing(ShiftDefinitionEntity::getId, Comparator.nullsLast(Long::compareTo));
    }

    private void replaceTeamRelations(Long shiftDefinitionId, List<Long> teamIds) {
        List<ShiftDefinitionTeamRelEntity> existingRelations = shiftDefinitionTeamRelMapper.selectList(
            Wrappers.<ShiftDefinitionTeamRelEntity>lambdaQuery()
                .eq(ShiftDefinitionTeamRelEntity::getShiftDefinitionId, shiftDefinitionId)
        );
        LinkedHashSet<Long> requestedTeamIds = new LinkedHashSet<>(teamIds);
        Map<Long, ShiftDefinitionTeamRelEntity> existingRelationByTeamId = existingRelations.stream()
            .collect(Collectors.toMap(
                ShiftDefinitionTeamRelEntity::getTeamId,
                Function.identity(),
                (left, right) -> left,
                LinkedHashMap::new
            ));

        List<Long> removedTeamIds = existingRelationByTeamId.keySet().stream()
            .filter(teamId -> !requestedTeamIds.contains(teamId))
            .toList();
        if (!removedTeamIds.isEmpty()) {
            shiftDefinitionTeamRelMapper.delete(Wrappers.<ShiftDefinitionTeamRelEntity>lambdaQuery()
                .eq(ShiftDefinitionTeamRelEntity::getShiftDefinitionId, shiftDefinitionId)
                .in(ShiftDefinitionTeamRelEntity::getTeamId, removedTeamIds));
        }

        List<Long> newTeamIds = requestedTeamIds.stream()
            .filter(teamId -> !existingRelationByTeamId.containsKey(teamId))
            .toList();
        Map<Long, Integer> nextDisplayOrderByTeamId = buildNextDisplayOrderByTeamId(newTeamIds);
        for (Long teamId : newTeamIds) {
            ShiftDefinitionTeamRelEntity relation = new ShiftDefinitionTeamRelEntity();
            relation.setShiftDefinitionId(shiftDefinitionId);
            relation.setTeamId(teamId);
            relation.setDisplayOrder(nextDisplayOrderByTeamId.getOrDefault(teamId, 0));
            shiftDefinitionTeamRelMapper.insert(relation);
        }
    }

    private Map<Long, Integer> buildNextDisplayOrderByTeamId(List<Long> teamIds) {
        if (teamIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, Integer> maxDisplayOrderByTeamId = new LinkedHashMap<>();
        shiftDefinitionTeamRelMapper.selectList(Wrappers.<ShiftDefinitionTeamRelEntity>lambdaQuery()
                .in(ShiftDefinitionTeamRelEntity::getTeamId, teamIds))
            .forEach(relation -> maxDisplayOrderByTeamId.merge(
                relation.getTeamId(),
                relation.getDisplayOrder() == null ? 0 : relation.getDisplayOrder(),
                Math::max
            ));

        return teamIds.stream()
            .collect(Collectors.toMap(
                Function.identity(),
                teamId -> maxDisplayOrderByTeamId.getOrDefault(teamId, -1) + 1,
                (left, right) -> left,
                LinkedHashMap::new
            ));
    }

    private record ShiftDefinitionOrderKey(Long teamId, Integer displayOrder) {
        private static final ShiftDefinitionOrderKey DEFAULT =
            new ShiftDefinitionOrderKey(DEFAULT_RELATION_TEAM_ORDER, DEFAULT_RELATION_DISPLAY_ORDER);
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
