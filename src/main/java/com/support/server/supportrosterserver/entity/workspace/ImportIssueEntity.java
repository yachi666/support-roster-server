package com.support.server.supportrosterserver.entity.workspace;

import java.time.LocalDate;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("workspace_import_issue")
public class ImportIssueEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long batchId;

    private Long importRecordId;

    private String severity;

    private String issueType;

    private String description;

    private String teamName;

    private String roleGroupCode;

    private String staffName;

    private LocalDate issueDate;

    private Boolean resolved;
}