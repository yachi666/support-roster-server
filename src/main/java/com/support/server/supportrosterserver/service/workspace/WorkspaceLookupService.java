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

    public String inferTimezone(String region, String teamName) {
        if (region != null) {
            String normalized = region.toLowerCase(Locale.ROOT);
            if (normalized.contains("china")) {
                return "Asia/Shanghai";
            }
            if (normalized.contains("india")) {
                return "Asia/Kolkata";
            }
            if (normalized.contains("emea") || normalized.contains("europe")) {
                return "Europe/London";
            }
            if (normalized.contains("apac") || normalized.contains("singapore") || normalized.contains("hong kong")) {
                return "Asia/Singapore";
            }
            if (normalized.contains("america")) {
                return "America/New_York";
            }
        }
        if (teamName != null) {
            String normalizedTeam = teamName.toLowerCase(Locale.ROOT);
            if (normalizedTeam.contains("india")) {
                return "Asia/Kolkata";
            }
            if (normalizedTeam.contains("china") || normalizedTeam.contains("ap")) {
                return "Asia/Shanghai";
            }
            if (normalizedTeam.contains("emea")) {
                return "Europe/London";
            }
        }
        return null;
    }
}