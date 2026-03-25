package com.support.server.supportrosterserver.dto.workspace;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceImportPreviewGroupDto {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long teamId;

    private String teamName;
    private String color;
    private Boolean newTeam;
    private List<WorkspaceImportPreviewPersonDto> staff;
}
