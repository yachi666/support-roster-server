package com.support.server.supportrosterserver.service.workspace;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
import com.support.server.supportrosterserver.entity.workspace.TeamEntity;
import com.support.server.supportrosterserver.exception.BadRequestException;
import com.support.server.supportrosterserver.exception.ResourceNotFoundException;
import com.support.server.supportrosterserver.mapper.ShiftDefinitionMapper;
import com.support.server.supportrosterserver.mapper.ShiftDefinitionTeamRelMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WorkspaceShiftDefinitionService {

    private final ShiftDefinitionMapper shiftDefinitionMapper;
    private final ShiftDefinitionTeamRelMapper shiftDefinitionTeamRelMapper;
    private final WorkspaceLookupService lookupService;

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
        Map<Long, List<TeamEntity>> teamsByShiftDefinitionId = loadTeamsByShiftDefinitionId(
            definitions.stream().map(ShiftDefinitionEntity::getId).toList(),
            teamMap
        );

        return definitions.stream()
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
        return toDto(entity, teamsByShiftDefinitionId.getOrDefault(id, List.of()));
    }

    @Transactional
    public WorkspaceShiftDefinitionDto createShiftDefinition(WorkspaceShiftDefinitionUpsertRequest request) {
        validateCodeAvailability(null, request.getCode(), request.getTeamIds());

        ShiftDefinitionEntity entity = new ShiftDefinitionEntity();
        apply(entity, request);
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

        validateCodeAvailability(id, request.getCode(), request.getTeamIds());
        apply(entity, request);
        shiftDefinitionMapper.updateById(entity);
        replaceTeamRelations(id, request.getTeamIds());
        return getShiftDefinition(id);
    }

    @Transactional
    public void deleteShiftDefinition(Long id) {
        if (shiftDefinitionMapper.selectById(id) == null) {
            throw new ResourceNotFoundException("ShiftDefinition", "id", id);
        }

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

    private WorkspaceShiftDefinitionDto toDto(ShiftDefinitionEntity entity, List<TeamEntity> teams) {
        TeamEntity primaryTeam = teams.isEmpty() ? null : teams.get(0);
        return new WorkspaceShiftDefinitionDto(
            entity.getId(),
            primaryTeam == null ? entity.getTeamId() : primaryTeam.getId(),
            primaryTeam == null ? null : primaryTeam.getTeamCode(),
            primaryTeam == null ? null : primaryTeam.getName(),
            entity.getCode(),
            entity.getMeaning(),
            entity.getStartTime(),
            entity.getEndTime(),
            lookupService.normalizeWorkspaceTimezone(entity.getTimezone()),
            entity.getPrimaryShift(),
            entity.getVisible(),
            entity.getColorHex(),
            entity.getRemark(),
            teams.stream()
                .map(team -> new WorkspaceShiftDefinitionTeamDto(team.getId(), team.getTeamCode(), team.getName(), team.getColor()))
                .toList()
        );
    }

    private void apply(ShiftDefinitionEntity entity, WorkspaceShiftDefinitionUpsertRequest request) {
        List<Long> normalizedTeamIds = request.getTeamIds().stream().distinct().toList();
        normalizedTeamIds.forEach(lookupService::requireTeam);

        entity.setTeamId(normalizedTeamIds.get(0));
        entity.setRoleGroupId(null);
        entity.setCode(request.getCode());
        entity.setMeaning(request.getMeaning());
        entity.setStartTime(request.getStartTime());
        entity.setEndTime(request.getEndTime());
        entity.setTimezone(lookupService.normalizeWorkspaceTimezone(request.getTimezone()));
        entity.setPrimaryShift(request.getPrimaryShift());
        entity.setVisible(request.getVisible());
        entity.setColorHex(request.getColorHex());
        entity.setRemark(request.getRemark());
    }

    private Map<Long, List<TeamEntity>> loadTeamsByShiftDefinitionId(List<Long> shiftDefinitionIds, Map<Long, TeamEntity> teamMap) {
        if (shiftDefinitionIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, List<TeamEntity>> teamsByShiftDefinitionId = shiftDefinitionTeamRelMapper.selectList(Wrappers.<ShiftDefinitionTeamRelEntity>lambdaQuery()
                .in(ShiftDefinitionTeamRelEntity::getShiftDefinitionId, shiftDefinitionIds)
                .orderByAsc(ShiftDefinitionTeamRelEntity::getTeamId))
            .stream()
            .collect(Collectors.groupingBy(
                ShiftDefinitionTeamRelEntity::getShiftDefinitionId,
                LinkedHashMap::new,
                Collectors.mapping(relation -> teamMap.get(relation.getTeamId()), Collectors.toList())
            ));

        teamsByShiftDefinitionId.replaceAll((ignored, teams) -> teams.stream().filter(Objects::nonNull).toList());
        return teamsByShiftDefinitionId;
    }

    private void replaceTeamRelations(Long shiftDefinitionId, List<Long> teamIds) {
        shiftDefinitionTeamRelMapper.delete(Wrappers.<ShiftDefinitionTeamRelEntity>lambdaQuery()
            .eq(ShiftDefinitionTeamRelEntity::getShiftDefinitionId, shiftDefinitionId));

        for (Long teamId : teamIds.stream().distinct().toList()) {
            ShiftDefinitionTeamRelEntity relation = new ShiftDefinitionTeamRelEntity();
            relation.setShiftDefinitionId(shiftDefinitionId);
            relation.setTeamId(teamId);
            shiftDefinitionTeamRelMapper.insert(relation);
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