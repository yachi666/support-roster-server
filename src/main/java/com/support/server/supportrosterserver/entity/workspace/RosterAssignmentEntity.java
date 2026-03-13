package com.support.server.supportrosterserver.entity.workspace;

import java.time.LocalDate;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("workspace_roster_assignment")
public class RosterAssignmentEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long staffId;

    private Long roleGroupId;

    private Long teamId;

    private Long shiftDefinitionId;

    private LocalDate assignmentDate;

    private String shiftCode;

    private String sourceType;

    private String notes;

    @TableLogic
    private Integer deleted;
}