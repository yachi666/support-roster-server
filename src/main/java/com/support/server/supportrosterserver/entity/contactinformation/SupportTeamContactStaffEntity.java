package com.support.server.supportrosterserver.entity.contactinformation;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.support.server.supportrosterserver.entity.workspace.BaseEntity;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("support_team_contact_staff")
public class SupportTeamContactStaffEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long contactId;

    private String staffId;

    @TableLogic
    private Integer deleted;
}
