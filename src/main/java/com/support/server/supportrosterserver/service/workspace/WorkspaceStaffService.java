package com.support.server.supportrosterserver.service.workspace;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceStaffDto;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceStaffBatchCreateRequest;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceStaffUpsertRequest;
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
import com.support.server.supportrosterserver.service.EmployeeDirectoryClient;
import com.support.server.supportrosterserver.service.auth.AuthContextService;
import com.support.server.supportrosterserver.service.auth.AuthTokenVersionService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WorkspaceStaffService {

    private static final Logger log = LogManager.getLogger(WorkspaceStaffService.class);

    private final AvatarUrlResolver avatarUrlResolver;
    private final EmployeeDirectoryClient employeeDirectoryClient;
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
            .orderByAsc(StaffEntity::getStaffCode)
            .orderByAsc(StaffEntity::getName);
        List<Long> readableTeamIds = authContextService.readableTeamIds();
        if (!readableTeamIds.isEmpty()) {
            query.in(StaffEntity::getTeamId, readableTeamIds);
        }
        if (keyword != null && !keyword.isBlank()) {
            query.and(wrapper -> wrapper
                .like(StaffEntity::getName, keyword)
                .or().like(StaffEntity::getEmail, keyword)
                .or().like(StaffEntity::getStaffCode, keyword)
                .or().like(StaffEntity::getRoleName, keyword)
                .or().like(StaffEntity::getRegion, keyword));
        }

        Map<Long, TeamEntity> teamMap = lookupService.teamMap();
        return staffMapper.selectList(query).stream()
            .map(staff -> toDto(staff, teamMap.get(staff.getTeamId())))
            .toList();
    }

    public WorkspaceStaffDto getStaff(Long id) {
        StaffEntity entity = staffMapper.selectById(id);
        if (entity == null) {
            throw new ResourceNotFoundException("Staff", "id", id);
        }
        authContextService.requireReadableTeam(entity.getTeamId());
        return toDto(entity, lookupService.teamMap().get(entity.getTeamId()));
    }

    @Transactional
    public WorkspaceStaffDto createStaff(WorkspaceStaffUpsertRequest request) {
        authContextService.requireWritableTeam(request.getTeamId());
        String staffCode = normalizeRequiredText(request.getStaffCode(), "Staff ID is required.");
        ensureStaffCodesAvailable(List.of(staffCode), null);
        EmployeeDirectoryLookupResponse employee = lookupEmployeeSafely(staffCode);
        StaffEntity entity = new StaffEntity();
        applyCreate(entity, staffCode, employee, request);
        staffMapper.insert(entity);
        WorkspaceStaffDto created = getStaff(entity.getId());
        workspaceOperationLogService.log(
            authContextService.currentActor("system"),
            "Create workspace staff",
            "workspace_staff",
            entity.getId(),
            "Staff ID=" + created.getStaffCode()
        );
        return created;
    }

    @Transactional
    public List<WorkspaceStaffDto> createStaffBatch(WorkspaceStaffBatchCreateRequest request) {
        authContextService.requireWritableTeam(request.getTeamId());
        lookupService.requireTeam(request.getTeamId());

        List<String> staffCodes = normalizeBatchStaffCodes(request.getStaffCodes());
        ensureBatchDoesNotContainDuplicates(staffCodes);
        ensureStaffCodesAvailable(staffCodes, null);

        List<WorkspaceStaffDto> createdStaff = new ArrayList<>();
        for (String staffCode : staffCodes) {
            EmployeeDirectoryLookupResponse employee = lookupEmployeeSafely(staffCode);
            StaffEntity entity = new StaffEntity();
            applyEmployeeLookup(entity, staffCode, employee, request);
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
        authContextService.requireWritableTeam(entity.getTeamId());
        authContextService.requireWritableTeam(request.getTeamId());
        ensureStaffCodesAvailable(List.of(normalizeRequiredText(request.getStaffCode(), "Staff ID is required.")), id);
        String previousStaffCode = entity.getStaffCode();
        apply(entity, request);
        staffMapper.updateById(entity);
        syncLinkedAccountStaffCode(entity.getId(), previousStaffCode, entity.getStaffCode());
        WorkspaceStaffDto updated = getStaff(id);
        workspaceOperationLogService.log(
            authContextService.currentActor("system"),
            "Update workspace staff",
            "workspace_staff",
            updated.getId(),
            "Staff ID=" + updated.getStaffCode()
        );
        return updated;
    }

    @Transactional
    public void deleteStaff(Long id) {
        StaffEntity entity = staffMapper.selectById(id);
        if (entity == null) {
            throw new ResourceNotFoundException("Staff", "id", id);
        }
        authContextService.requireWritableTeam(entity.getTeamId());
        String actor = authContextService.currentActor("system");
        WorkspaceAccountEntity linkedAccount = workspaceAccountMapper.selectOne(Wrappers.<WorkspaceAccountEntity>lambdaQuery()
            .eq(WorkspaceAccountEntity::getStaffId, id)
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
                ? "Staff ID=" + entity.getStaffCode()
                : "Staff ID=" + entity.getStaffCode() + "; removed linked workspace account"
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
                avatarUrlResolver.resolve(entity.getStaffCode()),
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
        TeamEntity team = entity.getTeamId() == null ? null : lookupService.teamMap().get(entity.getTeamId());
        return new com.support.server.supportrosterserver.dto.StaffDto(
            entity.getId(),
            entity.getName(),
            avatarUrlResolver.resolve(entity.getStaffCode()),
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
            entity.getStaffCode(),
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
            avatarUrlResolver.resolve(entity.getStaffCode()),
            entity.getNotes(),
            new ArrayList<>(tags.values())
        );
    }

    private void apply(StaffEntity entity, WorkspaceStaffUpsertRequest request) {
        lookupService.requireTeam(request.getTeamId());
        entity.setStaffCode(normalizeRequiredText(request.getStaffCode(), "Staff ID is required."));
        entity.setName(normalizeRequiredText(request.getName(), "Full name is required."));
        entity.setEmail(normalizeOptionalText(request.getEmail()));
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
            String staffCode,
            EmployeeDirectoryLookupResponse employee,
            WorkspaceStaffUpsertRequest request) {
        lookupService.requireTeam(request.getTeamId());
        entity.setStaffCode(staffCode);
        entity.setName(resolveCreateName(staffCode, request.getName(), employee));
        entity.setEmail(resolvePreferredText(request.getEmail(), employee == null ? null : employee.emailAddress()));
        entity.setPhone(normalizeOptionalText(request.getPhone()));
        entity.setSlack(normalizeOptionalText(request.getSlack()));
        entity.setRegion(resolvePreferredText(request.getRegion(), employee == null ? null : buildRegion(employee.city(), employee.country())));
        entity.setTimezone(lookupService.normalizeWorkspaceTimezone(normalizeOptionalText(request.getTimezone())));
        entity.setRoleName(resolvePreferredText(request.getRoleName(), employee == null ? null : employee.roleFromLDAP()));
        entity.setTeamId(request.getTeamId());
        entity.setRoleGroupId(null);
        entity.setStatus(resolveStatus(request.getStatus()));
        entity.setAvatar(request.getAvatar());
        entity.setNotes(normalizeOptionalText(request.getNotes()));
    }

    private void applyEmployeeLookup(
            StaffEntity entity,
            String staffCode,
            EmployeeDirectoryLookupResponse employee,
            WorkspaceStaffBatchCreateRequest request) {
        entity.setStaffCode(staffCode);
        entity.setName(resolveEmployeeName(staffCode, employee));
        entity.setEmail(employee == null ? null : normalizeOptionalText(employee.emailAddress()));
        entity.setPhone(null);
        entity.setSlack(null);
        entity.setRegion(employee == null ? null : buildRegion(employee.city(), employee.country()));
        entity.setTimezone(lookupService.normalizeWorkspaceTimezone(normalizeOptionalText(request.getTimezone())));
        entity.setRoleName(employee == null ? null : normalizeOptionalText(employee.roleFromLDAP()));
        entity.setTeamId(request.getTeamId());
        entity.setRoleGroupId(null);
        entity.setStatus(resolveStatus(request.getStatus()));
        entity.setAvatar(null);
        entity.setNotes(normalizeOptionalText(request.getNotes()));
    }

    private EmployeeDirectoryLookupResponse lookupEmployeeSafely(String staffCode) {
        try {
            return employeeDirectoryClient.getEmployee(staffCode);
        } catch (RuntimeException ex) {
            log.warn("Employee lookup failed for staff ID {}. Falling back to minimal staff profile.", staffCode, ex);
            return null;
        }
    }

    private String resolveEmployeeName(String staffCode, EmployeeDirectoryLookupResponse employee) {
        if (employee == null) {
            return staffCode;
        }
        String displayName = normalizeOptionalText(employee.displayName());
        return displayName == null ? staffCode : displayName;
    }

    private String resolveCreateName(String staffCode, String requestedName, EmployeeDirectoryLookupResponse employee) {
        String normalizedRequestedName = normalizeOptionalText(requestedName);
        if (normalizedRequestedName != null) {
            return normalizedRequestedName;
        }
        return resolveEmployeeName(staffCode, employee);
    }

    private String resolvePreferredText(String preferredValue, String fallbackValue) {
        String normalizedPreferredValue = normalizeOptionalText(preferredValue);
        if (normalizedPreferredValue != null) {
            return normalizedPreferredValue;
        }
        return normalizeOptionalText(fallbackValue);
    }

    private void ensureBatchDoesNotContainDuplicates(List<String> staffCodes) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        LinkedHashSet<String> duplicates = new LinkedHashSet<>();
        for (String staffCode : staffCodes) {
            if (!seen.add(staffCode)) {
                duplicates.add(staffCode);
            }
        }
        if (!duplicates.isEmpty()) {
            throw new BadRequestException("Duplicate staff IDs in request: " + String.join(", ", duplicates));
        }
    }

    private void ensureStaffCodesAvailable(List<String> staffCodes, Long excludedStaffId) {
        List<StaffEntity> existingStaff = staffMapper.selectList(Wrappers.<StaffEntity>lambdaQuery()
            .in(StaffEntity::getStaffCode, staffCodes)
            .ne(excludedStaffId != null, StaffEntity::getId, excludedStaffId));

        if (!existingStaff.isEmpty()) {
            String existingCodes = existingStaff.stream()
                .map(StaffEntity::getStaffCode)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
            throw new BadRequestException("Staff ID already exists: " + existingCodes);
        }
    }

    private List<String> normalizeBatchStaffCodes(List<String> staffCodes) {
        if (staffCodes == null || staffCodes.isEmpty()) {
            throw new BadRequestException("At least one staff ID is required.");
        }
        List<String> normalized = staffCodes.stream()
            .map(value -> normalizeRequiredText(value, "Staff ID is required."))
            .toList();
        if (normalized.isEmpty()) {
            throw new BadRequestException("At least one staff ID is required.");
        }
        return normalized;
    }

    private String buildRegion(String city, String country) {
        String normalizedCity = normalizeOptionalText(city);
        String normalizedCountry = normalizeOptionalText(country);
        if (normalizedCity == null) {
            return normalizedCountry;
        }
        if (normalizedCountry == null) {
            return normalizedCity;
        }
        return normalizedCity + ", " + normalizedCountry;
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

    private void syncLinkedAccountStaffCode(Long staffId, String previousStaffCode, String currentStaffCode) {
        if (staffId == null || java.util.Objects.equals(previousStaffCode, currentStaffCode)) {
            return;
        }
        WorkspaceAccountEntity account = workspaceAccountMapper.selectOne(Wrappers.<WorkspaceAccountEntity>lambdaQuery()
            .eq(WorkspaceAccountEntity::getStaffId, staffId)
            .last("limit 1"));
        if (account == null) {
            return;
        }
        account.setStaffCode(currentStaffCode);
        workspaceAccountMapper.updateById(account);
    }
}
