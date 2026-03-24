package com.support.server.supportrosterserver.service.workspace;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.support.server.supportrosterserver.auth.AuthenticatedAccount;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceAccessPolicyResponse;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceAccessPolicyUpdateRequest;
import com.support.server.supportrosterserver.dto.workspace.WorkspacePageAccessPolicyDto;
import com.support.server.supportrosterserver.entity.workspace.WorkspaceAccessPolicyEntity;
import com.support.server.supportrosterserver.exception.BadRequestException;
import com.support.server.supportrosterserver.mapper.WorkspaceAccessPolicyMapper;
import com.support.server.supportrosterserver.service.auth.AuthContextService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WorkspaceAccessPolicyService {

    private static final String POLICY_RESOURCE_TYPE = "workspace_access_policy";

    private final WorkspaceAccessPolicyMapper workspaceAccessPolicyMapper;
    private final AuthContextService authContextService;
    private final WorkspaceOperationLogService workspaceOperationLogService;

    public WorkspaceAccessPolicyResponse getAccessPolicy() {
        return new WorkspaceAccessPolicyResponse(listPagePolicies());
    }

    public boolean isAnonymousReadAllowed(String method, String requestPath) {
        if (authContextService.isLoggedIn()) {
            return true;
        }

        String normalizedMethod = method == null ? "" : method.trim().toUpperCase(Locale.ROOT);
        if (!HttpMethod.GET.matches(normalizedMethod) && !HttpMethod.HEAD.matches(normalizedMethod)) {
            return false;
        }

        String normalizedPath = normalizePath(requestPath);
        if ("/api/workspace/access-policy".equals(normalizedPath)) {
            return true;
        }

        String pageCode = resolvePageCode(normalizedPath);
        return pageCode != null && !isWorkspacePageAuthRequired(pageCode);
    }

    public boolean isWorkspacePageAuthRequired(String pageCode) {
        PageDefinition definition = requireDefinition(pageCode);
        return currentPolicyMap().getOrDefault(definition.pageCode(), definition.authRequiredByDefault());
    }

    @Transactional
    public WorkspaceAccessPolicyResponse updateAccessPolicy(WorkspaceAccessPolicyUpdateRequest request) {
        authContextService.requireAdmin();

        Map<String, WorkspacePageAccessPolicyDto> requestedPolicies = request.getPages().stream()
            .filter(Objects::nonNull)
            .collect(Collectors.toMap(
                policy -> normalizePageCode(policy.getPageCode()),
                Function.identity(),
                (left, right) -> right,
                LinkedHashMap::new
            ));

        List<PageDefinition> configurableDefinitions = PAGE_DEFINITIONS.values().stream()
            .filter(PageDefinition::configurable)
            .toList();

        if (requestedPolicies.size() != configurableDefinitions.size()) {
            throw new BadRequestException("All configurable workspace page policies must be provided.");
        }

        for (PageDefinition definition : configurableDefinitions) {
            WorkspacePageAccessPolicyDto requested = requestedPolicies.get(definition.pageCode());
            if (requested == null) {
                throw new BadRequestException("Missing workspace page policy: " + definition.pageCode());
            }
            if (requested.getAuthRequired() == null) {
                throw new BadRequestException("Auth requirement is required for page: " + definition.pageCode());
            }
            upsertPolicy(definition.pageCode(), requested.getAuthRequired());
        }

        AuthenticatedAccount current = authContextService.requireLogin();
        workspaceOperationLogService.log(
            current.staffName(),
            "Update workspace access policy",
            POLICY_RESOURCE_TYPE,
            0L,
            configurableDefinitions.stream()
                .map(definition -> definition.pageCode() + "=" + isWorkspacePageAuthRequired(definition.pageCode()))
                .collect(Collectors.joining(", "))
        );
        return getAccessPolicy();
    }

    private List<WorkspacePageAccessPolicyDto> listPagePolicies() {
        Map<String, Boolean> currentPolicyMap = currentPolicyMap();
        return PAGE_DEFINITIONS.values().stream()
            .map(definition -> new WorkspacePageAccessPolicyDto(
                definition.pageCode(),
                currentPolicyMap.getOrDefault(definition.pageCode(), definition.authRequiredByDefault()),
                definition.configurable()
            ))
            .toList();
    }

    private void upsertPolicy(String pageCode, boolean authRequired) {
        WorkspaceAccessPolicyEntity existing = workspaceAccessPolicyMapper.selectOne(Wrappers.<WorkspaceAccessPolicyEntity>lambdaQuery()
            .eq(WorkspaceAccessPolicyEntity::getPageCode, pageCode)
            .last("limit 1"));

        if (existing == null) {
            WorkspaceAccessPolicyEntity entity = new WorkspaceAccessPolicyEntity();
            entity.setPageCode(pageCode);
            entity.setAuthRequired(authRequired);
            workspaceAccessPolicyMapper.insert(entity);
            return;
        }

        existing.setAuthRequired(authRequired);
        workspaceAccessPolicyMapper.updateById(existing);
    }

    private Map<String, Boolean> currentPolicyMap() {
        return workspaceAccessPolicyMapper.selectList(Wrappers.<WorkspaceAccessPolicyEntity>lambdaQuery()
                .in(WorkspaceAccessPolicyEntity::getPageCode, PAGE_DEFINITIONS.keySet()))
            .stream()
            .collect(Collectors.toMap(
                WorkspaceAccessPolicyEntity::getPageCode,
                entity -> Boolean.TRUE.equals(entity.getAuthRequired()),
                (left, right) -> right,
                LinkedHashMap::new
            ));
    }

    private String resolvePageCode(String normalizedPath) {
        if ("/api/workspace/overview".equals(normalizedPath)) {
            return "overview";
        }
        if ("/api/workspace/roster".equals(normalizedPath)) {
            return "roster";
        }
        if (normalizedPath.startsWith("/api/workspace/staff")) {
            return "staff";
        }
        if (normalizedPath.startsWith("/api/workspace/shift-definitions")) {
            return "shifts";
        }
        if ("/api/workspace/validation".equals(normalizedPath)) {
            return "validation";
        }
        if ("/api/workspace/import-export/export".equals(normalizedPath)
                || "/api/workspace/import-export/template".equals(normalizedPath)) {
            return "import-export";
        }
        if (normalizedPath.startsWith("/api/workspace/teams")) {
            return "teams";
        }
        if (normalizedPath.startsWith("/api/workspace/accounts")) {
            return "accounts";
        }
        return null;
    }

    private String normalizePageCode(String pageCode) {
        if (pageCode == null || pageCode.isBlank()) {
            throw new BadRequestException("Workspace page code is required.");
        }
        return pageCode.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizePath(String requestPath) {
        if (requestPath == null || requestPath.isBlank()) {
            return "";
        }
        String normalized = requestPath.trim();
        return normalized.endsWith("/") && normalized.length() > 1
            ? normalized.substring(0, normalized.length() - 1)
            : normalized;
    }

    private PageDefinition requireDefinition(String pageCode) {
        PageDefinition definition = PAGE_DEFINITIONS.get(normalizePageCode(pageCode));
        if (definition == null) {
            throw new BadRequestException("Unsupported workspace page code: " + pageCode);
        }
        return definition;
    }

    private static final Map<String, PageDefinition> PAGE_DEFINITIONS = buildPageDefinitions();

    private static Map<String, PageDefinition> buildPageDefinitions() {
        Map<String, PageDefinition> definitions = new LinkedHashMap<>();
        definitions.put("overview", new PageDefinition("overview", false, true));
        definitions.put("roster", new PageDefinition("roster", false, true));
        definitions.put("staff", new PageDefinition("staff", false, true));
        definitions.put("shifts", new PageDefinition("shifts", false, true));
        definitions.put("validation", new PageDefinition("validation", false, true));
        definitions.put("import-export", new PageDefinition("import-export", false, true));
        definitions.put("teams", new PageDefinition("teams", false, true));
        definitions.put("accounts", new PageDefinition("accounts", true, false));
        return definitions;
    }

    private record PageDefinition(String pageCode, boolean authRequiredByDefault, boolean configurable) {
    }
}
