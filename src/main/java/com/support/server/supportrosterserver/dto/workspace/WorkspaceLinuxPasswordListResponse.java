package com.support.server.supportrosterserver.dto.workspace;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceLinuxPasswordListResponse {
    private List<WorkspaceLinuxPasswordDto> items;
    private List<String> businessUnits;
}
