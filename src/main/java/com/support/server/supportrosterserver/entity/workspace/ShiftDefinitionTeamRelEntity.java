package com.support.server.supportrosterserver.entity.workspace;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("workspace_shift_definition_team_rel")
public class ShiftDefinitionTeamRelEntity extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long shiftDefinitionId;

    private Long teamId;

    private Integer displayOrder;
}
