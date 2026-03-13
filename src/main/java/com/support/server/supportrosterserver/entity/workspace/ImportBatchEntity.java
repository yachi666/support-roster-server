package com.support.server.supportrosterserver.entity.workspace;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("workspace_import_batch")
public class ImportBatchEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Integer rosterYear;

    private Integer rosterMonth;

    private String fileName;

    private String status;

    private Integer totalRecords;

    private Integer validRecords;

    private Integer invalidRecords;

    private String operatorName;

    private LocalDateTime appliedTime;
}