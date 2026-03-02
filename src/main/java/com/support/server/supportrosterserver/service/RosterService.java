package com.support.server.supportrosterserver.service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import com.support.server.supportrosterserver.dto.ContactDto;
import com.support.server.supportrosterserver.dto.ShiftDto;
import com.support.server.supportrosterserver.dto.TeamDto;
import com.support.server.supportrosterserver.entity.ShiftDefinition;
import com.support.server.supportrosterserver.entity.StaffShift;
import com.support.server.supportrosterserver.repository.RosterRepository;

@Service
@RequiredArgsConstructor
public class RosterService {

    private final RosterRepository rosterRepository;

    private static final Map<String, TeamDto> TEAM_MAPPING = Map.ofEntries(
        Map.entry("Incident_Manager_China", new TeamDto("incident-manager", "Incident Manager", "orange", 0)),
        Map.entry("Incident_Manager_India", new TeamDto("incident-manager", "Incident Manager", "orange", 0)),
        Map.entry("L1_China", new TeamDto("l1", "L1", "blue", 1)),
        Map.entry("L1_India", new TeamDto("l1", "L1", "blue", 1)),
        Map.entry("AP_L2", new TeamDto("ap-l2", "AP L2", "green", 2)),
        Map.entry("EMEA_L2", new TeamDto("emea-l2", "EMEA L2", "purple", 3)),
        Map.entry("MDP_L2", new TeamDto("mdp-l2", "MDP L2", "red", 4)),
        Map.entry("AP_L2+", new TeamDto("ap-l2-plus", "AP L2+", "green", 5)),
        Map.entry("AP_L3", new TeamDto("ap-l3", "AP L3", "green", 6)),
        Map.entry("DevOps_China", new TeamDto("devops", "DevOps", "orange", 7)),
        Map.entry("DevOps_India", new TeamDto("devops", "DevOps", "orange", 7))
    );

    private static final Set<String> PRIMARY_CODES = Set.of("OC", "DS", "NS", "A", "B", "D");

    private static final List<String> AVATARS = List.of(
        "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=100&auto=format&fit=crop&q=60",
        "https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=100&auto=format&fit=crop&q=60",
        "https://images.unsplash.com/photo-1599566150163-29194dcaad36?w=100&auto=format&fit=crop&q=60",
        "https://images.unsplash.com/photo-1438761681033-6461ffad8d80?w=100&auto=format&fit=crop&q=60",
        "https://images.unsplash.com/photo-1472099645785-5658abf4ffad8d80?w=100&auto=format&fit=crop&q=60",
        "https://images.unsplash.com/photo-1580489944761-15a19d654956?w=100&auto=format&fit=crop&q=60",
        "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=100&auto=format&fit=crop&q=60",
        "https://images.unsplash.com/photo-1544005313-94ddf0286df2?w=100&auto=format&fit=crop&q=60"
    );

    public List<TeamDto> getAllTeams() {
        return TEAM_MAPPING.values().stream()
            .distinct()
            .sorted(Comparator.comparingInt(TeamDto::getOrder))
            .toList();
    }

    public List<ShiftDto> getShiftsByDate(LocalDate date, String teamId, String timezone) {
        List<StaffShift> staffShifts;

        if (teamId != null && !teamId.isEmpty()) {
            var roleGroup = findRoleGroupByTeamId(teamId);
            if (roleGroup != null) {
                staffShifts = rosterRepository.findStaffShiftsByRoleGroup(roleGroup);
            } else {
                staffShifts = rosterRepository.findAllStaffShifts();
            }
        } else {
            staffShifts = rosterRepository.findAllStaffShifts();
        }

        var shifts = new ArrayList<ShiftDto>();
        int dayOfMonth = date.getDayOfMonth();
        var targetZone = ZoneId.of(Objects.requireNonNullElse(timezone, "UTC"));

        for (var staffShift : staffShifts) {
            String shiftCode = staffShift.getShiftCodeByDay(dayOfMonth);
            if (shiftCode == null || shiftCode.isEmpty()) {
                continue;
            }

            if (!isPrimaryShift(shiftCode)) {
                continue;
            }

            ShiftDefinition shiftDef = rosterRepository.findShiftDefinition(staffShift.getRoleGroup(), shiftCode);
            if (shiftDef != null && !Boolean.TRUE.equals(shiftDef.getShowOnRosterPage())) {
                continue;
            }

            var dto = convertToShiftDto(staffShift, shiftCode, shiftDef, date, timezone);
            
            if (!isShiftOnDate(dto.getStart(), dto.getEnd(), date, targetZone)) {
                continue;
            }
            
            shifts.add(dto);
        }

        return shifts;
    }

    private boolean isShiftOnDate(OffsetDateTime start, OffsetDateTime end, LocalDate targetDate, ZoneId targetZone) {
        ZonedDateTime startInTargetZone = start.atZoneSameInstant(targetZone);
        ZonedDateTime endInTargetZone = end.atZoneSameInstant(targetZone);
        
        LocalDate startDate = startInTargetZone.toLocalDate();
        LocalDate endDate = endInTargetZone.toLocalDate();
        
        return startDate.equals(targetDate) || endDate.equals(targetDate) ||
               (startDate.isBefore(targetDate) && endDate.isAfter(targetDate));
    }

