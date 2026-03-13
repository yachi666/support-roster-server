package com.support.server.supportrosterserver.service.workspace;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceStaffDto;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceStaffUpsertRequest;
import com.support.server.supportrosterserver.entity.workspace.RoleGroupEntity;
import com.support.server.supportrosterserver.entity.workspace.RosterAssignmentEntity;
import com.support.server.supportrosterserver.entity.workspace.StaffEntity;
import com.support.server.supportrosterserver.entity.workspace.TeamEntity;
import com.support.server.supportrosterserver.mapper.RosterAssignmentMapper;
import com.support.server.supportrosterserver.mapper.StaffMapper;
import com.support.server.supportrosterserver.exception.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WorkspaceStaffService {

    private final StaffMapper staffMapper;
    private final RosterAssignmentMapper rosterAssignmentMapper;
    private final WorkspaceLookupService lookupService;

    public List<WorkspaceStaffDto> listStaff(String keyword) {
        LambdaQueryWrapper<StaffEntity> query = Wrappers.<StaffEntity>lambdaQuery()
            .orderByAsc(StaffEntity::getStaffCode)
            .orderByAsc(StaffEntity::getName);
        if (keyword != null && !keyword.isBlank()) {
            query.and(wrapper -> wrapper
                .like(StaffEntity::getName, keyword)
                .or().like(StaffEntity::getEmail, keyword)
                .or().like(StaffEntity::getStaffCode, keyword)
                .or().like(StaffEntity::getRoleName, keyword)
                .or().like(StaffEntity::getRegion, keyword));
        }

        Map<Long, RoleGroupEntity> roleGroupMap = lookupService.roleGroupMap();
        Map<Long, TeamEntity> teamByRoleGroupId = lookupService.teamByRoleGroupId();
        return staffMapper.selectList(query).stream()
            .map(staff -> toDto(staff, roleGroupMap.get(staff.getRoleGroupId()), teamByRoleGroupId.get(staff.getRoleGroupId())))
            .toList();
    }

    public WorkspaceStaffDto getStaff(Long id) {
        StaffEntity entity = staffMapper.selectById(id);
        if (entity == null) {
            throw new ResourceNotFoundException("Staff", "id", id);
        }
        Map<Long, RoleGroupEntity> roleGroupMap = lookupService.roleGroupMap();
        Map<Long, TeamEntity> teamByRoleGroupId = lookupService.teamByRoleGroupId();
        return toDto(entity, roleGroupMap.get(entity.getRoleGroupId()), teamByRoleGroupId.get(entity.getRoleGroupId()));
    }

    @Transactional
    public WorkspaceStaffDto createStaff(WorkspaceStaffUpsertRequest request) {
        StaffEntity entity = new StaffEntity();
        apply(entity, request);
        staffMapper.insert(entity);
        return getStaff(entity.getId());
    }

    @Transactional
    public WorkspaceStaffDto updateStaff(Long id, WorkspaceStaffUpsertRequest request) {
        StaffEntity entity = staffMapper.selectById(id);
        if (entity == null) {
            throw new ResourceNotFoundException("Staff", "id", id);
        }
        apply(entity, request);
        staffMapper.updateById(entity);
        return getStaff(id);
    }

    @Transactional
    public void deleteStaff(Long id) {
        StaffEntity entity = staffMapper.selectById(id);
        if (entity == null) {
            throw new ResourceNotFoundException("Staff", "id", id);
        }
        staffMapper.deleteById(id);
    }

    public List<com.support.server.supportrosterserver.dto.StaffDto> listViewerStaff() {
        return staffMapper.selectList(Wrappers.<StaffEntity>lambdaQuery()
                .eq(StaffEntity::getStatus, "Active")
                .orderByAsc(StaffEntity::getName))
            .stream()
            .map(entity -> new com.support.server.supportrosterserver.dto.StaffDto(
                entity.getId(),
                entity.getName(),
                entity.getAvatar(),
                entity.getEmail(),
                entity.getPhone(),
                entity.getSlack(),
                entity.getRegion(),
                entity.getPhone(),
                List.of()
            ))
            .toList();
    }

    public com.support.server.supportrosterserver.dto.StaffDto getViewerStaff(Long id) {
        StaffEntity entity = staffMapper.selectById(id);
        if (entity == null) {
            return null;
        }
        RoleGroupEntity roleGroup = lookupService.roleGroupMap().get(entity.getRoleGroupId());
        return new com.support.server.supportrosterserver.dto.StaffDto(
            entity.getId(),
            entity.getName(),
            entity.getAvatar(),
            entity.getEmail(),
            entity.getPhone(),
            entity.getSlack(),
            entity.getRegion(),
            entity.getPhone(),
            roleGroup == null ? List.of() : List.of(roleGroup.getCode())
        );
    }

    private WorkspaceStaffDto toDto(StaffEntity entity, RoleGroupEntity roleGroup, TeamEntity team) {
        YearMonth currentMonth = YearMonth.now();
        List<RosterAssignmentEntity> assignments = rosterAssignmentMapper.selectList(Wrappers.<RosterAssignmentEntity>lambdaQuery()
            .eq(RosterAssignmentEntity::getStaffId, entity.getId())
            .between(RosterAssignmentEntity::getAssignmentDate, currentMonth.atDay(1), currentMonth.atEndOfMonth()));
        Map<String, String> tags = new LinkedHashMap<>();
        if (team != null) {
            tags.put("team", team.getName());
        }
        if (roleGroup != null) {
            tags.put("roleGroup", roleGroup.getCode());
        }
        tags.put("assignments", assignments.size() + " shifts this month");
        return new WorkspaceStaffDto(
            entity.getId(),
            entity.getStaffCode(),
            entity.getName(),
            entity.getEmail(),
            entity.getPhone(),
            entity.getSlack(),
            entity.getRegion(),
            entity.getTimezone(),
            entity.getRoleName(),
            team == null ? null : team.getName(),
            entity.getRoleGroupId(),
            roleGroup == null ? null : roleGroup.getCode(),
            roleGroup == null ? null : roleGroup.getName(),
            entity.getStatus(),
            entity.getAvatar(),
            entity.getNotes(),
            new ArrayList<>(tags.values())
        );
    }

    private void apply(StaffEntity entity, WorkspaceStaffUpsertRequest request) {
        lookupService.requireRoleGroup(request.getRoleGroupId());
        entity.setStaffCode(request.getStaffCode());
        entity.setName(request.getName());
        entity.setEmail(request.getEmail());
        entity.setPhone(request.getPhone());
        entity.setSlack(request.getSlack());
        entity.setRegion(request.getRegion());
        entity.setTimezone(request.getTimezone());
        entity.setRoleName(request.getRoleName());
        entity.setRoleGroupId(request.getRoleGroupId());
        entity.setStatus(request.getStatus() == null || request.getStatus().isBlank() ? "Active" : request.getStatus());
        entity.setAvatar(request.getAvatar());
        entity.setNotes(request.getNotes());
    }
}