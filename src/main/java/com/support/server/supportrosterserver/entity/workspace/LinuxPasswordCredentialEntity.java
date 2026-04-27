package com.support.server.supportrosterserver.entity.workspace;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("workspace_linux_password_credential")
public class LinuxPasswordCredentialEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long serverId;

    private String username;

    private String passwordCiphertext;

    private String passwordIv;

    private String keyVersion;

    private String notes;

    @TableLogic
    private Integer deleted;
}
