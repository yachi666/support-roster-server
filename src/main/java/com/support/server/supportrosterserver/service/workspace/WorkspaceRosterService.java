package com.support.server.supportrosterserver.service.workspace;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceMonthlyRosterResponse;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceRosterCellUpdateRequest;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceRosterGroupDto;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceRosterPersonDto;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceRosterSaveRequest;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceRosterShiftDetailDto;
import com.support.server.supportrosterserver.entity.workspace.RosterAssignmentEntity;
import com.support.server.supportrosterserver.entity.workspace.ShiftDefinitionEntity;
import com.support.server.supportrosterserver.entity.workspace.ShiftDefinitionTeamRelEntity;
import com.support.server.supportrosterserver.entity.workspace.StaffEntity;
import com.support.server.supportrosterserver.entity.workspace.TeamEntity;
import com.support.server.supportrosterserver.exception.BadRequestException;
import com.support.server.supportrosterserver.exception.ResourceNotFoundException;
import com.support.server.supportrosterserver.mapper.RosterAssignmentMapper;
import com.support.server.supportrosterserver.mapper.ShiftDefinitionMapper;
import com.support.server.supportrosterserver.mapper.ShiftDefinitionTeamRelMapper;
import com.support.server.supportrosterserver.mapper.StaffMapper;
import com.support.server.supportrosterserver.service.AvatarUrlResolver;
import com.support.server.supportrosterserver.service.auth.AuthContextService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WorkspaceRosterService {

    private final StaffMapper staffMapper;
    private final ShiftDefinitionMapper shiftDefinitionMapper;
    private final ShiftDefinitionTeamRelMapper shiftDefinitionTeamRelMapper;
    private final RosterAssignmentMapper rosterAssignmentMapper;
    private final WorkspaceLookupService lookupService;
    private final AvatarUrlResolver avatarUrlResolver;
    private final AuthContextService authContextService;
    private final WorkspaceShiftTimeSupport shiftTimeSupport;

    public WorkspaceMonthlyRosterResponse getMonthlyRoster(Integer year, Integer month) {
        YearMonth targetMonth = resolveMonth(year, month);
        Map<String, String> scheduleMap = new HashMap<>();

        List<RosterAssignmentEntity> assignments = rosterAssignmentMapper.selectList(Wrappers.<RosterAssignmentEntity>lambdaQuery()
            .between(RosterAssignmentEntity::getAssignmentDate, targetMonth.atDay(1), targetMonth.atEndOfMonth()));
        Map<Long, ShiftDefinitionEntity> shiftDefinitionById = shiftDefinitionMapper.selectList(Wrappers.<ShiftDefinitionEntity>lambdaQuery())
            .stream()
            .collect(Collectors.toMap(ShiftDefinitionEntity::getId, definition -> definition));
        for (RosterAssignmentEntity assignment : assignments) {
            ShiftDefinitionEntity definition = shiftDefinitionById.get(assignment.getShiftDefinitionId());
            String displayCode = definition == null || definition.getCode() == null || definition.getCode().isBlank()
                ? assignment.getShiftCode()
                : definition.getCode();
            scheduleMap.put(assignment.getStaffId() + "|" + assignment.getAssignmentDate().getDayOfMonth(), displayCode);
        }

        Map<Long, TeamEntity> teamMap = lookupService.teamMap();
        List<Long> readableTeamIds = authContextService.readableTeamIds();
        Map<Long, List<StaffEntity>> staffByTeamId = new LinkedHashMap<>();
        for (StaffEntity staff : staffMapper.selectList(Wrappers.<StaffEntity>lambdaQuery().orderByAsc(StaffEntity::getName))) {
            TeamEntity team = staff.getTeamId() == null ? null : teamMap.get(staff.getTeamId());
            if (team != null && Boolean.TRUE.equals(team.getVisible()) && readableTeamIds.contains(team.getId())) {
                staffByTeamId.computeIfAbsent(team.getId(), ignored -> new ArrayList<>()).add(staff);
            }
        }

        List<WorkspaceRosterGroupDto> groups = new ArrayList<>();
        for (TeamEntity team : lookupService.listTeams()) {
            if (!Boolean.TRUE.equals(team.getVisible()) || !readableTeamIds.contains(team.getId())) {
                continue;
            }
            List<WorkspaceRosterPersonDto> persons = new ArrayList<>();
            for (StaffEntity staff : staffByTeamId.getOrDefault(team.getId(), List.of())) {
                Map<Integer, String> schedule = new LinkedHashMap<>();
                for (int day = 1; day <= targetMonth.lengthOfMonth(); day++) {
                    schedule.put(day, scheduleMap.getOrDefault(staff.getId() + "|" + day, ""));
                }
                persons.add(new WorkspaceRosterPersonDto(
                    staff.getId(),
                    staff.getName(),
                    avatarUrlResolver.resolve(staff.getStaffCode()),
                    staff.getRoleName(),
                    staff.getTeamId(),
                    schedule
                ));
            }
            groups.add(new WorkspaceRosterGroupDto(team.getId(), team.getName(), team.getColor(), persons));
        }

        List<ShiftDefinitionEntity> visibleShiftDefinitions = shiftDefinitionById.values().stream()
            .filter(shiftDefinition -> Boolean.TRUE.equals(shiftDefinition.getVisible()))
            .sorted(java.util.Comparator
                .comparing(ShiftDefinitionEntity::getTeamId, java.util.Comparator.nullsLast(Long::compareTo))
                .thenComparing(ShiftDefinitionEntity::getCode, java.util.Comparator.nullsLast(String::compareTo)))
            .toList();
        Map<Long, List<Long>> teamIdsByShiftDefinitionId = loadTeamIdsByShiftDefinitionId(visibleShiftDefinitions.stream()
            .map(ShiftDefinitionEntity::getId)
            .toList());

        Map<Long, List<String>> shiftCodeOptionsByTeam = new LinkedHashMap<>();
        Map<String, String> shiftCodeColorMap = new HashMap<>();
        Map<Long, Map<String, WorkspaceRosterShiftDetailDto>> shiftDetailsByTeam = new LinkedHashMap<>();

        for (ShiftDefinitionEntity shiftDefinition : visibleShiftDefinitions) {
            if (shiftDefinition.getCode() == null || shiftDefinition.getCode().isBlank()) {
                continue;
            }

            WorkspaceRosterShiftDetailDto detail = toShiftDetail(shiftDefinition);
            if (shiftDefinition.getColorHex() != null) {
                shiftCodeColorMap.putIfAbsent(shiftDefinition.getCode(), shiftDefinition.getColorHex());
            }

            for (Long teamId : teamIdsByShiftDefinitionId.getOrDefault(shiftDefinition.getId(), List.of())) {
                if (!readableTeamIds.contains(teamId)) {
                    continue;
                }
                shiftCodeOptionsByTeam.computeIfAbsent(teamId, ignored -> new ArrayList<>());
                if (!shiftCodeOptionsByTeam.get(teamId).contains(shiftDefinition.getCode())) {
                    shiftCodeOptionsByTeam.get(teamId).add(shiftDefinition.getCode());
                }
                shiftDetailsByTeam.computeIfAbsent(teamId, ignored -> new LinkedHashMap<>())
                    .put(shiftDefinition.getCode(), detail);
            }
        }

        List<String> shiftOptions = shiftCodeOptionsByTeam.values().stream()
            .flatMap(List::stream)
            .distinct()
            .toList();

        return new WorkspaceMonthlyRosterResponse(
            targetMonth.getYear(),
            targetMonth.getMonthValue(),
            groups,
            shiftOptions,
            shiftCodeOptionsByTeam,
            shiftCodeColorMap,
            shiftDetailsByTeam,
            ""
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
            authContextService.requireWritableTeam(staff.getTeamId());

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

            ShiftDefinitionEntity shiftDefinition = findShiftDefinitionForTeamAndCode(staff.getTeamId(), shiftCode);
            if (shiftDefinition == null) {
                throw new BadRequestException("Shift code '" + shiftCode + "' does not exist for staff team.");
            }

            TeamEntity team = staff.getTeamId() == null ? null : lookupService.teamMap().get(staff.getTeamId());
            if (team == null) {
                throw new BadRequestException("Staff team does not exist.");
            }

            if (existing == null) {
                existing = new RosterAssignmentEntity();
                existing.setStaffId(staff.getId());
                existing.setAssignmentDate(date);
            }
            existing.setRoleGroupId(null);
            existing.setTeamId(team.getId());
            existing.setShiftDefinitionId(shiftDefinition.getId());
            existing.setShiftCode(shiftDefinition.getCode());
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

    private Map<Long, List<Long>> loadTeamIdsByShiftDefinitionId(List<Long> shiftDefinitionIds) {
        if (shiftDefinitionIds.isEmpty()) {
            return Map.of();
        }

        return shiftDefinitionTeamRelMapper.selectList(Wrappers.<ShiftDefinitionTeamRelEntity>lambdaQuery()
                .in(ShiftDefinitionTeamRelEntity::getShiftDefinitionId, shiftDefinitionIds)
                .orderByAsc(ShiftDefinitionTeamRelEntity::getTeamId))
            .stream()
            .collect(Collectors.groupingBy(
                ShiftDefinitionTeamRelEntity::getShiftDefinitionId,
                LinkedHashMap::new,
                Collectors.mapping(ShiftDefinitionTeamRelEntity::getTeamId, Collectors.toList())
            ));
    }

    private ShiftDefinitionEntity findShiftDefinitionForTeamAndCode(Long teamId, String shiftCode) {
        if (teamId == null || shiftCode == null || shiftCode.isBlank()) {
            return null;
        }

        List<ShiftDefinitionEntity> candidates = shiftDefinitionMapper.selectList(Wrappers.<ShiftDefinitionEntity>lambdaQuery()
            .eq(ShiftDefinitionEntity::getCode, shiftCode)
            .eq(ShiftDefinitionEntity::getVisible, true));
        if (candidates.isEmpty()) {
            return null;
        }

        Set<Long> candidateIds = candidates.stream().map(ShiftDefinitionEntity::getId).collect(Collectors.toSet());
        Set<Long> matchingIds = shiftDefinitionTeamRelMapper.selectList(Wrappers.<ShiftDefinitionTeamRelEntity>lambdaQuery()
                .eq(ShiftDefinitionTeamRelEntity::getTeamId, teamId)
                .in(ShiftDefinitionTeamRelEntity::getShiftDefinitionId, candidateIds))
            .stream()
            .map(ShiftDefinitionTeamRelEntity::getShiftDefinitionId)
            .collect(Collectors.toSet());

        return candidates.stream()
            .filter(candidate -> matchingIds.contains(candidate.getId()))
            .findFirst()
            .orElse(null);
    }

    private WorkspaceRosterShiftDetailDto toShiftDetail(ShiftDefinitionEntity shiftDefinition) {
        LocalTime startTime = shiftDefinition.getStartTime();
        int durationMinutes = shiftTimeSupport.resolveDurationMinutes(shiftDefinition);
        LocalTime endTime = shiftTimeSupport.deriveEndTime(startTime, durationMinutes == 0 ? null : durationMinutes);
        return new WorkspaceRosterShiftDetailDto(
            shiftDefinition.getId(),
            shiftDefinition.getCode(),
            shiftDefinition.getMeaning(),
            startTime,
            endTime,
            durationMinutes == 0 ? null : durationMinutes,
            lookupService.normalizeWorkspaceTimezone(shiftDefinition.getTimezone()),
            shiftDefinition.getPrimaryShift(),
            shiftDefinition.getColorHex(),
            shiftTimeSupport.isOvernight(startTime, durationMinutes == 0 ? null : durationMinutes)
        );
    }
}
