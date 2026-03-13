package com.support.server.supportrosterserver.service.workspace;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.support.server.supportrosterserver.dto.ShiftCodeDto;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceShiftDefinitionDto;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceShiftDefinitionUpsertRequest;
import com.support.server.supportrosterserver.entity.workspace.RoleGroupEntity;
import com.support.server.supportrosterserver.entity.workspace.ShiftDefinitionEntity;
import com.support.server.supportrosterserver.exception.ResourceNotFoundException;
import com.support.server.supportrosterserver.mapper.ShiftDefinitionMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WorkspaceShiftDefinitionService {

    private final ShiftDefinitionMapper shiftDefinitionMapper;
    private final WorkspaceLookupService lookupService;

    public List<WorkspaceShiftDefinitionDto> listShiftDefinitions(String keyword) {
        LambdaQueryWrapper<ShiftDefinitionEntity> query = Wrappers.<ShiftDefinitionEntity>lambdaQuery()
            .orderByAsc(ShiftDefinitionEntity::getCode)
            .orderByAsc(ShiftDefinitionEntity::getRoleGroupId);
        if (keyword != null && !keyword.isBlank()) {
            query.and(wrapper -> wrapper
                .like(ShiftDefinitionEntity::getCode, keyword)
                .or().like(ShiftDefinitionEntity::getMeaning, keyword)
                .or().like(ShiftDefinitionEntity::getTimezone, keyword));
        }
        Map<Long, RoleGroupEntity> roleGroupMap = lookupService.roleGroupMap();
        return shiftDefinitionMapper.selectList(query).stream()
            .map(def -> toDto(def, roleGroupMap.get(def.getRoleGroupId())))
            .toList();
    }

    public WorkspaceShiftDefinitionDto getShiftDefinition(Long id) {
        ShiftDefinitionEntity entity = shiftDefinitionMapper.selectById(id);
        if (entity == null) {
            throw new ResourceNotFoundException("ShiftDefinition", "id", id);
        }
        RoleGroupEntity roleGroup = lookupService.roleGroupMap().get(entity.getRoleGroupId());
        return toDto(entity, roleGroup);
    }

    @Transactional
    public WorkspaceShiftDefinitionDto createShiftDefinition(WorkspaceShiftDefinitionUpsertRequest request) {
        ShiftDefinitionEntity entity = new ShiftDefinitionEntity();
        apply(entity, request);
        shiftDefinitionMapper.insert(entity);
        return getShiftDefinition(entity.getId());
    }

    @Transactional
    public WorkspaceShiftDefinitionDto updateShiftDefinition(Long id, WorkspaceShiftDefinitionUpsertRequest request) {
        ShiftDefinitionEntity entity = shiftDefinitionMapper.selectById(id);
        if (entity == null) {
            throw new ResourceNotFoundException("ShiftDefinition", "id", id);
        }
        apply(entity, request);
        shiftDefinitionMapper.updateById(entity);
        return getShiftDefinition(id);
    }

    @Transactional
    public void deleteShiftDefinition(Long id) {
        if (shiftDefinitionMapper.selectById(id) == null) {
            throw new ResourceNotFoundException("ShiftDefinition", "id", id);
        }
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

    private WorkspaceShiftDefinitionDto toDto(ShiftDefinitionEntity entity, RoleGroupEntity roleGroup) {
        return new WorkspaceShiftDefinitionDto(
            entity.getId(),
            entity.getRoleGroupId(),
            roleGroup == null ? null : roleGroup.getCode(),
            roleGroup == null ? null : roleGroup.getName(),
            entity.getCode(),
            entity.getMeaning(),
            entity.getStartTime(),
            entity.getEndTime(),
            entity.getTimezone(),
            entity.getPrimaryShift(),
            entity.getVisible(),
            entity.getColorHex(),
            entity.getRemark()
        );
    }

    private void apply(ShiftDefinitionEntity entity, WorkspaceShiftDefinitionUpsertRequest request) {
        lookupService.requireRoleGroup(request.getRoleGroupId());
        entity.setRoleGroupId(request.getRoleGroupId());
        entity.setCode(request.getCode());
        entity.setMeaning(request.getMeaning());
        entity.setStartTime(request.getStartTime());
        entity.setEndTime(request.getEndTime());
        entity.setTimezone(request.getTimezone());
        entity.setPrimaryShift(request.getPrimaryShift());
        entity.setVisible(request.getVisible());
        entity.setColorHex(request.getColorHex());
        entity.setRemark(request.getRemark());
    }
}