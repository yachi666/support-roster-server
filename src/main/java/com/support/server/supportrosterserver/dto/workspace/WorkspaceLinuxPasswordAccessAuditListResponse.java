package com.support.server.supportrosterserver.dto.workspace;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceLinuxPasswordAccessAuditListResponse {
    private List<WorkspaceLinuxPasswordAccessAuditDto> items;
    private long page;
    private long pageSize;
    private long total;
}
