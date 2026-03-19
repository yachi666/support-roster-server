package com.support.server.supportrosterserver.dto.workspace;

import java.time.LocalTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class WorkspaceShiftDefinitionUpsertRequest {
    @NotNull
    private Long teamId;

    @NotBlank
    private String code;

    @NotBlank
    private String meaning;

    @NotNull
    private LocalTime startTime;

    @NotNull
    private LocalTime endTime;

    @NotBlank
    private String timezone;

    @NotNull
    private Boolean primaryShift;

    @NotNull
    private Boolean visible;

    private String colorHex;

    private String remark;
}