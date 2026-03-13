package com.support.server.supportrosterserver.dto.workspace;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceImportApplyResponse {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long batchId;
    private Integer year;
    private Integer month;
    private String status;
    private Integer appliedRecords;
}