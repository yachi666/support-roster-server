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
@TableName("support_team_contact")
public class SupportTeamContactEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String teamName;

    private String teamEmail;

    private String xmatterGroup;

    private String gsdGroup;

    private String eimId;

    private String otherInfo;

    private Long createdByAccountId;

    private Long updatedByAccountId;

    @TableLogic
    private Integer deleted;
}
