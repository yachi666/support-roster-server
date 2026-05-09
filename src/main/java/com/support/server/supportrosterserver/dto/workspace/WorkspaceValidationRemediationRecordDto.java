package com.support.server.supportrosterserver.dto.workspace;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceValidationRemediationRecordDto {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long recordId;
    private String title;
    private String subtitle;
    private String description;
}
