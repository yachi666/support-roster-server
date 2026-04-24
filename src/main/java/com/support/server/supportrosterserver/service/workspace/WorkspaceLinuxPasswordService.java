package com.support.server.supportrosterserver.service.workspace;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceLinuxPasswordDto;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceLinuxPasswordListResponse;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceLinuxPasswordUpsertRequest;
import com.support.server.supportrosterserver.entity.workspace.LinuxPasswordDirectoryEntity;
import com.support.server.supportrosterserver.entity.workspace.LinuxPasswordServerBusinessUnitEntity;
import com.support.server.supportrosterserver.entity.workspace.LinuxPasswordServerEntity;
import com.support.server.supportrosterserver.exception.BadRequestException;
import com.support.server.supportrosterserver.exception.ResourceNotFoundException;
import com.support.server.supportrosterserver.mapper.LinuxPasswordDirectoryMapper;
import com.support.server.supportrosterserver.mapper.LinuxPasswordServerBusinessUnitMapper;
import com.support.server.supportrosterserver.mapper.LinuxPasswordServerMapper;
import com.support.server.supportrosterserver.service.auth.AuthContextService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WorkspaceLinuxPasswordService {

    private static final String DEFAULT_STATUS = "online";
    private static final String DEFAULT_BUSINESS_UNIT = "Uncategorized";

    private final LinuxPasswordServerMapper linuxPasswordServerMapper;
    private final LinuxPasswordServerBusinessUnitMapper linuxPasswordServerBusinessUnitMapper;
    private final LinuxPasswordDirectoryMapper linuxPasswordDirectoryMapper;
    private final AuthContextService authContextService;
    private final WorkspaceOperationLogService workspaceOperationLogService;

    public WorkspaceLinuxPasswordListResponse listServers(String search, String businessUnit) {
        authContextService.requireLogin();
        List<LinuxPasswordServerEntity> servers = linuxPasswordServerMapper.selectList(
            Wrappers.<LinuxPasswordServerEntity>lambdaQuery()
                .orderByAsc(LinuxPasswordServerEntity::getHostname)
                .orderByAsc(LinuxPasswordServerEntity::getIp)
        );
        Map<Long, List<String>> businessUnitsByServerId = loadBusinessUnitsByServerId(servers.stream()
            .map(LinuxPasswordServerEntity::getId)
            .filter(Objects::nonNull)
            .toList());

        String normalizedSearch = normalizeOptional(search);
        String normalizedBusinessUnit = normalizeOptional(businessUnit);
        List<WorkspaceLinuxPasswordDto> items = servers.stream()
            .filter(server -> matchesSearch(server, normalizedSearch))
            .filter(server -> matchesBusinessUnit(server.getId(), businessUnitsByServerId, normalizedBusinessUnit))
            .map(server -> toDto(server, businessUnitsByServerId.getOrDefault(server.getId(), List.of(DEFAULT_BUSINESS_UNIT))))
            .toList();

        return new WorkspaceLinuxPasswordListResponse(items, collectBusinessUnits(businessUnitsByServerId));
    }

    public List<String> listDirectories() {
        authContextService.requireLogin();
        backfillDirectoriesFromRelationsIfNeeded();
        return linuxPasswordDirectoryMapper.selectList(Wrappers.<LinuxPasswordDirectoryEntity>lambdaQuery()
                .orderByAsc(LinuxPasswordDirectoryEntity::getName))
            .stream()
            .map(LinuxPasswordDirectoryEntity::getName)
            .toList();
    }

    public WorkspaceLinuxPasswordDto getServer(Long id) {
        authContextService.requireLogin();
        LinuxPasswordServerEntity entity = requireServer(id);
        return toDto(entity, loadBusinessUnitsByServerId(List.of(id)).getOrDefault(id, List.of(DEFAULT_BUSINESS_UNIT)));
    }

    @Transactional
    public WorkspaceLinuxPasswordDto createServer(WorkspaceLinuxPasswordUpsertRequest request) {
        authContextService.requireLogin();
        LinuxPasswordServerEntity entity = new LinuxPasswordServerEntity();
        applyForCreate(entity, request);
        ensureUnique(entity, null);
        linuxPasswordServerMapper.insert(entity);

        List<String> businessUnits = normalizeBusinessUnits(request.getBusinessUnits());
        ensureDirectoriesExist(businessUnits);
        replaceBusinessUnits(entity.getId(), businessUnits);
        workspaceOperationLogService.log(
            authContextService.currentActor("system"),
            "Create linux password server",
            "workspace_linux_password_server",
            entity.getId(),
            "Hostname=" + entity.getHostname()
        );
        return toDto(entity, businessUnits);
    }

    @Transactional
    public WorkspaceLinuxPasswordDto updateServer(Long id, WorkspaceLinuxPasswordUpsertRequest request) {
        authContextService.requireAdmin();
        LinuxPasswordServerEntity entity = requireServer(id);
        List<String> previousBusinessUnits = loadBusinessUnitsByServerId(List.of(id)).getOrDefault(id, List.of(DEFAULT_BUSINESS_UNIT));
        applyForUpdate(entity, request);
        ensureUnique(entity, id);
        linuxPasswordServerMapper.updateById(entity);

        List<String> businessUnits = normalizeBusinessUnits(request.getBusinessUnits());
        ensureDirectoriesExist(businessUnits);
        replaceBusinessUnits(id, businessUnits);
        cleanupOrphanDirectories(previousBusinessUnits);
        workspaceOperationLogService.log(
            authContextService.currentActor("system"),
            "Update linux password server",
            "workspace_linux_password_server",
            id,
            "Hostname=" + entity.getHostname()
        );
        return toDto(entity, businessUnits);
    }

    @Transactional
    public void deleteServer(Long id) {
        authContextService.requireAdmin();
        LinuxPasswordServerEntity entity = requireServer(id);
        List<String> previousBusinessUnits = loadBusinessUnitsByServerId(List.of(id)).getOrDefault(id, List.of(DEFAULT_BUSINESS_UNIT));
        linuxPasswordServerBusinessUnitMapper.delete(Wrappers.<LinuxPasswordServerBusinessUnitEntity>lambdaQuery()
            .eq(LinuxPasswordServerBusinessUnitEntity::getServerId, id));
        linuxPasswordServerMapper.deleteById(id);
        cleanupOrphanDirectories(previousBusinessUnits);
        workspaceOperationLogService.log(
            authContextService.currentActor("system"),
            "Delete linux password server",
            "workspace_linux_password_server",
            id,
            "Hostname=" + entity.getHostname()
        );
    }

    private LinuxPasswordServerEntity requireServer(Long id) {
        LinuxPasswordServerEntity entity = linuxPasswordServerMapper.selectById(id);
        if (entity == null) {
            throw new ResourceNotFoundException("LinuxPasswordServer", "id", id);
        }
        return entity;
    }

    private void applyForCreate(LinuxPasswordServerEntity entity, WorkspaceLinuxPasswordUpsertRequest request) {
        entity.setHostname(normalizeRequired(request.getHostname(), "Hostname is required."));
        entity.setIp(normalizeRequired(request.getIp(), "IP address is required."));
        entity.setUsername(normalizeRequired(request.getUsername(), "Username is required."));
        entity.setPassword(normalizeRequired(request.getPassword(), "Password is required."));
        entity.setStatus(DEFAULT_STATUS);
    }

    private void applyForUpdate(LinuxPasswordServerEntity entity, WorkspaceLinuxPasswordUpsertRequest request) {
        entity.setHostname(normalizeRequired(request.getHostname(), "Hostname is required."));
        entity.setIp(normalizeRequired(request.getIp(), "IP address is required."));
        entity.setUsername(normalizeRequired(request.getUsername(), "Username is required."));
        entity.setPassword(normalizeRequired(request.getPassword(), "Password is required."));
        entity.setStatus(normalizeStatus(request.getStatus()));
    }

    private String normalizeRequired(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException(message);
        }
        return value.trim();
    }

    private String normalizeStatus(String value) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw new BadRequestException("Status is required.");
        }
        if (!List.of("online", "maintenance", "offline").contains(normalized)) {
            throw new BadRequestException("Status must be one of online, maintenance, or offline.");
        }
        return normalized;
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private List<String> normalizeBusinessUnits(List<String> businessUnits) {
        List<String> normalized = (businessUnits == null ? List.<String>of() : businessUnits).stream()
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .distinct()
            .toList();
        return normalized.isEmpty() ? List.of(DEFAULT_BUSINESS_UNIT) : normalized;
    }

    private void replaceBusinessUnits(Long serverId, List<String> businessUnits) {
        linuxPasswordServerBusinessUnitMapper.delete(Wrappers.<LinuxPasswordServerBusinessUnitEntity>lambdaQuery()
            .eq(LinuxPasswordServerBusinessUnitEntity::getServerId, serverId));
        for (String businessUnit : businessUnits) {
            LinuxPasswordServerBusinessUnitEntity relation = new LinuxPasswordServerBusinessUnitEntity();
            relation.setServerId(serverId);
            relation.setBusinessUnit(businessUnit);
            linuxPasswordServerBusinessUnitMapper.insert(relation);
        }
    }

    private void ensureDirectoriesExist(List<String> directoryNames) {
        Map<String, LinuxPasswordDirectoryEntity> directoriesByNormalizedName = linuxPasswordDirectoryMapper.selectList(
                Wrappers.<LinuxPasswordDirectoryEntity>lambdaQuery())
            .stream()
            .collect(Collectors.toMap(
                directory -> normalizeDirectoryKey(directory.getName()),
                Function.identity(),
                (left, right) -> left
            ));
        for (String directoryName : directoryNames) {
            if (directoriesByNormalizedName.containsKey(normalizeDirectoryKey(directoryName))) {
                continue;
            }
            LinuxPasswordDirectoryEntity entity = new LinuxPasswordDirectoryEntity();
            entity.setName(directoryName);
            linuxPasswordDirectoryMapper.insert(entity);
        }
    }

    private void backfillDirectoriesFromRelationsIfNeeded() {
        if (!linuxPasswordDirectoryMapper.selectList(Wrappers.<LinuxPasswordDirectoryEntity>lambdaQuery()).isEmpty()) {
            return;
        }
        List<String> relationDirectories = linuxPasswordServerBusinessUnitMapper.selectList(
                Wrappers.<LinuxPasswordServerBusinessUnitEntity>lambdaQuery())
            .stream()
            .map(LinuxPasswordServerBusinessUnitEntity::getBusinessUnit)
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .distinct()
            .toList();
        ensureDirectoriesExist(relationDirectories);
    }

    private void cleanupOrphanDirectories(List<String> directoryNames) {
        List<LinuxPasswordServerBusinessUnitEntity> allRelations = linuxPasswordServerBusinessUnitMapper.selectList(
            Wrappers.<LinuxPasswordServerBusinessUnitEntity>lambdaQuery());
        Map<String, Long> relationCountByDirectory = allRelations.stream()
            .collect(Collectors.groupingBy(
                relation -> normalizeDirectoryKey(relation.getBusinessUnit()),
                Collectors.counting()
            ));
        for (String directoryName : directoryNames) {
            String normalizedName = normalizeDirectoryKey(directoryName);
            if (relationCountByDirectory.getOrDefault(normalizedName, 0L) > 0) {
                continue;
            }
            linuxPasswordDirectoryMapper.delete(Wrappers.<LinuxPasswordDirectoryEntity>lambdaQuery()
                .apply("LOWER(BTRIM(name)) = {0}", normalizedName));
        }
    }

    private void ensureUnique(LinuxPasswordServerEntity candidate, Long currentId) {
        List<LinuxPasswordServerEntity> existing = linuxPasswordServerMapper.selectList(Wrappers.<LinuxPasswordServerEntity>lambdaQuery());
        String normalizedHostname = candidate.getHostname().toLowerCase(Locale.ROOT);
        String normalizedIp = candidate.getIp().toLowerCase(Locale.ROOT);
        for (LinuxPasswordServerEntity entity : existing) {
            if (currentId != null && Objects.equals(currentId, entity.getId())) {
                continue;
            }
            if (normalizedHostname.equals(normalizeOptional(entity.getHostname()).toLowerCase(Locale.ROOT))) {
                throw new BadRequestException("Hostname already exists.");
            }
            if (normalizedIp.equals(normalizeOptional(entity.getIp()).toLowerCase(Locale.ROOT))) {
                throw new BadRequestException("IP address already exists.");
            }
        }
    }

    private Map<Long, List<String>> loadBusinessUnitsByServerId(List<Long> serverIds) {
        if (serverIds.isEmpty()) {
            return Map.of();
        }
        return linuxPasswordServerBusinessUnitMapper.selectList(Wrappers.<LinuxPasswordServerBusinessUnitEntity>lambdaQuery()
                .in(LinuxPasswordServerBusinessUnitEntity::getServerId, serverIds)
                .orderByAsc(LinuxPasswordServerBusinessUnitEntity::getBusinessUnit))
            .stream()
            .collect(Collectors.groupingBy(
                LinuxPasswordServerBusinessUnitEntity::getServerId,
                Collectors.mapping(LinuxPasswordServerBusinessUnitEntity::getBusinessUnit, Collectors.toList())
            ));
    }

    private List<String> collectBusinessUnits(Map<Long, List<String>> businessUnitsByServerId) {
        LinkedHashSet<String> units = new LinkedHashSet<>();
        businessUnitsByServerId.values().stream()
            .flatMap(List::stream)
            .forEach(units::add);
        return units.stream().sorted().toList();
    }

    private String normalizeDirectoryKey(String value) {
        return normalizeOptional(value).toLowerCase(Locale.ROOT);
    }

    private boolean matchesSearch(LinuxPasswordServerEntity entity, String normalizedSearch) {
        if (normalizedSearch == null) {
            return true;
        }
        String search = normalizedSearch.toLowerCase(Locale.ROOT);
        return entity.getHostname().toLowerCase(Locale.ROOT).contains(search)
            || entity.getIp().toLowerCase(Locale.ROOT).contains(search);
    }

    private boolean matchesBusinessUnit(Long serverId, Map<Long, List<String>> businessUnitsByServerId, String normalizedBusinessUnit) {
        if (normalizedBusinessUnit == null) {
            return true;
        }
        return businessUnitsByServerId.getOrDefault(serverId, List.of(DEFAULT_BUSINESS_UNIT)).stream()
            .map(value -> value.toLowerCase(Locale.ROOT))
            .anyMatch(normalizedBusinessUnit.toLowerCase(Locale.ROOT)::equals);
    }

    private WorkspaceLinuxPasswordDto toDto(LinuxPasswordServerEntity entity, List<String> businessUnits) {
        return new WorkspaceLinuxPasswordDto(
            entity.getId(),
            entity.getHostname(),
            entity.getIp(),
            entity.getUsername(),
            entity.getPassword(),
            businessUnits,
            entity.getStatus()
        );
    }
}