    public ShiftDto getShiftById(String id) {
        return null;
    }

    private ShiftDto convertToShiftDto(StaffShift staffShift, String shiftCode, ShiftDefinition shiftDef, 
                                       LocalDate date, String timezone) {
        var dto = new ShiftDto();
        dto.setId(buildShiftId(staffShift, shiftCode, date));

        var team = TEAM_MAPPING.get(staffShift.getRoleGroup());
        if (team != null) {
            dto.setTeamId(team.getId());
        } else {
            dto.setTeamId(staffShift.getRoleGroup().toLowerCase().replace("_", "-"));
        }

        dto.setStaffId(staffShift.getStaffId());
        dto.setUserName(staffShift.getName());
        dto.setUserAvatar(generateAvatarUrl(staffShift.getStaffId()));
        dto.setCode(shiftCode);
        dto.setTimezone(shiftDef != null ? shiftDef.getTimezone() : "HKT");
        dto.setIsPrimary(isPrimaryShift(shiftCode));
        dto.setShowOnRoster(true);
        dto.setRemark(shiftDef != null ? shiftDef.getRemark() : staffShift.getNotes());

        if (shiftDef != null) {
            dto.setMeaning(shiftDef.getMeaning());
        } else {
            dto.setMeaning(getDefaultMeaning(shiftCode));
        }

        LocalTime startTime = parseTime(shiftDef != null ? shiftDef.getStartTime() : null);
        LocalTime endTime = parseTime(shiftDef != null ? shiftDef.getEndTime() : null);
        String shiftTimezone = shiftDef != null ? shiftDef.getTimezone() : "HKT";

        var targetZone = ZoneId.of(Objects.requireNonNullElse(timezone, "UTC"));
        var shiftZone = getZoneId(shiftTimezone);

        var startDateTime = calculateShiftDateTime(date, startTime, shiftZone, targetZone);
        var endDateTime = calculateShiftDateTime(date, endTime, shiftZone, targetZone);

        if (endTime != null && startTime != null && endTime.isBefore(startTime)) {
            endDateTime = endDateTime.plusDays(1);
        }

        dto.setStart(startDateTime);
        dto.setEnd(endDateTime);

        var contact = new ContactDto(
            "@" + staffShift.getName().toLowerCase().replace(" ", ""),
            staffShift.getName().toLowerCase().replace(" ", "") + "@company.com",
            staffShift.getContact() != null ? staffShift.getContact() : ""
        );
        dto.setContact(contact);

        return dto;
    }

    private LocalTime parseTime(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) return null;
        try {
            String[] parts = timeStr.split(":");
            return LocalTime.of(
                Integer.parseInt(parts[0]),
                Integer.parseInt(parts[1]),
                parts.length > 2 ? Integer.parseInt(parts[2]) : 0
            );
        } catch (Exception e) {
            return null;
        }
    }

    private String getDefaultMeaning(String code) {
        return switch (code) {
            case "A" -> "00:00-07:00";
            case "B" -> "06:30-15:30";
            case "C" -> "08:00-17:00";
            case "D" -> "15:30-00:30";
            case "DS" -> "Day Shift";
            case "NS" -> "Night Shift";
            case "OC" -> "Full Day Oncall Support";
            case "BH" -> "Business Hours";
            case "HoL" -> "Holiday or Leave";
            default -> code;
        };
    }

    private ZoneId getZoneId(String timezone) {
        return switch (timezone) {
            case "HKT" -> ZoneId.of("Asia/Hong_Kong");
            case "IST" -> ZoneId.of("Asia/Kolkata");
            case "INT" -> ZoneId.of("UTC");
            default -> ZoneId.of("UTC");
        };
    }

    private OffsetDateTime calculateShiftDateTime(LocalDate date, LocalTime time, ZoneId sourceZone, ZoneId targetZone) {
        if (time == null) {
            return date.atStartOfDay(targetZone).toOffsetDateTime();
        }
        ZonedDateTime zonedDateTime = date.atTime(time).atZone(sourceZone);
        return zonedDateTime.withZoneSameInstant(targetZone).toOffsetDateTime();
    }

    private boolean isPrimaryShift(String code) {
        return PRIMARY_CODES.contains(code);
    }

    private String findRoleGroupByTeamId(String teamId) {
        return TEAM_MAPPING.entrySet().stream()
            .filter(mapping -> mapping.getValue().getId().equals(teamId))
            .map(Map.Entry::getKey)
            .findFirst()
            .orElse(null);
    }

    private String generateAvatarUrl(Long staffId) {
        var avatarIndex = Math.toIntExact(staffId % 8) + 1;
        return AVATARS.get((avatarIndex - 1) % AVATARS.size());
    }

    private String buildShiftId(StaffShift staffShift, String shiftCode, LocalDate date) {
        var key = String.join("|",
            Objects.toString(staffShift.getStaffId(), ""),
            Objects.toString(shiftCode, ""),
            Objects.toString(date, "")
        );
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
    }
}
