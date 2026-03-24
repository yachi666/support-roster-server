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
import com.support.server.supportrosterserver.entity.auth.WorkspaceAccountEntity;
import com.support.server.supportrosterserver.entity.workspace.RosterAssignmentEntity;
import com.support.server.supportrosterserver.entity.workspace.StaffEntity;
import com.support.server.supportrosterserver.entity.workspace.TeamEntity;
import com.support.server.supportrosterserver.mapper.RosterAssignmentMapper;
import com.support.server.supportrosterserver.mapper.StaffMapper;
import com.support.server.supportrosterserver.mapper.WorkspaceAccountMapper;
import com.support.server.supportrosterserver.exception.ResourceNotFoundException;
import com.support.server.supportrosterserver.service.AvatarUrlResolver;
import com.support.server.supportrosterserver.service.auth.AuthContextService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WorkspaceStaffService {

    private final AvatarUrlResolver avatarUrlResolver;
    private final StaffMapper staffMapper;
    private final RosterAssignmentMapper rosterAssignmentMapper;
    private final WorkspaceLookupService lookupService;
    private final WorkspaceAccountMapper workspaceAccountMapper;
    private final AuthContextService authContextService;

    public List<WorkspaceStaffDto> listStaff(String keyword) {
        LambdaQueryWrapper<StaffEntity> query = Wrappers.<StaffEntity>lambdaQuery()
            .orderByAsc(StaffEntity::getStaffCode)
            .orderByAsc(StaffEntity::getName);
        List<Long> readableTeamIds = authContextService.readableTeamIds();
        if (!readableTeamIds.isEmpty()) {
            query.in(StaffEntity::getTeamId, readableTeamIds);
        }
        if (keyword != null && !keyword.isBlank()) {
            query.and(wrapper -> wrapper
                .like(StaffEntity::getName, keyword)
                .or().like(StaffEntity::getEmail, keyword)
                .or().like(StaffEntity::getStaffCode, keyword)
                .or().like(StaffEntity::getRoleName, keyword)
                .or().like(StaffEntity::getRegion, keyword));
        }

        Map<Long, TeamEntity> teamMap = lookupService.teamMap();
        return staffMapper.selectList(query).stream()
            .map(staff -> toDto(staff, teamMap.get(staff.getTeamId())))
            .toList();
    }

    public WorkspaceStaffDto getStaff(Long id) {
        StaffEntity entity = staffMapper.selectById(id);
        if (entity == null) {
            throw new ResourceNotFoundException("Staff", "id", id);
        }
        authContextService.requireReadableTeam(entity.getTeamId());
        return toDto(entity, lookupService.teamMap().get(entity.getTeamId()));
    }

    @Transactional
    public WorkspaceStaffDto createStaff(WorkspaceStaffUpsertRequest request) {
        authContextService.requireWritableTeam(request.getTeamId());
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
        authContextService.requireWritableTeam(entity.getTeamId());
        authContextService.requireWritableTeam(request.getTeamId());
        String previousStaffCode = entity.getStaffCode();
        apply(entity, request);
        staffMapper.updateById(entity);
        syncLinkedAccountStaffCode(entity.getId(), previousStaffCode, entity.getStaffCode());
        return getStaff(id);
    }

    @Transactional
    public void deleteStaff(Long id) {
        StaffEntity entity = staffMapper.selectById(id);
        if (entity == null) {
            throw new ResourceNotFoundException("Staff", "id", id);
        }
        authContextService.requireWritableTeam(entity.getTeamId());
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
                avatarUrlResolver.resolve(entity.getStaffCode()),
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
        TeamEntity team = entity.getTeamId() == null ? null : lookupService.teamMap().get(entity.getTeamId());
        return new com.support.server.supportrosterserver.dto.StaffDto(
            entity.getId(),
            entity.getName(),
            avatarUrlResolver.resolve(entity.getStaffCode()),
            entity.getEmail(),
            entity.getPhone(),
            entity.getSlack(),
            entity.getRegion(),
            entity.getPhone(),
            team == null ? List.of() : List.of(team.getName())
        );
    }

    private WorkspaceStaffDto toDto(StaffEntity entity, TeamEntity team) {
        YearMonth currentMonth = YearMonth.now();
        List<RosterAssignmentEntity> assignments = rosterAssignmentMapper.selectList(Wrappers.<RosterAssignmentEntity>lambdaQuery()
            .eq(RosterAssignmentEntity::getStaffId, entity.getId())
            .between(RosterAssignmentEntity::getAssignmentDate, currentMonth.atDay(1), currentMonth.atEndOfMonth()));
        Map<String, String> tags = new LinkedHashMap<>();
        if (team != null) {
            tags.put("team", team.getName());
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
            lookupService.normalizeWorkspaceTimezone(entity.getTimezone()),
            entity.getRoleName(),
            entity.getTeamId(),
            team == null ? null : team.getName(),
            entity.getStatus(),
            avatarUrlResolver.resolve(entity.getStaffCode()),
            entity.getNotes(),
            new ArrayList<>(tags.values())
        );
    }

    private void apply(StaffEntity entity, WorkspaceStaffUpsertRequest request) {
        lookupService.requireTeam(request.getTeamId());
        entity.setStaffCode(request.getStaffCode());
        entity.setName(request.getName());
        entity.setEmail(request.getEmail());
        entity.setPhone(request.getPhone());
        entity.setSlack(request.getSlack());
        entity.setRegion(request.getRegion());
        entity.setTimezone(lookupService.normalizeWorkspaceTimezone(request.getTimezone()));
        entity.setRoleName(request.getRoleName());
        entity.setTeamId(request.getTeamId());
        entity.setRoleGroupId(null);
        entity.setStatus(request.getStatus() == null || request.getStatus().isBlank() ? "Active" : request.getStatus());
        entity.setAvatar(request.getAvatar());
        entity.setNotes(request.getNotes());
    }

    private void syncLinkedAccountStaffCode(Long staffId, String previousStaffCode, String currentStaffCode) {
        if (staffId == null || java.util.Objects.equals(previousStaffCode, currentStaffCode)) {
            return;
        }
        WorkspaceAccountEntity account = workspaceAccountMapper.selectOne(Wrappers.<WorkspaceAccountEntity>lambdaQuery()
            .eq(WorkspaceAccountEntity::getStaffId, staffId)
            .last("limit 1"));
        if (account == null) {
            return;
        }
        account.setStaffCode(currentStaffCode);
        workspaceAccountMapper.updateById(account);
    }
}
