package com.support.server.supportrosterserver.entity.workspace;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("workspace_staff")
public class StaffEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String staffCode;

    private String name;

    private String email;

    private String phone;

    private String slack;

    private String region;

    private String timezone;

    private String roleName;

    private Long roleGroupId;

    private String status;

    private String avatar;

    private String notes;

    @TableLogic
    private Integer deleted;
}