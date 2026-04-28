package com.support.server.supportrosterserver.dto.workspace;

import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class WorkspaceImportPreviewSaveRowRequest {
    @NotBlank
    private String staffId;

    @NotBlank
    private String teamName;

    @NotNull
    private Map<Integer, String> schedule;
}
