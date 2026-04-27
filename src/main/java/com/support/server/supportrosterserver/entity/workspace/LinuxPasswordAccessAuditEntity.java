package com.support.server.supportrosterserver.entity.workspace;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("workspace_linux_password_access_audit")
public class LinuxPasswordAccessAuditEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long accountId;

    private Long staffRecordId;

    private String staffId;

    private String staffName;

    private Long serverId;

    private Long credentialId;

    private String action;

    private String result;

    private String clientIp;

    private String userAgent;
}
