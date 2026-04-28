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
public class WorkspaceLinuxPasswordDto {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String hostname;
    private String ip;
    private List<WorkspaceLinuxPasswordCredentialDto> credentials;
    private List<String> businessUnits;
    private String status;
}
