package com.support.server.supportrosterserver.dto.workspace;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceImportSaveResponse {
    private Integer year;
    private Integer month;
    private Integer appliedStaffCount;
    private Integer createdStaffCount;
    private Integer createdTeamCount;
}
