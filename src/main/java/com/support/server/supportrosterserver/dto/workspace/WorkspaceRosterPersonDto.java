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
public class WorkspaceRosterPersonDto {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long staffId;
    private String staffName;
    private String avatar;
    private String roleName;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long roleGroupId;
    private Map<Integer, String> schedule;
}