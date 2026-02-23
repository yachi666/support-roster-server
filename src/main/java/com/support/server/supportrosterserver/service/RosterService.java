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
import com.support.server.supportrosterserver.entity.RosterEntry;
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
        var entries = rosterRepository.findAllRosterEntries();

        if (teamId != null && !teamId.isEmpty()) {
            var roleGroup = findRoleGroupByTeamId(teamId);
            if (roleGroup != null) {
                entries = rosterRepository.findRosterEntriesByRoleGroup(roleGroup);
            }
        }

        var shifts = new ArrayList<ShiftDto>();
        for (var entry : entries) {
            if (!Boolean.TRUE.equals(entry.getShowOnRosterPage())) {
                continue;
            }

            shifts.add(convertToShiftDto(entry, date, timezone));
        }

        return shifts;
    }

    public ShiftDto getShiftById(String id) {
        var today = LocalDate.now(ZoneId.of("UTC"));
        return rosterRepository.findAllRosterEntries().stream()
            .filter(entry -> buildShiftId(entry).equals(id))
            .findFirst()
            .map(entry -> convertToShiftDto(entry, today, "UTC"))
            .orElse(null);
    }

    private ShiftDto convertToShiftDto(RosterEntry entry, LocalDate date, String timezone) {
        var dto = new ShiftDto();
        dto.setId(buildShiftId(entry));

        var team = TEAM_MAPPING.get(entry.getRoleGroup());
        if (team != null) {
            dto.setTeamId(team.getId());
        } else {
            dto.setTeamId(entry.getRoleGroup().toLowerCase().replace("_", "-"));
        }

        dto.setStaffId(entry.getStaffId());
        dto.setUserName("Staff " + entry.getStaffId());
        dto.setUserAvatar(generateAvatarUrl(entry.getStaffId()));
        dto.setCode(entry.getCode());
        dto.setMeaning(entry.getMeaning());
        dto.setTimezone(entry.getTimezone());
        dto.setIsPrimary(isPrimaryShift(entry));
        dto.setShowOnRoster(entry.getShowOnRosterPage());
        dto.setRemark(entry.getRemark());

        var targetZone = ZoneId.of(Objects.requireNonNullElse(timezone, "UTC"));
        var shiftZone = "HKT".equals(entry.getTimezone())
            ? ZoneId.of("Asia/Hong_Kong")
            : ZoneId.of("UTC");

        var startDateTime = calculateShiftDateTime(date, entry.getStartTime(), shiftZone, targetZone);
        var endDateTime = calculateShiftDateTime(date, entry.getEndTime(), shiftZone, targetZone);

        if (entry.getEndTime() != null && entry.getStartTime() != null
            && entry.getEndTime().isBefore(entry.getStartTime())) {
            endDateTime = endDateTime.plusDays(1);
        }

        dto.setStart(startDateTime);
        dto.setEnd(endDateTime);

        var contact = new ContactDto(
            "@staff" + entry.getStaffId(),
            "staff" + entry.getStaffId() + "@company.com",
            "+1-555-" + String.format("%04d", entry.getStaffId())
        );
        dto.setContact(contact);

        return dto;
    }

    private OffsetDateTime calculateShiftDateTime(LocalDate date, LocalTime time, ZoneId sourceZone, ZoneId targetZone) {
        if (time == null) {
            return date.atStartOfDay(targetZone).toOffsetDateTime();
        }
        ZonedDateTime zonedDateTime = date.atTime(time).atZone(sourceZone);
        return zonedDateTime.withZoneSameInstant(targetZone).toOffsetDateTime();
    }

    private boolean isPrimaryShift(RosterEntry entry) {
        return PRIMARY_CODES.contains(entry.getCode());
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

    private String buildShiftId(RosterEntry entry) {
        var key = String.join("|",
            Objects.toString(entry.getRoleGroup(), ""),
            Objects.toString(entry.getStaffId(), ""),
            Objects.toString(entry.getCode(), ""),
            Objects.toString(entry.getStartTime(), ""),
            Objects.toString(entry.getEndTime(), ""),
            Objects.toString(entry.getTimezone(), "")
        );
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
    }
}
