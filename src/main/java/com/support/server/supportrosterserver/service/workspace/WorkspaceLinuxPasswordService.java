package com.support.server.supportrosterserver.service.workspace;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.support.server.supportrosterserver.auth.AuthenticatedAccount;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceLinuxPasswordAccessAuditDto;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceLinuxPasswordAccessAuditListResponse;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceLinuxPasswordCredentialDto;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceLinuxPasswordDto;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceLinuxPasswordListResponse;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceLinuxPasswordSecretResponse;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceLinuxPasswordUpsertRequest;
import com.support.server.supportrosterserver.entity.workspace.LinuxPasswordAccessAuditEntity;
import com.support.server.supportrosterserver.entity.workspace.LinuxPasswordCredentialEntity;
import com.support.server.supportrosterserver.entity.workspace.LinuxPasswordDirectoryEntity;
import com.support.server.supportrosterserver.entity.workspace.LinuxPasswordServerBusinessUnitEntity;
import com.support.server.supportrosterserver.entity.workspace.LinuxPasswordServerEntity;
import com.support.server.supportrosterserver.exception.BadRequestException;
import com.support.server.supportrosterserver.exception.ResourceNotFoundException;
import com.support.server.supportrosterserver.mapper.LinuxPasswordAccessAuditMapper;
import com.support.server.supportrosterserver.mapper.LinuxPasswordCredentialMapper;
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
    private final LinuxPasswordCredentialMapper linuxPasswordCredentialMapper;
    private final LinuxPasswordAccessAuditMapper linuxPasswordAccessAuditMapper;
    private final LinuxPasswordSecretService linuxPasswordSecretService;
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
        Map<Long, List<LinuxPasswordCredentialEntity>> credentialsByServerId = loadCredentialsByServerId(servers);
        List<WorkspaceLinuxPasswordDto> items = servers.stream()
            .filter(server -> matchesSearch(server, normalizedSearch))
            .filter(server -> matchesBusinessUnit(server.getId(), businessUnitsByServerId, normalizedBusinessUnit))
            .map(server -> toDto(
                server,
                credentialsByServerId.getOrDefault(server.getId(), List.of()),
                businessUnitsByServerId.getOrDefault(server.getId(), List.of(DEFAULT_BUSINESS_UNIT))
            ))
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
        return toDto(
            entity,
            loadCredentialsByServerId(List.of(entity)).getOrDefault(id, List.of()),
            loadBusinessUnitsByServerId(List.of(id)).getOrDefault(id, List.of(DEFAULT_BUSINESS_UNIT))
        );
    }

    public WorkspaceLinuxPasswordAccessAuditListResponse listAccessAudits(
            String keyword,
            String staffId,
            String staffName,
            String hostname,
            String ip,
            String username,
            String action,
            String result,
            String from,
            String to,
            long page,
            long pageSize) {
        authContextService.requireAdmin();
        long normalizedPage = Math.max(page, 1);
        long normalizedPageSize = Math.min(Math.max(pageSize, 1), 100);
        LocalDateTime fromTime = parseDateTimeBoundary(from, false);
        LocalDateTime toTime = parseDateTimeBoundary(to, true);
        String normalizedAction = normalizeAuditEnum(action);
        String normalizedResult = normalizeAuditEnum(result);
        String normalizedStaffId = normalizeSearchKey(staffId);
        String normalizedStaffName = normalizeSearchKey(staffName);
        String normalizedHostname = normalizeSearchKey(hostname);
        String normalizedIp = normalizeSearchKey(ip);
        String normalizedUsername = normalizeSearchKey(username);
        String normalizedKeyword = normalizeSearchKey(keyword);

        var wrapper = Wrappers.<LinuxPasswordAccessAuditEntity>lambdaQuery()
            .ge(fromTime != null, LinuxPasswordAccessAuditEntity::getCreateTime, fromTime)
            .le(toTime != null, LinuxPasswordAccessAuditEntity::getCreateTime, toTime)
            .eq(normalizedAction != null, LinuxPasswordAccessAuditEntity::getAction, normalizedAction)
            .eq(normalizedResult != null, LinuxPasswordAccessAuditEntity::getResult, normalizedResult)
            .like(normalizedStaffId != null, LinuxPasswordAccessAuditEntity::getStaffId, normalizedStaffId)
            .like(normalizedStaffName != null, LinuxPasswordAccessAuditEntity::getStaffName, normalizedStaffName)
            .like(normalizedHostname != null, LinuxPasswordAccessAuditEntity::getHostnameSnapshot, normalizedHostname)
            .like(normalizedIp != null, LinuxPasswordAccessAuditEntity::getIpSnapshot, normalizedIp)
            .like(normalizedUsername != null, LinuxPasswordAccessAuditEntity::getUsernameSnapshot, normalizedUsername);

        if (normalizedKeyword != null) {
            String likeParam = "%" + normalizedKeyword + "%";
            wrapper.apply(
                "(LOWER(staff_id) LIKE {0}"
                + " OR LOWER(staff_name) LIKE {0}"
                + " OR LOWER(hostname_snapshot) LIKE {0}"
                + " OR LOWER(ip_snapshot) LIKE {0}"
                + " OR LOWER(username_snapshot) LIKE {0}"
                + " OR LOWER(action) LIKE {0}"
                + " OR LOWER(result) LIKE {0}"
                + " OR LOWER(client_ip) LIKE {0})",
                likeParam
            );
        }

        wrapper.orderByDesc(LinuxPasswordAccessAuditEntity::getCreateTime);

        Page<LinuxPasswordAccessAuditEntity> pageParam = new Page<>(normalizedPage, normalizedPageSize);
        IPage<LinuxPasswordAccessAuditEntity> pageResult = linuxPasswordAccessAuditMapper.selectPage(pageParam, wrapper);

        List<LinuxPasswordAccessAuditEntity> audits = pageResult.getRecords();
        long total = pageResult.getTotal();

        // For legacy records without snapshots, fall back to live join
        List<Long> legacyServerIds = audits.stream()
            .filter(a -> a.getHostnameSnapshot() == null && a.getServerId() != null)
            .map(LinuxPasswordAccessAuditEntity::getServerId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        List<Long> legacyCredentialIds = audits.stream()
            .filter(a -> a.getUsernameSnapshot() == null && a.getCredentialId() != null)
            .map(LinuxPasswordAccessAuditEntity::getCredentialId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();

        Map<Long, LinuxPasswordServerEntity> serversById = legacyServerIds.isEmpty()
            ? Map.of()
            : loadServerMap(legacyServerIds);
        Map<Long, LinuxPasswordCredentialEntity> credentialsById = legacyCredentialIds.isEmpty()
            ? Map.of()
            : loadCredentialMap(legacyCredentialIds);

        List<WorkspaceLinuxPasswordAccessAuditDto> items = audits.stream()
            .map(audit -> toAuditDto(
                audit,
                serversById.get(audit.getServerId()),
                credentialsById.get(audit.getCredentialId())
            ))
            .toList();

        return new WorkspaceLinuxPasswordAccessAuditListResponse(items, normalizedPage, normalizedPageSize, total);
    }

    public WorkspaceLinuxPasswordSecretResponse revealCredentialSecret(Long credentialId, String action, String clientIp, String userAgent) {
        AuthenticatedAccount current = authContextService.requireLogin();
        String normalizedAction = normalizeSecretAction(action);
        LinuxPasswordCredentialEntity credential = linuxPasswordCredentialMapper.selectById(credentialId);
        if (credential == null) {
            writeAccessAudit(current, null, credentialId, normalizedAction, "FAILED", clientIp, userAgent);
            throw new ResourceNotFoundException("LinuxPasswordCredential", "id", credentialId);
        }
        LinuxPasswordServerEntity server;
        try {
            server = requireServer(credential.getServerId());
        } catch (RuntimeException ex) {
            writeAccessAudit(current, null, credential.getId(), normalizedAction, "FAILED", clientIp, userAgent);
            throw ex;
        }
        try {
            String password = linuxPasswordSecretService.decrypt(credential.getPasswordCiphertext(), credential.getPasswordIv());
            writeAccessAudit(
                current, server.getId(), credential.getId(), normalizedAction, "SUCCESS",
                clientIp, userAgent,
                server.getHostname(), server.getIp(), credential.getUsername()
            );
            return new WorkspaceLinuxPasswordSecretResponse(password);
        } catch (RuntimeException ex) {
            writeAccessAudit(
                current, server.getId(), credential.getId(), normalizedAction, "FAILED",
                clientIp, userAgent,
                server.getHostname(), server.getIp(), credential.getUsername()
            );
            throw ex;
        }
    }

    @Transactional
    public WorkspaceLinuxPasswordDto createServer(WorkspaceLinuxPasswordUpsertRequest request) {
        authContextService.requireLogin();
        LinuxPasswordServerEntity entity = new LinuxPasswordServerEntity();
        applyForCreate(entity, request);
        ensureUnique(entity, null);
        linuxPasswordServerMapper.insert(entity);

        List<String> businessUnits = normalizeBusinessUnits(request.getBusinessUnits());
        List<WorkspaceLinuxPasswordUpsertRequest.CredentialRequest> credentials = normalizeCredentialRequests(request, true);
        replaceCredentials(entity.getId(), credentials, Map.of());
        ensureDirectoriesExist(businessUnits);
        replaceBusinessUnits(entity.getId(), businessUnits);
        workspaceOperationLogService.log(
            authContextService.currentActor("system"),
            "Create linux password server",
            "workspace_linux_password_server",
            entity.getId(),
            "Hostname=" + entity.getHostname()
        );
        return toDto(entity, credentialsForServer(loadCredentialsByServerId(List.of(entity)), entity.getId()), businessUnits);
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
        List<WorkspaceLinuxPasswordUpsertRequest.CredentialRequest> credentials = normalizeCredentialRequests(request, false);
        Map<Long, LinuxPasswordCredentialEntity> existingCredentialsById = linuxPasswordCredentialMapper.selectList(
                Wrappers.<LinuxPasswordCredentialEntity>lambdaQuery().eq(LinuxPasswordCredentialEntity::getServerId, id))
            .stream()
            .filter(credential -> credential.getId() != null)
            .collect(Collectors.toMap(LinuxPasswordCredentialEntity::getId, Function.identity(), (left, right) -> left));
        replaceCredentials(id, credentials, existingCredentialsById);
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
        return toDto(entity, credentialsForServer(loadCredentialsByServerId(List.of(entity)), entity.getId()), businessUnits);
    }

    @Transactional
    public void deleteServer(Long id) {
        authContextService.requireAdmin();
        LinuxPasswordServerEntity entity = requireServer(id);
        List<String> previousBusinessUnits = loadBusinessUnitsByServerId(List.of(id)).getOrDefault(id, List.of(DEFAULT_BUSINESS_UNIT));
        linuxPasswordCredentialMapper.delete(Wrappers.<LinuxPasswordCredentialEntity>lambdaQuery()
            .eq(LinuxPasswordCredentialEntity::getServerId, id));
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
        entity.setUsername(null);
        entity.setPassword(null);
        entity.setStatus(DEFAULT_STATUS);
    }

    private void applyForUpdate(LinuxPasswordServerEntity entity, WorkspaceLinuxPasswordUpsertRequest request) {
        entity.setHostname(normalizeRequired(request.getHostname(), "Hostname is required."));
        entity.setIp(normalizeRequired(request.getIp(), "IP address is required."));
        entity.setUsername(null);
        entity.setPassword(null);
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
        List<String> normalized = normalizeDistinctNames(businessUnits);
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
            String normalizedKey = normalizeDirectoryKey(directoryName);
            if (directoriesByNormalizedName.containsKey(normalizedKey)) {
                continue;
            }
            LinuxPasswordDirectoryEntity entity = new LinuxPasswordDirectoryEntity();
            entity.setName(directoryName);
            linuxPasswordDirectoryMapper.insert(entity);
            directoriesByNormalizedName.put(normalizedKey, entity);
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
            .toList();
        relationDirectories = normalizeDistinctNames(relationDirectories);
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

    private Map<Long, List<LinuxPasswordCredentialEntity>> loadCredentialsByServerId(List<LinuxPasswordServerEntity> servers) {
        List<Long> serverIds = servers.stream()
            .map(LinuxPasswordServerEntity::getId)
            .filter(Objects::nonNull)
            .toList();
        if (serverIds.isEmpty()) {
            return Map.of();
        }
        List<LinuxPasswordCredentialEntity> credentials = linuxPasswordCredentialMapper.selectList(Wrappers.<LinuxPasswordCredentialEntity>lambdaQuery()
            .in(LinuxPasswordCredentialEntity::getServerId, serverIds)
            .orderByAsc(LinuxPasswordCredentialEntity::getUsername));
        return credentials.stream()
            .collect(Collectors.groupingBy(
                LinuxPasswordCredentialEntity::getServerId,
                Collectors.toList()
            ));
    }

    private Map<Long, LinuxPasswordCredentialEntity> loadCredentialMap(List<Long> credentialIds) {
        List<Long> ids = credentialIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return linuxPasswordCredentialMapper.selectList(Wrappers.<LinuxPasswordCredentialEntity>lambdaQuery()
                .in(LinuxPasswordCredentialEntity::getId, ids))
            .stream()
            .filter(credential -> credential.getId() != null)
            .collect(Collectors.toMap(LinuxPasswordCredentialEntity::getId, Function.identity(), (left, right) -> left));
    }

    private Map<Long, LinuxPasswordServerEntity> loadServerMap(List<Long> serverIds) {
        List<Long> ids = serverIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return linuxPasswordServerMapper.selectList(Wrappers.<LinuxPasswordServerEntity>lambdaQuery()
                .in(LinuxPasswordServerEntity::getId, ids))
            .stream()
            .filter(server -> server.getId() != null)
            .collect(Collectors.toMap(LinuxPasswordServerEntity::getId, Function.identity(), (left, right) -> left));
    }

    private List<LinuxPasswordCredentialEntity> credentialsForServer(
            Map<Long, List<LinuxPasswordCredentialEntity>> credentialsByServerId,
            Long serverId) {
        if (serverId == null) {
            return List.of();
        }
        return credentialsByServerId.getOrDefault(serverId, List.of());
    }

    private void backfillLegacyCredentialsIfNeeded(
            List<LinuxPasswordServerEntity> servers,
            Map<Long, List<LinuxPasswordCredentialEntity>> credentialsByServerId) {
        // Legacy lazy backfill - kept for backwards compatibility but explicit startup
        // backfill via LinuxPasswordLegacyBackfillRunner is the preferred path.
        for (LinuxPasswordServerEntity server : servers) {
            if (server.getId() == null
                    || !credentialsByServerId.getOrDefault(server.getId(), List.of()).isEmpty()
                    || !hasText(server.getUsername())
                    || !hasText(server.getPassword())) {
                continue;
            }
            LinuxPasswordCredentialEntity credential = buildCredential(server.getId(), server.getUsername(), server.getPassword(), null);
            linuxPasswordCredentialMapper.insert(credential);
            credentialsByServerId.put(server.getId(), List.of(credential));
        }
    }

    private List<WorkspaceLinuxPasswordUpsertRequest.CredentialRequest> normalizeCredentialRequests(WorkspaceLinuxPasswordUpsertRequest request, boolean requirePasswordForAll) {
        List<WorkspaceLinuxPasswordUpsertRequest.CredentialRequest> credentials = request.getCredentials();
        if (credentials == null || credentials.isEmpty()) {
            if (hasText(request.getUsername()) || hasText(request.getPassword())) {
                WorkspaceLinuxPasswordUpsertRequest.CredentialRequest credential = new WorkspaceLinuxPasswordUpsertRequest.CredentialRequest();
                credential.setUsername(request.getUsername());
                credential.setPassword(request.getPassword());
                credentials = List.of(credential);
            } else {
                credentials = List.of();
            }
        }
        if (credentials.isEmpty()) {
            throw new BadRequestException("At least one login account is required.");
        }
        LinkedHashSet<String> usernames = new LinkedHashSet<>();
        for (WorkspaceLinuxPasswordUpsertRequest.CredentialRequest credential : credentials) {
            String username = normalizeRequired(credential.getUsername(), "Username is required.");
            if (requirePasswordForAll || credential.getId() == null) {
                normalizeRequired(credential.getPassword(), "Password is required.");
            }
            String normalizedUsername = username.toLowerCase(Locale.ROOT);
            if (!usernames.add(normalizedUsername)) {
                throw new BadRequestException("Username already exists on this machine.");
            }
        }
        return credentials;
    }

    private void replaceCredentials(
            Long serverId,
            List<WorkspaceLinuxPasswordUpsertRequest.CredentialRequest> credentials,
            Map<Long, LinuxPasswordCredentialEntity> existingCredentialsById) {
        linuxPasswordCredentialMapper.delete(Wrappers.<LinuxPasswordCredentialEntity>lambdaQuery()
            .eq(LinuxPasswordCredentialEntity::getServerId, serverId));
        for (WorkspaceLinuxPasswordUpsertRequest.CredentialRequest credential : credentials) {
            LinuxPasswordCredentialEntity existingCredential = credential.getId() == null ? null : existingCredentialsById.get(credential.getId());
            if (!hasText(credential.getPassword()) && existingCredential != null) {
                LinuxPasswordCredentialEntity next = new LinuxPasswordCredentialEntity();
                next.setServerId(serverId);
                next.setUsername(normalizeRequired(credential.getUsername(), "Username is required."));
                next.setPasswordCiphertext(existingCredential.getPasswordCiphertext());
                next.setPasswordIv(existingCredential.getPasswordIv());
                next.setKeyVersion(existingCredential.getKeyVersion());
                next.setNotes(normalizeOptional(credential.getNotes()));
                linuxPasswordCredentialMapper.insert(next);
                continue;
            }
            linuxPasswordCredentialMapper.insert(buildCredential(serverId, normalizeRequired(credential.getUsername(), "Username is required."),
                normalizeRequired(credential.getPassword(), "Password is required."), normalizeOptional(credential.getNotes())));
        }
    }

    private LinuxPasswordCredentialEntity buildCredential(Long serverId, String username, String password, String notes) {
        LinuxPasswordSecretService.EncryptedSecret encryptedSecret = linuxPasswordSecretService.encrypt(password);
        LinuxPasswordCredentialEntity entity = new LinuxPasswordCredentialEntity();
        entity.setServerId(serverId);
        entity.setUsername(username.trim());
        entity.setPasswordCiphertext(encryptedSecret.ciphertext());
        entity.setPasswordIv(encryptedSecret.iv());
        entity.setKeyVersion(encryptedSecret.keyVersion());
        entity.setNotes(notes);
        return entity;
    }

    private String normalizeSecretAction(String action) {
        String normalized = normalizeRequired(action, "Password access action is required.").toUpperCase(Locale.ROOT);
        if (!List.of("VIEW", "COPY").contains(normalized)) {
            throw new BadRequestException("Password access action must be VIEW or COPY.");
        }
        return normalized;
    }

    private String normalizeAuditEnum(String value) {
        String normalized = normalizeOptional(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private LocalDateTime parseDateTimeBoundary(String value, boolean endOfDay) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            return null;
        }
        try {
            if (normalized.length() <= 10) {
                LocalDate date = LocalDate.parse(normalized);
                return endOfDay ? date.plusDays(1).atStartOfDay().minusNanos(1) : date.atStartOfDay();
            }
            return LocalDateTime.parse(normalized);
        } catch (RuntimeException ex) {
            throw new BadRequestException("Invalid audit time range.");
        }
    }

    private String normalizeSearchKey(String value) {
        String normalized = normalizeOptional(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private boolean containsIgnoreCase(String value, String normalizedNeedle) {
        return normalizedNeedle == null || (value != null && value.toLowerCase(Locale.ROOT).contains(normalizedNeedle));
    }

    private boolean matchesAuditKeyword(WorkspaceLinuxPasswordAccessAuditDto audit, String normalizedKeyword) {
        if (normalizedKeyword == null) {
            return true;
        }
        return List.of(
                audit.getStaffId(),
                audit.getStaffName(),
                audit.getHostname(),
                audit.getIp(),
                audit.getUsername(),
                audit.getAction(),
                audit.getResult(),
                audit.getClientIp()
            )
            .stream()
            .filter(Objects::nonNull)
            .map(value -> value.toLowerCase(Locale.ROOT))
            .anyMatch(value -> value.contains(normalizedKeyword));
    }

    private WorkspaceLinuxPasswordAccessAuditDto toAuditDto(
            LinuxPasswordAccessAuditEntity audit,
            LinuxPasswordServerEntity server,
            LinuxPasswordCredentialEntity credential) {
        // Prefer persisted snapshots; fall back to live join for legacy records without snapshots
        String hostname = audit.getHostnameSnapshot() != null
            ? audit.getHostnameSnapshot()
            : (server != null ? server.getHostname() : null);
        String ip = audit.getIpSnapshot() != null
            ? audit.getIpSnapshot()
            : (server != null ? server.getIp() : null);
        String username = audit.getUsernameSnapshot() != null
            ? audit.getUsernameSnapshot()
            : (credential != null ? credential.getUsername() : null);
        return new WorkspaceLinuxPasswordAccessAuditDto(
            audit.getId(),
            audit.getAccountId(),
            audit.getStaffRecordId(),
            audit.getStaffId(),
            audit.getStaffName(),
            audit.getServerId(),
            hostname,
            ip,
            audit.getCredentialId(),
            username,
            audit.getAction(),
            audit.getResult(),
            audit.getClientIp(),
            audit.getUserAgent(),
            audit.getCreateTime()
        );
    }

    private void writeAccessAudit(
            AuthenticatedAccount current,
            Long serverId,
            Long credentialId,
            String action,
            String result,
            String clientIp,
            String userAgent) {
        writeAccessAudit(current, serverId, credentialId, action, result, clientIp, userAgent, null, null, null);
    }

    private void writeAccessAudit(
            AuthenticatedAccount current,
            Long serverId,
            Long credentialId,
            String action,
            String result,
            String clientIp,
            String userAgent,
            String hostnameSnapshot,
            String ipSnapshot,
            String usernameSnapshot) {
        LinuxPasswordAccessAuditEntity audit = new LinuxPasswordAccessAuditEntity();
        audit.setAccountId(current.accountId());
        audit.setStaffRecordId(current.staffRecordId());
        audit.setStaffId(current.staffId());
        audit.setStaffName(current.staffName());
        audit.setServerId(serverId);
        audit.setCredentialId(credentialId);
        audit.setAction(action);
        audit.setResult(result);
        audit.setClientIp(truncate(clientIp, 128));
        audit.setUserAgent(truncate(userAgent, 500));
        audit.setHostnameSnapshot(truncate(hostnameSnapshot, 255));
        audit.setIpSnapshot(truncate(ipSnapshot, 128));
        audit.setUsernameSnapshot(truncate(usernameSnapshot, 255));
        linuxPasswordAccessAuditMapper.insert(audit);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
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

    private List<String> normalizeDistinctNames(List<String> values) {
        if (values == null) {
            return List.of();
        }
        Map<String, String> valuesByNormalizedKey = values.stream()
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .collect(Collectors.toMap(
                value -> value.toLowerCase(Locale.ROOT),
                Function.identity(),
                (left, right) -> left,
                java.util.LinkedHashMap::new
            ));
        return valuesByNormalizedKey.values().stream().toList();
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

    private WorkspaceLinuxPasswordDto toDto(
            LinuxPasswordServerEntity entity,
            List<LinuxPasswordCredentialEntity> credentials,
            List<String> businessUnits) {
        return new WorkspaceLinuxPasswordDto(
            entity.getId(),
            entity.getHostname(),
            entity.getIp(),
            credentials.stream()
                .map(credential -> new WorkspaceLinuxPasswordCredentialDto(
                    credential.getId(),
                    credential.getUsername(),
                    credential.getNotes(),
                    hasText(credential.getPasswordCiphertext())
                ))
                .toList(),
            businessUnits,
            entity.getStatus()
        );
    }
}
