package com.support.server.supportrosterserver.entity.workspace;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("workspace_linux_password_server")
public class LinuxPasswordServerEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String hostname;

    private String ip;

    /**
     * Legacy columns retained for migration from the original one-account model.
     * New writes store credentials in workspace_linux_password_credential.
     */
    private String username;

    private String password;

    private String status;

    @TableLogic
    private Integer deleted;
}
