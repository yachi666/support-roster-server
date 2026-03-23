package com.support.server.supportrosterserver.dto.workspace;

import java.time.LocalTime;
import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class WorkspaceShiftDefinitionUpsertRequest {
    @NotEmpty
    private List<@NotNull Long> teamIds;

    @NotBlank
    private String code;

    @NotBlank
    private String meaning;

    @NotNull
    private LocalTime startTime;

    @NotNull
    @Min(1)
    @Max(1440)
    private Integer durationMinutes;

    @NotBlank
    private String timezone;

    @NotNull
    private Boolean primaryShift;

    @NotNull
    private Boolean visible;

    private String colorHex;

    private String remark;
}
