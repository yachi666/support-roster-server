package com.support.server.supportrosterserver.service.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.support.server.supportrosterserver.auth.AccountRole;
import com.support.server.supportrosterserver.auth.AccountStatus;
import com.support.server.supportrosterserver.entity.auth.WorkspaceAccountEntity;
import com.support.server.supportrosterserver.entity.auth.WorkspaceAccountTeamScopeEntity;
import com.support.server.supportrosterserver.entity.workspace.StaffEntity;
import com.support.server.supportrosterserver.mapper.StaffMapper;
import com.support.server.supportrosterserver.mapper.WorkspaceAccountMapper;
import com.support.server.supportrosterserver.mapper.WorkspaceAccountTeamScopeMapper;
import com.support.server.supportrosterserver.service.workspace.WorkspaceOperationLogService;

@Service
public class WorkspaceAdminBootstrapService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceAdminBootstrapService.class);
    private static final String AUTH_SOURCE_LOCAL_PASSWORD = "LOCAL_PASSWORD";

    private final String bootstrapAdminStaffId;
    private final StaffMapper staffMapper;
    private final WorkspaceAccountMapper workspaceAccountMapper;
    private final WorkspaceAccountTeamScopeMapper workspaceAccountTeamScopeMapper;
    private final WorkspaceOperationLogService workspaceOperationLogService;
    private final AuthTokenVersionService authTokenVersionService;

    public WorkspaceAdminBootstrapService(
        @Value("${support.auth.bootstrap-admin-staff-id:}") String bootstrapAdminStaffId,
        StaffMapper staffMapper,
        WorkspaceAccountMapper workspaceAccountMapper,
        WorkspaceAccountTeamScopeMapper workspaceAccountTeamScopeMapper,
        WorkspaceOperationLogService workspaceOperationLogService,
        AuthTokenVersionService authTokenVersionService
    ) {
        this.bootstrapAdminStaffId = bootstrapAdminStaffId == null ? "" : bootstrapAdminStaffId.trim();
        this.staffMapper = staffMapper;
        this.workspaceAccountMapper = workspaceAccountMapper;
        this.workspaceAccountTeamScopeMapper = workspaceAccountTeamScopeMapper;
        this.workspaceOperationLogService = workspaceOperationLogService;
        this.authTokenVersionService = authTokenVersionService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (bootstrapAdminStaffId.isBlank()) {
            return;
        }

        StaffEntity staff = staffMapper.selectOne(Wrappers.<StaffEntity>lambdaQuery()
            .eq(StaffEntity::getStaffId, bootstrapAdminStaffId)
            .last("limit 1"));
        if (staff == null) {
            throw new IllegalStateException(
                "Configured support.auth.bootstrap-admin-staff-id does not match any workspace staff record: "
                    + bootstrapAdminStaffId
            );
        }

        WorkspaceAccountEntity account = workspaceAccountMapper.selectOne(Wrappers.<WorkspaceAccountEntity>lambdaQuery()
            .eq(WorkspaceAccountEntity::getStaffRecordId, staff.getId())
            .last("limit 1"));
        if (account == null) {
            createBootstrapAdmin(staff);
            return;
        }

        promoteBootstrapAdminIfNeeded(staff, account);
    }

    private void createBootstrapAdmin(StaffEntity staff) {
        WorkspaceAccountEntity account = new WorkspaceAccountEntity();
        account.setStaffRecordId(staff.getId());
        account.setStaffId(staff.getStaffId());
        account.setRoleCode(AccountRole.ADMIN.getCode());
        account.setAccountStatus(AccountStatus.PENDING_ACTIVATION.getCode());
        account.setPasswordHash(null);
        account.setPasswordSetAt(null);
        account.setAuthSource(AUTH_SOURCE_LOCAL_PASSWORD);
        account.setExternalSubject(null);
        account.setNotes("Bootstrap admin account from support.auth.bootstrap-admin-staff-id");
        account.setLastLoginAt(null);
        account.setTokenVersion(AuthTokenVersionService.INITIAL_TOKEN_VERSION);
        workspaceAccountMapper.insert(account);
        workspaceOperationLogService.log(
            "system",
            "Bootstrap workspace admin",
            "workspace_account",
            account.getId(),
            "Created bootstrap admin for staff_id=" + staff.getStaffId()
        );
        log.info("Created bootstrap workspace admin account for staff_id={}", staff.getStaffId());
    }

    private void promoteBootstrapAdminIfNeeded(StaffEntity staff, WorkspaceAccountEntity account) {
        boolean changed = false;

        if (!AccountRole.ADMIN.getCode().equalsIgnoreCase(account.getRoleCode())) {
            account.setRoleCode(AccountRole.ADMIN.getCode());
            changed = true;
        }
        if (account.getAuthSource() == null || account.getAuthSource().isBlank()) {
            account.setAuthSource(AUTH_SOURCE_LOCAL_PASSWORD);
            changed = true;
        }

        String expectedStatus = account.getPasswordHash() == null || account.getPasswordHash().isBlank()
            ? AccountStatus.PENDING_ACTIVATION.getCode()
            : AccountStatus.ACTIVE.getCode();
        if (!expectedStatus.equalsIgnoreCase(account.getAccountStatus())) {
            account.setAccountStatus(expectedStatus);
            changed = true;
        }

        if (changed) {
            authTokenVersionService.bumpTokenVersion(account);
            workspaceAccountMapper.updateById(account);
            workspaceOperationLogService.log(
                "system",
                "Bootstrap workspace admin",
                "workspace_account",
                account.getId(),
                "Elevated bootstrap admin for staff_id=" + staff.getStaffId()
            );
            log.info("Updated workspace account to bootstrap admin for staff_id={}", staff.getStaffId());
        }

        workspaceAccountTeamScopeMapper.delete(Wrappers.<WorkspaceAccountTeamScopeEntity>lambdaQuery()
            .eq(WorkspaceAccountTeamScopeEntity::getAccountId, account.getId()));
    }
}
