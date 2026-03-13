package com.support.server.supportrosterserver.service.workspace;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceRoleGroupDto;
import com.support.server.supportrosterserver.entity.workspace.RoleGroupEntity;
import com.support.server.supportrosterserver.entity.workspace.TeamEntity;
import com.support.server.supportrosterserver.entity.workspace.TeamRoleGroupRelEntity;
import com.support.server.supportrosterserver.exception.ResourceNotFoundException;
import com.support.server.supportrosterserver.mapper.RoleGroupMapper;
import com.support.server.supportrosterserver.mapper.TeamMapper;
import com.support.server.supportrosterserver.mapper.TeamRoleGroupRelMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WorkspaceLookupService {

    private final RoleGroupMapper roleGroupMapper;
    private final TeamMapper teamMapper;
    private final TeamRoleGroupRelMapper teamRoleGroupRelMapper;

    public List<RoleGroupEntity> listRoleGroups() {
        return roleGroupMapper.selectList(Wrappers.<RoleGroupEntity>lambdaQuery()
            .orderByAsc(RoleGroupEntity::getCode));
    }

    public Map<Long, RoleGroupEntity> roleGroupMap() {
        Map<Long, RoleGroupEntity> map = new LinkedHashMap<>();
        for (RoleGroupEntity entity : listRoleGroups()) {
            map.put(entity.getId(), entity);
        }
        return map;
    }

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

    public Map<Long, List<Long>> teamRoleGroupIdsMap() {
        Map<Long, List<Long>> result = new LinkedHashMap<>();
        List<TeamRoleGroupRelEntity> rels = teamRoleGroupRelMapper.selectList(Wrappers.<TeamRoleGroupRelEntity>lambdaQuery()
            .orderByAsc(TeamRoleGroupRelEntity::getTeamId)
            .orderByAsc(TeamRoleGroupRelEntity::getRoleGroupId));
        for (TeamRoleGroupRelEntity rel : rels) {
            result.computeIfAbsent(rel.getTeamId(), ignored -> new ArrayList<>()).add(rel.getRoleGroupId());
        }
        return result;
    }

    public Map<Long, TeamEntity> teamByRoleGroupId() {
        Map<Long, TeamEntity> result = new LinkedHashMap<>();
        Map<Long, TeamEntity> teams = teamMap();
        List<TeamRoleGroupRelEntity> rels = teamRoleGroupRelMapper.selectList(Wrappers.<TeamRoleGroupRelEntity>lambdaQuery());
        rels.stream()
            .sorted(Comparator.comparing(TeamRoleGroupRelEntity::getTeamId))
            .forEach(rel -> result.putIfAbsent(rel.getRoleGroupId(), teams.get(rel.getTeamId())));
        return result;
    }

    public RoleGroupEntity requireRoleGroup(Long id) {
        RoleGroupEntity entity = roleGroupMapper.selectById(id);
        if (entity == null) {
            throw new ResourceNotFoundException("RoleGroup", "id", id);
        }
        return entity;
    }

    public TeamEntity requireTeam(Long id) {
        TeamEntity entity = teamMapper.selectById(id);
        if (entity == null) {
            throw new ResourceNotFoundException("Team", "id", id);
        }
        return entity;
    }

    @Transactional
    public void replaceTeamRoleGroups(Long teamId, List<Long> roleGroupIds) {
        teamRoleGroupRelMapper.delete(Wrappers.<TeamRoleGroupRelEntity>lambdaQuery()
            .eq(TeamRoleGroupRelEntity::getTeamId, teamId));
        for (Long roleGroupId : roleGroupIds) {
            TeamRoleGroupRelEntity rel = new TeamRoleGroupRelEntity();
            rel.setTeamId(teamId);
            rel.setRoleGroupId(roleGroupId);
            teamRoleGroupRelMapper.insert(rel);
        }
    }

    public WorkspaceRoleGroupDto toRoleGroupDto(RoleGroupEntity entity) {
        return new WorkspaceRoleGroupDto(
            entity.getId(),
            entity.getCode(),
            entity.getName(),
            entity.getCategory(),
            entity.getRegion(),
            entity.getDescription(),
            entity.getActive()
        );
    }

    public RoleGroupEntity findOrCreateRoleGroupByCode(String code) {
        RoleGroupEntity existing = roleGroupMapper.selectOne(Wrappers.<RoleGroupEntity>lambdaQuery()
            .eq(RoleGroupEntity::getCode, code)
            .last("limit 1"));
        if (existing != null) {
            return existing;
        }

        RoleGroupEntity entity = deriveRoleGroup(code);
        roleGroupMapper.insert(entity);
        return entity;
    }

    @Transactional
    public TeamEntity findOrCreateTeamForRoleGroup(RoleGroupEntity roleGroup) {
        TeamProfile profile = deriveTeamProfile(roleGroup.getCode());
        if (profile == null) {
            return null;
        }

        TeamEntity team = teamMapper.selectOne(Wrappers.<TeamEntity>lambdaQuery()
            .eq(TeamEntity::getTeamCode, profile.teamCode())
            .last("limit 1"));
        if (team == null) {
            team = new TeamEntity();
            team.setTeamCode(profile.teamCode());
            team.setName(profile.name());
            team.setColor(profile.color());
            team.setDisplayOrder(profile.displayOrder());
            team.setVisible(true);
            team.setDescription("Auto created from import");
            teamMapper.insert(team);
        }

        TeamRoleGroupRelEntity rel = teamRoleGroupRelMapper.selectOne(Wrappers.<TeamRoleGroupRelEntity>lambdaQuery()
            .eq(TeamRoleGroupRelEntity::getTeamId, team.getId())
            .eq(TeamRoleGroupRelEntity::getRoleGroupId, roleGroup.getId())
            .last("limit 1"));
        if (rel == null) {
            rel = new TeamRoleGroupRelEntity();
            rel.setTeamId(team.getId());
            rel.setRoleGroupId(roleGroup.getId());
            teamRoleGroupRelMapper.insert(rel);
        }
        return team;
    }

    public RoleGroupEntity deriveRoleGroup(String code) {
        RoleGroupEntity entity = new RoleGroupEntity();
        entity.setCode(code);
        entity.setName(code.replace("_", " "));
        entity.setCategory(deriveCategory(code));
        entity.setRegion(deriveRegion(code));
        entity.setDescription("Imported from roster source");
        entity.setActive(true);
        return entity;
    }

    public TeamProfile deriveTeamProfile(String roleGroupCode) {
        if (roleGroupCode == null || roleGroupCode.isBlank()) {
            return null;
        }
        if (roleGroupCode.startsWith("Incident_Manager_")) {
            return new TeamProfile("incident-manager", "Incident Manager", "orange", 0);
        }
        if (roleGroupCode.startsWith("L1_")) {
            return new TeamProfile("l1", "L1", "blue", 1);
        }
        if (roleGroupCode.equals("AP_L2")) {
            return new TeamProfile("ap-l2", "AP L2", "green", 2);
        }
        if (roleGroupCode.equals("EMEA_L2")) {
            return new TeamProfile("emea-l2", "EMEA L2", "purple", 3);
        }
        if (roleGroupCode.equals("MDP_L2")) {
            return new TeamProfile("mdp-l2", "MDP L2", "red", 4);
        }
        if (roleGroupCode.equals("AP_L2+")) {
            return new TeamProfile("ap-l2-plus", "AP L2+", "green", 5);
        }
        if (roleGroupCode.equals("AP_L3")) {
            return new TeamProfile("ap-l3", "AP L3", "green", 6);
        }
        if (roleGroupCode.startsWith("DevOps_")) {
            return new TeamProfile("devops", "DevOps", "orange", 7);
        }
        return null;
    }

    public String inferTimezone(String region, String roleGroupCode) {
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
        if (roleGroupCode != null) {
            if (roleGroupCode.contains("India")) {
                return "Asia/Kolkata";
            }
            if (roleGroupCode.contains("China") || roleGroupCode.startsWith("AP_")) {
                return "Asia/Shanghai";
            }
            if (roleGroupCode.startsWith("EMEA")) {
                return "Europe/London";
            }
        }
        return null;
    }

    private String deriveCategory(String code) {
        if (code == null) {
            return "Unknown";
        }
        if (code.startsWith("L1_")) {
            return "L1";
        }
        if (code.equals("AP_L2+") || code.contains("L2+")) {
            return "L2+";
        }
        if (code.contains("L3")) {
            return "L3";
        }
        if (code.contains("L2")) {
            return "L2";
        }
        if (code.startsWith("Incident_Manager_")) {
            return "Incident Manager";
        }
        if (code.startsWith("DevOps_")) {
            return "DevOps";
        }
        return "Other";
    }

    private String deriveRegion(String code) {
        if (code == null) {
            return "Unknown";
        }
        if (code.endsWith("_China") || code.contains("China")) {
            return "China";
        }
        if (code.endsWith("_India") || code.contains("India")) {
            return "India";
        }
        if (code.startsWith("AP_")) {
            return "APAC";
        }
        if (code.startsWith("EMEA")) {
            return "EMEA";
        }
        if (code.startsWith("MDP")) {
            return "Global";
        }
        return Objects.toString(code, "Unknown");
    }

    public record TeamProfile(String teamCode, String name, String color, Integer displayOrder) {
    }
}