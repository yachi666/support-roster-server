package com.support.server.supportrosterserver.dto.auth;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthCurrentUserDto {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long accountId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long staffRecordId;
    private String staffId;
    private String staffName;
    private String role;
    private String status;
    private String authSource;
    private List<String> permissions;
    private List<String> editableTeamIds;
    private List<AuthCurrentTeamDto> editableTeams;
}
