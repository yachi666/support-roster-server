package com.support.server.supportrosterserver.service.workspace;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceStaffDto;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceStaffBatchCreateRequest;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceStaffUpsertRequest;
import com.support.server.supportrosterserver.auth.AuthenticatedAccount;
import com.support.server.supportrosterserver.dto.employee.EmployeeDirectoryLookupResponse;
import com.support.server.supportrosterserver.entity.auth.WorkspaceAccountEntity;
import com.support.server.supportrosterserver.entity.auth.WorkspaceAccountTeamScopeEntity;
import com.support.server.supportrosterserver.entity.workspace.RosterAssignmentEntity;
import com.support.server.supportrosterserver.entity.workspace.StaffEntity;
import com.support.server.supportrosterserver.entity.workspace.TeamEntity;
import com.support.server.supportrosterserver.exception.BadRequestException;
import com.support.server.supportrosterserver.mapper.RosterAssignmentMapper;
import com.support.server.supportrosterserver.mapper.StaffMapper;
import com.support.server.supportrosterserver.mapper.WorkspaceAccountMapper;
import com.support.server.supportrosterserver.mapper.WorkspaceAccountTeamScopeMapper;
import com.support.server.supportrosterserver.exception.ResourceNotFoundException;
import com.support.server.supportrosterserver.service.AvatarUrlResolver;
import com.support.server.supportrosterserver.service.auth.AuthContextService;
import com.support.server.supportrosterserver.service.auth.AuthTokenVersionService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WorkspaceStaffService {

    private final AvatarUrlResolver avatarUrlResolver;
    private final WorkspaceStaffProfileSupport staffProfileSupport;
    private final StaffMapper staffMapper;
    private final RosterAssignmentMapper rosterAssignmentMapper;
    private final WorkspaceLookupService lookupService;
    private final WorkspaceAccountMapper workspaceAccountMapper;
    private final WorkspaceAccountTeamScopeMapper workspaceAccountTeamScopeMapper;
    private final AuthContextService authContextService;
    private final AuthTokenVersionService authTokenVersionService;
    private final WorkspaceOperationLogService workspaceOperationLogService;

    public List<WorkspaceStaffDto> listStaff(String keyword) {
        LambdaQueryWrapper<StaffEntity> query = Wrappers.<StaffEntity>lambdaQuery()
            .orderByAsc(StaffEntity::getStaffId)
            .orderByAsc(StaffEntity::getName);
        if (!shouldBypassTeamScopeFilter()) {
            List<Long> readableTeamIds = authContextService.readableTeamIds();
            if (!readableTeamIds.isEmpty()) {
                query.in(StaffEntity::getTeamId, readableTeamIds);
            }
        }
        if (keyword != null && !keyword.isBlank()) {
            query.and(wrapper -> wrapper
                .like(StaffEntity::getName, keyword)
                .or().like(StaffEntity::getEmail, keyword)
                .or().like(StaffEntity::getStaffId, keyword)
                .or().like(StaffEntity::getRoleName, keyword)
                .or().like(StaffEntity::getRegion, keyword));
        }

        Map<Long, TeamEntity> teamMap = lookupService.teamMap();
        return staffMapper.selectList(query).stream()
            .map(staff -> toDto(staff, resolveTeam(teamMap, staff.getTeamId())))
            .toList();
    }

    public WorkspaceStaffDto getStaff(Long id) {
        StaffEntity entity = staffMapper.selectById(id);
        if (entity == null) {
            throw new ResourceNotFoundException("Staff", "id", id);
        }
        requireReadableExistingStaff(entity);
        return toDto(entity, resolveTeam(lookupService.teamMap(), entity.getTeamId()));
    }

    @Transactional
    public WorkspaceStaffDto createStaff(WorkspaceStaffUpsertRequest request) {
        authContextService.requireWritableTeam(request.getTeamId());
        String staffId = normalizeRequiredText(request.getStaffId(), "Staff ID is required.");
        ensureStaffIdsAvailable(List.of(staffId), null);
        EmployeeDirectoryLookupResponse employee = staffProfileSupport.lookupEmployeeSafely(staffId);
        StaffEntity entity = new StaffEntity();
        applyCreate(entity, staffId, employee, request);
        staffMapper.insert(entity);
        WorkspaceStaffDto created = getStaff(entity.getId());
        workspaceOperationLogService.log(
            authContextService.currentActor("system"),
            "Create workspace staff",
            "workspace_staff",
            entity.getId(),
            "Staff ID=" + created.getStaffId()
        );
        return created;
    }

    @Transactional
    public List<WorkspaceStaffDto> createStaffBatch(WorkspaceStaffBatchCreateRequest request) {
        authContextService.requireWritableTeam(request.getTeamId());
        lookupService.requireTeam(request.getTeamId());

        List<String> staffIds = normalizeBatchStaffIds(request.getStaffIds());
        ensureBatchDoesNotContainDuplicates(staffIds);
        ensureStaffIdsAvailable(staffIds, null);

        List<WorkspaceStaffDto> createdStaff = new ArrayList<>();
        for (String staffId : staffIds) {
            EmployeeDirectoryLookupResponse employee = staffProfileSupport.lookupEmployeeSafely(staffId);
            StaffEntity entity = new StaffEntity();
            applyEmployeeLookup(entity, staffId, employee, request);
            staffMapper.insert(entity);
            createdStaff.add(getStaff(entity.getId()));
        }
        return createdStaff;
    }

    @Transactional
    public WorkspaceStaffDto updateStaff(Long id, WorkspaceStaffUpsertRequest request) {
        StaffEntity entity = staffMapper.selectById(id);
        if (entity == null) {
            throw new ResourceNotFoundException("Staff", "id", id);
        }
        requireWritableExistingStaff(entity);
        authContextService.requireWritableTeam(request.getTeamId());
        ensureStaffIdsAvailable(List.of(normalizeRequiredText(request.getStaffId(), "Staff ID is required.")), id);
        String previousStaffId = entity.getStaffId();
        apply(entity, request);
        staffMapper.updateById(entity);
        syncLinkedAccountStaffId(entity.getId(), previousStaffId, entity.getStaffId());
        WorkspaceStaffDto updated = getStaff(id);
        workspaceOperationLogService.log(
            authContextService.currentActor("system"),
            "Update workspace staff",
            "workspace_staff",
            updated.getId(),
            "Staff ID=" + updated.getStaffId()
        );
        return updated;
    }

    @Transactional
    public void deleteStaff(Long id) {
        StaffEntity entity = staffMapper.selectById(id);
        if (entity == null) {
            throw new ResourceNotFoundException("Staff", "id", id);
        }
        requireWritableExistingStaff(entity);
        String actor = authContextService.currentActor("system");
        WorkspaceAccountEntity linkedAccount = workspaceAccountMapper.selectOne(Wrappers.<WorkspaceAccountEntity>lambdaQuery()
            .eq(WorkspaceAccountEntity::getStaffRecordId, id)
            .last("limit 1"));
        if (linkedAccount != null) {
            authTokenVersionService.bumpTokenVersion(linkedAccount);
            workspaceAccountTeamScopeMapper.delete(Wrappers.<WorkspaceAccountTeamScopeEntity>lambdaQuery()
                .eq(WorkspaceAccountTeamScopeEntity::getAccountId, linkedAccount.getId()));
            workspaceAccountMapper.deleteById(linkedAccount.getId());
        }
        staffMapper.deleteById(id);
        workspaceOperationLogService.log(
            actor,
            "Delete workspace staff",
            "workspace_staff",
            id,
            linkedAccount == null
                ? "Staff ID=" + entity.getStaffId()
                : "Staff ID=" + entity.getStaffId() + "; removed linked workspace account"
        );
    }

    public List<com.support.server.supportrosterserver.dto.StaffDto> listViewerStaff() {
        return staffMapper.selectList(Wrappers.<StaffEntity>lambdaQuery()
                .eq(StaffEntity::getStatus, "Active")
                .orderByAsc(StaffEntity::getName))
            .stream()
            .map(entity -> new com.support.server.supportrosterserver.dto.StaffDto(
                entity.getId(),
                entity.getName(),
                avatarUrlResolver.resolve(entity.getStaffId()),
                entity.getEmail(),
                entity.getPhone(),
                entity.getSlack(),
                entity.getRegion(),
                entity.getPhone(),
                List.of()
            ))
            .toList();
    }

    public com.support.server.supportrosterserver.dto.StaffDto getViewerStaff(Long id) {
        StaffEntity entity = staffMapper.selectById(id);
        if (entity == null) {
            return null;
        }
        TeamEntity team = resolveTeam(lookupService.teamMap(), entity.getTeamId());
        return new com.support.server.supportrosterserver.dto.StaffDto(
            entity.getId(),
            entity.getName(),
            avatarUrlResolver.resolve(entity.getStaffId()),
            entity.getEmail(),
            entity.getPhone(),
            entity.getSlack(),
            entity.getRegion(),
            entity.getPhone(),
            team == null ? List.of() : List.of(team.getName())
        );
    }

    private WorkspaceStaffDto toDto(StaffEntity entity, TeamEntity team) {
        YearMonth currentMonth = YearMonth.now();
        List<RosterAssignmentEntity> assignments = rosterAssignmentMapper.selectList(Wrappers.<RosterAssignmentEntity>lambdaQuery()
            .eq(RosterAssignmentEntity::getStaffId, entity.getId())
            .between(RosterAssignmentEntity::getAssignmentDate, currentMonth.atDay(1), currentMonth.atEndOfMonth()));
        Map<String, String> tags = new LinkedHashMap<>();
        if (team != null) {
            tags.put("team", team.getName());
        }
        tags.put("assignments", assignments.size() + " shifts this month");
        return new WorkspaceStaffDto(
            entity.getId(),
            entity.getStaffId(),
            entity.getName(),
            entity.getEmail(),
            entity.getPhone(),
            entity.getSlack(),
            entity.getRegion(),
            lookupService.normalizeWorkspaceTimezone(entity.getTimezone()),
            entity.getRoleName(),
            entity.getTeamId(),
            team == null ? null : team.getName(),
            entity.getStatus(),
            avatarUrlResolver.resolve(entity.getStaffId()),
            entity.getNotes(),
            new ArrayList<>(tags.values())
        );
    }

    private void apply(StaffEntity entity, WorkspaceStaffUpsertRequest request) {
        lookupService.requireTeam(request.getTeamId());
        String staffId = normalizeRequiredText(request.getStaffId(), "Staff ID is required.");
        String requestedName = normalizeOptionalText(request.getName());
        String requestedEmail = normalizeOptionalText(request.getEmail());
        EmployeeDirectoryLookupResponse employee = shouldLookupMissingProfileFields(requestedName, requestedEmail)
            ? staffProfileSupport.lookupEmployeeSafely(staffId)
            : null;

        entity.setStaffId(staffId);
        entity.setName(resolveUpdateName(entity, staffId, requestedName, employee));
        entity.setEmail(resolveUpdateEmail(entity, requestedEmail, employee));
        entity.setPhone(normalizeOptionalText(request.getPhone()));
        entity.setSlack(normalizeOptionalText(request.getSlack()));
        entity.setRegion(normalizeOptionalText(request.getRegion()));
        entity.setTimezone(lookupService.normalizeWorkspaceTimezone(normalizeOptionalText(request.getTimezone())));
        entity.setRoleName(normalizeOptionalText(request.getRoleName()));
        entity.setTeamId(request.getTeamId());
        entity.setRoleGroupId(null);
        entity.setStatus(resolveStatus(request.getStatus()));
        entity.setAvatar(request.getAvatar());
        entity.setNotes(normalizeOptionalText(request.getNotes()));
    }

    private void applyCreate(
            StaffEntity entity,
            String staffId,
            EmployeeDirectoryLookupResponse employee,
            WorkspaceStaffUpsertRequest request) {
        lookupService.requireTeam(request.getTeamId());
        entity.setStaffId(staffId);
        entity.setName(resolveCreateName(staffId, request.getName(), employee));
        entity.setEmail(staffProfileSupport.resolvePreferredText(request.getEmail(), employee == null ? null : employee.emailAddress()));
        entity.setPhone(normalizeOptionalText(request.getPhone()));
        entity.setSlack(normalizeOptionalText(request.getSlack()));
        entity.setRegion(staffProfileSupport.resolvePreferredText(request.getRegion(), staffProfileSupport.buildRegion(employee)));
        entity.setTimezone(lookupService.normalizeWorkspaceTimezone(normalizeOptionalText(request.getTimezone())));
        entity.setRoleName(staffProfileSupport.resolvePreferredText(request.getRoleName(), employee == null ? null : employee.roleFromLDAP()));
        entity.setTeamId(request.getTeamId());
        entity.setRoleGroupId(null);
        entity.setStatus(resolveStatus(request.getStatus()));
        entity.setAvatar(request.getAvatar());
        entity.setNotes(normalizeOptionalText(request.getNotes()));
    }

    private void applyEmployeeLookup(
            StaffEntity entity,
            String staffId,
            EmployeeDirectoryLookupResponse employee,
            WorkspaceStaffBatchCreateRequest request) {
        entity.setStaffId(staffId);
        entity.setName(staffProfileSupport.resolveEmployeeName(staffId, employee));
        entity.setEmail(employee == null ? null : normalizeOptionalText(employee.emailAddress()));
        entity.setPhone(null);
        entity.setSlack(null);
        entity.setRegion(staffProfileSupport.buildRegion(employee));
        entity.setTimezone(lookupService.normalizeWorkspaceTimezone(normalizeOptionalText(request.getTimezone())));
        entity.setRoleName(employee == null ? null : normalizeOptionalText(employee.roleFromLDAP()));
        entity.setTeamId(request.getTeamId());
        entity.setRoleGroupId(null);
        entity.setStatus(resolveStatus(request.getStatus()));
        entity.setAvatar(null);
        entity.setNotes(normalizeOptionalText(request.getNotes()));
    }

    private String resolveCreateName(String staffId, String requestedName, EmployeeDirectoryLookupResponse employee) {
        String normalizedRequestedName = normalizeOptionalText(requestedName);
        if (normalizedRequestedName != null) {
            return normalizedRequestedName;
        }
        return staffProfileSupport.resolveEmployeeName(staffId, employee);
    }

    private String resolveUpdateName(StaffEntity entity, String staffId, String requestedName, EmployeeDirectoryLookupResponse employee) {
        if (requestedName != null) {
            return requestedName;
        }
        if (employee == null) {
            return normalizeOptionalText(entity.getName());
        }
        return staffProfileSupport.resolveEmployeeName(staffId, employee);
    }

    private String resolveUpdateEmail(StaffEntity entity, String requestedEmail, EmployeeDirectoryLookupResponse employee) {
        String fallbackEmail = employee == null ? entity.getEmail() : employee.emailAddress();
        return staffProfileSupport.resolvePreferredText(requestedEmail, fallbackEmail);
    }
    private boolean shouldLookupMissingProfileFields(String requestedName, String requestedEmail) {
        return requestedName == null || requestedEmail == null;
    }

    private void ensureBatchDoesNotContainDuplicates(List<String> staffIds) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        LinkedHashSet<String> duplicates = new LinkedHashSet<>();
        for (String staffId : staffIds) {
            if (!seen.add(staffId)) {
                duplicates.add(staffId);
            }
        }
        if (!duplicates.isEmpty()) {
            throw new BadRequestException("Duplicate staff IDs in request: " + String.join(", ", duplicates));
        }
    }

    private void ensureStaffIdsAvailable(List<String> staffIds, Long excludedStaffId) {
        List<StaffEntity> existingStaff = staffMapper.selectList(Wrappers.<StaffEntity>lambdaQuery()
            .in(StaffEntity::getStaffId, staffIds)
            .ne(excludedStaffId != null, StaffEntity::getId, excludedStaffId));

        if (!existingStaff.isEmpty()) {
            String existingCodes = existingStaff.stream()
                .map(StaffEntity::getStaffId)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
            throw new BadRequestException("Staff ID already exists: " + existingCodes);
        }
    }

    private List<String> normalizeBatchStaffIds(List<String> staffIds) {
        if (staffIds == null || staffIds.isEmpty()) {
            throw new BadRequestException("At least one staff ID is required.");
        }
        List<String> normalized = staffIds.stream()
            .map(value -> normalizeRequiredText(value, "Staff ID is required."))
            .toList();
        if (normalized.isEmpty()) {
            throw new BadRequestException("At least one staff ID is required.");
        }
        return normalized;
    }

    private String normalizeRequiredText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException(message);
        }
        return value.trim();
    }

    private String normalizeOptionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String resolveStatus(String value) {
        return value == null || value.isBlank() ? "Active" : value.trim();
    }

    private TeamEntity resolveTeam(Map<Long, TeamEntity> teamMap, Long teamId) {
        if (teamId == null) {
            return null;
        }
        return teamMap.get(teamId);
    }

    private boolean shouldBypassTeamScopeFilter() {
        if (!authContextService.isLoggedIn()) {
            return false;
        }
        AuthenticatedAccount current = authContextService.requireLogin();
        return current.isAdmin() || current.isReadonly();
    }

    private void requireReadableExistingStaff(StaffEntity entity) {
        if (entity.getTeamId() != null) {
            authContextService.requireReadableTeam(entity.getTeamId());
            return;
        }
        authContextService.requireAdmin();
    }

    private void requireWritableExistingStaff(StaffEntity entity) {
        if (entity.getTeamId() != null) {
            authContextService.requireWritableTeam(entity.getTeamId());
            return;
        }
        authContextService.requireAdmin();
    }

    private void syncLinkedAccountStaffId(Long staffId, String previousStaffId, String currentStaffId) {
        if (staffId == null || java.util.Objects.equals(previousStaffId, currentStaffId)) {
            return;
        }
        WorkspaceAccountEntity account = workspaceAccountMapper.selectOne(Wrappers.<WorkspaceAccountEntity>lambdaQuery()
            .eq(WorkspaceAccountEntity::getStaffRecordId, staffId)
            .last("limit 1"));
        if (account == null) {
            return;
        }
        account.setStaffId(currentStaffId);
        workspaceAccountMapper.updateById(account);
    }
}
