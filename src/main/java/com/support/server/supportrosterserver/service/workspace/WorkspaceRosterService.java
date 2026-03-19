package com.support.server.supportrosterserver.service.workspace;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceMonthlyRosterResponse;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceRosterCellUpdateRequest;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceRosterGroupDto;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceRosterPersonDto;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceRosterSaveRequest;
import com.support.server.supportrosterserver.entity.workspace.RoleGroupEntity;
import com.support.server.supportrosterserver.entity.workspace.RosterAssignmentEntity;
import com.support.server.supportrosterserver.entity.workspace.ShiftDefinitionEntity;
import com.support.server.supportrosterserver.entity.workspace.StaffEntity;
import com.support.server.supportrosterserver.entity.workspace.TeamEntity;
import com.support.server.supportrosterserver.exception.BadRequestException;
import com.support.server.supportrosterserver.exception.ResourceNotFoundException;
import com.support.server.supportrosterserver.mapper.RosterAssignmentMapper;
import com.support.server.supportrosterserver.mapper.ShiftDefinitionMapper;
import com.support.server.supportrosterserver.mapper.StaffMapper;
import com.support.server.supportrosterserver.service.AvatarUrlResolver;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WorkspaceRosterService {

    private final StaffMapper staffMapper;
    private final ShiftDefinitionMapper shiftDefinitionMapper;
    private final RosterAssignmentMapper rosterAssignmentMapper;
    private final WorkspaceLookupService lookupService;
    private final WorkspaceValidationService validationService;
    private final AvatarUrlResolver avatarUrlResolver;

    public WorkspaceMonthlyRosterResponse getMonthlyRoster(Integer year, Integer month) {
        YearMonth targetMonth = resolveMonth(year, month);
        Map<Long, RoleGroupEntity> roleGroupMap = lookupService.roleGroupMap();
        Map<Long, TeamEntity> teamByRoleGroupId = lookupService.teamByRoleGroupId();
        Map<String, String> scheduleMap = new HashMap<>();

        List<RosterAssignmentEntity> assignments = rosterAssignmentMapper.selectList(Wrappers.<RosterAssignmentEntity>lambdaQuery()
            .between(RosterAssignmentEntity::getAssignmentDate, targetMonth.atDay(1), targetMonth.atEndOfMonth()));
        for (RosterAssignmentEntity assignment : assignments) {
            scheduleMap.put(assignment.getStaffId() + "|" + assignment.getAssignmentDate().getDayOfMonth(), assignment.getShiftCode());
        }

        Map<Long, List<StaffEntity>> staffByTeamId = new LinkedHashMap<>();
        for (StaffEntity staff : staffMapper.selectList(Wrappers.<StaffEntity>lambdaQuery().orderByAsc(StaffEntity::getName))) {
            TeamEntity team = teamByRoleGroupId.get(staff.getRoleGroupId());
            if (team != null && Boolean.TRUE.equals(team.getVisible())) {
                staffByTeamId.computeIfAbsent(team.getId(), ignored -> new ArrayList<>()).add(staff);
            }
        }

        List<WorkspaceRosterGroupDto> groups = new ArrayList<>();
        for (TeamEntity team : lookupService.listTeams()) {
            if (!Boolean.TRUE.equals(team.getVisible())) {
                continue;
            }
            List<WorkspaceRosterPersonDto> persons = new ArrayList<>();
            for (StaffEntity staff : staffByTeamId.getOrDefault(team.getId(), List.of())) {
                Map<Integer, String> schedule = new LinkedHashMap<>();
                for (int day = 1; day <= targetMonth.lengthOfMonth(); day++) {
                    schedule.put(day, scheduleMap.getOrDefault(staff.getId() + "|" + day, ""));
                }
                RoleGroupEntity roleGroup = roleGroupMap.get(staff.getRoleGroupId());
                persons.add(new WorkspaceRosterPersonDto(
                    staff.getId(),
                    staff.getName(),
                    avatarUrlResolver.resolve(staff.getStaffCode()),
                    roleGroup == null ? staff.getRoleName() : roleGroup.getName(),
                    staff.getRoleGroupId(),
                    schedule
                ));
            }
            groups.add(new WorkspaceRosterGroupDto(team.getId(), team.getName(), team.getColor(), persons));
        }

        List<ShiftDefinitionEntity> visibleShiftDefinitions = shiftDefinitionMapper.selectList(Wrappers.<ShiftDefinitionEntity>lambdaQuery()
                .eq(ShiftDefinitionEntity::getVisible, true)
                .orderByAsc(ShiftDefinitionEntity::getRoleGroupId)
                .orderByAsc(ShiftDefinitionEntity::getCode));

        List<String> shiftOptions = visibleShiftDefinitions.stream()
            .map(ShiftDefinitionEntity::getCode)
            .distinct()
            .toList();

        Map<Long, List<String>> shiftCodeOptionsByRoleGroup = visibleShiftDefinitions.stream()
            .filter(shiftDefinition -> shiftDefinition.getRoleGroupId() != null)
            .collect(Collectors.groupingBy(
                ShiftDefinitionEntity::getRoleGroupId,
                LinkedHashMap::new,
                Collectors.mapping(ShiftDefinitionEntity::getCode, Collectors.collectingAndThen(Collectors.toList(), codes -> codes.stream()
                    .filter(code -> code != null && !code.isBlank())
                    .distinct()
                    .toList()))
            ));

        Map<String, String> shiftCodeColorMap = new HashMap<>();
        for (ShiftDefinitionEntity shiftDef : visibleShiftDefinitions) {
            if (shiftDef.getCode() != null && shiftDef.getColorHex() != null) {
                shiftCodeColorMap.put(shiftDef.getCode(), shiftDef.getColorHex());
            }
        }

        String validationWarning = validationService.validateLiveData(targetMonth).stream()
            .filter(issue -> "high".equalsIgnoreCase(issue.getSeverity()))
            .map(issue -> issue.getDescription())
            .findFirst()
            .orElse("");

        return new WorkspaceMonthlyRosterResponse(
            targetMonth.getYear(),
            targetMonth.getMonthValue(),
            groups,
            shiftOptions,
            shiftCodeOptionsByRoleGroup,
            shiftCodeColorMap,
            validationWarning
        );
    }

    @Transactional
    public WorkspaceMonthlyRosterResponse saveMonthlyRoster(WorkspaceRosterSaveRequest request) {
        YearMonth targetMonth = resolveMonth(request.getYear(), request.getMonth());
        for (WorkspaceRosterCellUpdateRequest update : request.getUpdates()) {
            StaffEntity staff = staffMapper.selectById(update.getStaffId());
            if (staff == null) {
                throw new ResourceNotFoundException("Staff", "id", update.getStaffId());
            }

            LocalDate date = targetMonth.atDay(update.getDay());
            RosterAssignmentEntity existing = rosterAssignmentMapper.selectOne(Wrappers.<RosterAssignmentEntity>lambdaQuery()
                .eq(RosterAssignmentEntity::getStaffId, update.getStaffId())
                .eq(RosterAssignmentEntity::getAssignmentDate, date)
                .last("limit 1"));

            String shiftCode = update.getShiftCode();
            if (shiftCode == null || shiftCode.isBlank() || "Clear".equalsIgnoreCase(shiftCode)) {
                if (existing != null) {
                    rosterAssignmentMapper.deleteById(existing.getId());
                }
                continue;
            }

            ShiftDefinitionEntity shiftDefinition = shiftDefinitionMapper.selectOne(Wrappers.<ShiftDefinitionEntity>lambdaQuery()
                .eq(ShiftDefinitionEntity::getRoleGroupId, staff.getRoleGroupId())
                .eq(ShiftDefinitionEntity::getCode, shiftCode)
                .last("limit 1"));
            if (shiftDefinition == null) {
                throw new BadRequestException("Shift code '" + shiftCode + "' does not exist for staff role group.");
            }

            TeamEntity team = lookupService.teamByRoleGroupId().get(staff.getRoleGroupId());
            if (team == null) {
                throw new BadRequestException("Staff role group is not mapped to any team.");
            }

            if (existing == null) {
                existing = new RosterAssignmentEntity();
                existing.setStaffId(staff.getId());
                existing.setAssignmentDate(date);
            }
            existing.setRoleGroupId(staff.getRoleGroupId());
            existing.setTeamId(team.getId());
            existing.setShiftDefinitionId(shiftDefinition.getId());
            existing.setShiftCode(shiftCode);
            existing.setSourceType("MANUAL");
            existing.setNotes(null);

            if (existing.getId() == null) {
                rosterAssignmentMapper.insert(existing);
            } else {
                rosterAssignmentMapper.updateById(existing);
            }
        }
        return getMonthlyRoster(targetMonth.getYear(), targetMonth.getMonthValue());
    }

    private YearMonth resolveMonth(Integer year, Integer month) {
        YearMonth now = YearMonth.now();
        return YearMonth.of(year == null ? now.getYear() : year, month == null ? now.getMonthValue() : month);
    }
}