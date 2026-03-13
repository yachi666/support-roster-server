package com.support.server.supportrosterserver.dto.workspace;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class WorkspaceRosterCellUpdateRequest {
    @NotNull
    private Long staffId;

    @NotNull
    @Min(1)
    @Max(31)
    private Integer day;

    private String shiftCode;
}