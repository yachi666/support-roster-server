package com.support.server.supportrosterserver.dto.workspace;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceImportPreviewPersonDto {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long previewStaffId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long staffRecordId;

    private String staffId;
    private String staffName;
    private String avatar;
    private String roleName;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long teamId;

    private String teamName;
    private Map<Integer, String> schedule;
    private Boolean newStaff;
}
