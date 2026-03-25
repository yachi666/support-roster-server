package com.support.server.supportrosterserver.dto.workspace;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

@Data
public class WorkspaceImportPreviewSaveRequest {
    @Min(2000)
    private Integer year;

    @Min(1)
    @Max(12)
    private Integer month;

    @NotEmpty
    private List<WorkspaceImportPreviewSaveRowRequest> rows;
}
