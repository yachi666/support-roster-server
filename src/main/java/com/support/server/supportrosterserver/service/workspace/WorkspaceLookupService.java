package com.support.server.supportrosterserver.service.workspace;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.support.server.supportrosterserver.entity.workspace.TeamEntity;
import com.support.server.supportrosterserver.exception.ResourceNotFoundException;
import com.support.server.supportrosterserver.mapper.TeamMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WorkspaceLookupService {

    private final TeamMapper teamMapper;

    public List<TeamEntity> listTeams() {
        return teamMapper.selectList(Wrappers.<TeamEntity>lambdaQuery()
            .orderByAsc(TeamEntity::getDisplayOrder)
            .orderByAsc(TeamEntity::getName));
    }

    public Map<Long, TeamEntity> teamMap() {
        Map<Long, TeamEntity> map = new LinkedHashMap<>();
        for (TeamEntity entity : listTeams()) {
            map.put(entity.getId(), entity);
        }
        return map;
    }

    public TeamEntity requireTeam(Long id) {
        TeamEntity entity = teamMapper.selectById(id);
        if (entity == null) {
            throw new ResourceNotFoundException("Team", "id", id);
        }
        return entity;
    }

    public String normalizeWorkspaceTimezone(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return "UTC";
        }

        return switch (timezone) {
            case "UTC", "GMT" -> "UTC";
            case "Asia/Shanghai", "Asia/Hong_Kong", "Asia/Singapore", "Asia/Tokyo", "Asia/Seoul", "HKT" -> "HKT";
            case "Asia/Kolkata", "Asia/Calcutta", "Asia/Colombo", "IST", "Europe/London", "America/New_York" -> "IST";
            default -> "UTC";
        };
    }

    public String inferTimezone(String region, String teamName) {
        if (region != null) {
            String normalized = region.toLowerCase(Locale.ROOT);
            if (normalized.contains("china")) {
                return "HKT";
            }
            if (normalized.contains("india")) {
                return "IST";
            }
            if (normalized.contains("emea") || normalized.contains("europe")) {
                return "UTC";
            }
            if (normalized.contains("apac") || normalized.contains("singapore") || normalized.contains("hong kong")) {
                return "HKT";
            }
            if (normalized.contains("america")) {
                return "UTC";
            }
        }
        if (teamName != null) {
            String normalizedTeam = teamName.toLowerCase(Locale.ROOT);
            if (normalizedTeam.contains("india")) {
                return "IST";
            }
            if (normalizedTeam.contains("china") || normalizedTeam.contains("ap")) {
                return "HKT";
            }
            if (normalizedTeam.contains("emea")) {
                return "UTC";
            }
        }
        return null;
    }
}