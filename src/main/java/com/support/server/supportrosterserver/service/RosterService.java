package com.support.server.supportrosterserver.service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.support.server.supportrosterserver.dto.ContactDto;
import com.support.server.supportrosterserver.dto.ShiftDto;
import com.support.server.supportrosterserver.dto.TeamDto;
import com.support.server.supportrosterserver.entity.workspace.RosterAssignmentEntity;
import com.support.server.supportrosterserver.entity.workspace.ShiftDefinitionEntity;
import com.support.server.supportrosterserver.entity.workspace.StaffEntity;
import com.support.server.supportrosterserver.entity.workspace.TeamEntity;
import com.support.server.supportrosterserver.mapper.RosterAssignmentMapper;
import com.support.server.supportrosterserver.mapper.ShiftDefinitionMapper;
import com.support.server.supportrosterserver.mapper.StaffMapper;
import com.support.server.supportrosterserver.service.workspace.WorkspaceLookupService;
import com.support.server.supportrosterserver.service.workspace.WorkspaceTeamService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RosterService {

    private final WorkspaceTeamService workspaceTeamService;
    private final WorkspaceLookupService lookupService;
    private final RosterAssignmentMapper rosterAssignmentMapper;
    private final ShiftDefinitionMapper shiftDefinitionMapper;
    private final StaffMapper staffMapper;

    public List<TeamDto> getAllTeams() {
        return workspaceTeamService.listViewerTeams();
    }

    public List<ShiftDto> getShiftsByDate(LocalDate date, String teamId, String timezone) {
        Map<Long, TeamEntity> teamMap = lookupService.teamMap();
        List<RosterAssignmentEntity> assignments = rosterAssignmentMapper.selectList(Wrappers.<RosterAssignmentEntity>lambdaQuery()
            .between(RosterAssignmentEntity::getAssignmentDate, date.minusDays(1), date)
            .orderByAsc(RosterAssignmentEntity::getAssignmentDate));
        ZoneId targetZone = ZoneId.of(timezone == null || timezone.isBlank() ? "UTC" : timezone);

        return assignments.stream()
            .map(assignment -> toShiftDto(assignment, teamMap, targetZone))
            .filter(java.util.Objects::nonNull)
            .filter(shift -> teamId == null || teamId.isBlank() || teamId.equals(shift.getTeamId()))
            .filter(shift -> intersectsDate(shift.getStart(), shift.getEnd(), date, targetZone))
            .toList();
    }

    public ShiftDto getShiftById(String id) {
        try {
            RosterAssignmentEntity assignment = rosterAssignmentMapper.selectById(Long.parseLong(id));
            if (assignment == null) {
                return null;
            }
            return toShiftDto(assignment, lookupService.teamMap(), ZoneId.of("UTC"));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private ShiftDto toShiftDto(RosterAssignmentEntity assignment, Map<Long, TeamEntity> teamMap, ZoneId targetZone) {
        ShiftDefinitionEntity shiftDefinition = shiftDefinitionMapper.selectById(assignment.getShiftDefinitionId());
        StaffEntity staff = staffMapper.selectById(assignment.getStaffId());
        TeamEntity team = teamMap.get(assignment.getTeamId());
        if (shiftDefinition == null || staff == null || team == null || !Boolean.TRUE.equals(team.getVisible()) || !Boolean.TRUE.equals(shiftDefinition.getVisible()) || !Boolean.TRUE.equals(shiftDefinition.getPrimaryShift())) {
            return null;
        }

        ZoneId sourceZone = toZoneId(shiftDefinition.getTimezone());
        ZonedDateTime start = assignment.getAssignmentDate().atTime(shiftDefinition.getStartTime() == null ? LocalTime.MIDNIGHT : shiftDefinition.getStartTime()).atZone(sourceZone);
        ZonedDateTime end = assignment.getAssignmentDate().atTime(shiftDefinition.getEndTime() == null ? LocalTime.MIDNIGHT : shiftDefinition.getEndTime()).atZone(sourceZone);
        if (shiftDefinition.getEndTime() != null && shiftDefinition.getStartTime() != null && shiftDefinition.getEndTime().isBefore(shiftDefinition.getStartTime())) {
            end = end.plusDays(1);
        }

        ShiftDto dto = new ShiftDto();
        dto.setId(String.valueOf(assignment.getId()));
        dto.setTeamId(team.getTeamCode());
        dto.setStaffId(staff.getId());
        dto.setUserName(staff.getName());
        dto.setUserAvatar(staff.getAvatar());
        dto.setCode(assignment.getShiftCode());
        dto.setMeaning(shiftDefinition.getMeaning());
        dto.setStart(start.withZoneSameInstant(targetZone).toOffsetDateTime());
        dto.setEnd(end.withZoneSameInstant(targetZone).toOffsetDateTime());
        dto.setTimezone(shiftDefinition.getTimezone());
        dto.setIsPrimary(shiftDefinition.getPrimaryShift());
        dto.setShowOnRoster(shiftDefinition.getVisible());
        dto.setColorHex(shiftDefinition.getColorHex());
        dto.setRemark(shiftDefinition.getRemark());
        dto.setContact(new ContactDto(staff.getSlack(), staff.getEmail(), staff.getPhone()));
        return dto;
    }

    private boolean intersectsDate(OffsetDateTime start, OffsetDateTime end, LocalDate date, ZoneId targetZone) {
        LocalDate startDate = start.atZoneSameInstant(targetZone).toLocalDate();
        LocalDate endDate = end.atZoneSameInstant(targetZone).toLocalDate();
        return !date.isBefore(startDate) && !date.isAfter(endDate);
    }

    private ZoneId toZoneId(String timezone) {
        return switch (timezone) {
            case "HKT" -> ZoneId.of("Asia/Hong_Kong");
            case "IST" -> ZoneId.of("Asia/Kolkata");
            case "INT" -> ZoneId.of("UTC");
            default -> timezone != null && timezone.contains("/") ? ZoneId.of(timezone) : ZoneId.of("UTC");
        };
    }
}
