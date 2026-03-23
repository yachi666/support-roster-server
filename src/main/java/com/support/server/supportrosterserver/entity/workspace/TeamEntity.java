package com.support.server.supportrosterserver.entity.workspace;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("workspace_team")
public class TeamEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String name;

    private String color;

    private Integer displayOrder;

    private Boolean visible;

    private String description;

    @TableLogic
    private Integer deleted;
}
