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
public class WorkspaceStaffDto {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String staffCode;
    private String name;
    private String email;
    private String phone;
    private String slack;
    private String region;
    private String timezone;
    private String roleName;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long teamId;
    private String teamCode;
    private String teamName;
    private String status;
    private String avatar;
    private String notes;
    private List<String> rosterTags;
}