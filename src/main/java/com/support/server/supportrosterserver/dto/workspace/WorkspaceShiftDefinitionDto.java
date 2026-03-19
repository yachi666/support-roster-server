package com.support.server.supportrosterserver.dto.workspace;

import java.time.LocalTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceShiftDefinitionDto {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long teamId;
    private String teamCode;
    private String teamName;
    private String code;
    private String meaning;
    private LocalTime startTime;
    private LocalTime endTime;
    private String timezone;
    private Boolean primaryShift;
    private Boolean visible;
    private String colorHex;
    private String remark;
}