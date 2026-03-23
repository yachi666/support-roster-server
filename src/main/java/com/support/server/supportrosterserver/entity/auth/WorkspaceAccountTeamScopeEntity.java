package com.support.server.supportrosterserver.entity.auth;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.support.server.supportrosterserver.entity.workspace.BaseEntity;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("workspace_account_team_scope")
public class WorkspaceAccountTeamScopeEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long accountId;

    private Long teamId;
}
