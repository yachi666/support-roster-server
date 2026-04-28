package com.support.server.supportrosterserver.entity.auth;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.support.server.supportrosterserver.entity.workspace.BaseEntity;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("workspace_account")
public class WorkspaceAccountEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long staffRecordId;

    private String staffId;

    private String roleCode;

    private String accountStatus;

    private String passwordHash;

    private LocalDateTime passwordSetAt;

    private String authSource;

    private String externalSubject;

    private String notes;

    private LocalDateTime lastLoginAt;

    private Long tokenVersion;

    @TableLogic
    private Integer deleted;
}
