package com.support.server.supportrosterserver.dto.workspace;

import java.time.LocalTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceRosterShiftDetailDto {
    private String code;
    private String meaning;
    private LocalTime startTime;
    private LocalTime endTime;
    private String timezone;
    private Boolean primaryShift;
    private String colorHex;
    private Boolean overnight;
}