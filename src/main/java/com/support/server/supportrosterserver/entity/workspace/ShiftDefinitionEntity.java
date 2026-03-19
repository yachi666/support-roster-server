package com.support.server.supportrosterserver.entity.workspace;

import java.time.LocalTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("workspace_shift_definition")
public class ShiftDefinitionEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long teamId;

    private Long roleGroupId;

    private String code;

    private String meaning;

    private LocalTime startTime;

    private LocalTime endTime;

    private String timezone;

    private Boolean primaryShift;

    private Boolean visible;

    private String colorHex;

    private String remark;

    @TableLogic
    private Integer deleted;
}