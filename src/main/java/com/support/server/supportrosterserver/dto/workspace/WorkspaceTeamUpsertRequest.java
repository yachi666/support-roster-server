package com.support.server.supportrosterserver.dto.workspace;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class WorkspaceTeamUpsertRequest {
    @NotBlank
    private String teamCode;

    @NotBlank
    private String name;

    @NotBlank
    private String color;

    @NotNull
    private Integer displayOrder;

    @NotNull
    private Boolean visible;

    private String description;

    @NotNull
    private List<Long> roleGroupIds;
}