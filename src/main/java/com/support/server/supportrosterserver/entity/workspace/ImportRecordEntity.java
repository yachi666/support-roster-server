package com.support.server.supportrosterserver.entity.workspace;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.support.server.supportrosterserver.typehandler.JsonbStringTypeHandler;

import org.apache.ibatis.type.JdbcType;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName(value = "workspace_import_record", autoResultMap = true)
public class ImportRecordEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long batchId;

    private String sheetName;

    private Integer rowNumber;

    private String recordType;

    @TableField(value = "payload_json", typeHandler = JsonbStringTypeHandler.class, jdbcType = JdbcType.OTHER)
    private String payloadJson;

    private Boolean valid;
}