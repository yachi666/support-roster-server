package com.support.server.supportrosterserver.dto.workspace;

import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceAccountDto {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long staffRecordId;
    private String staffId;
    private String staffName;
    private String roleCode;
    private String accountStatus;
    private String authSource;
    private String notes;
    private LocalDateTime lastLoginAt;
    private List<String> editableTeamIds;
    private List<WorkspaceAccountScopeDto> editableTeams;
}
