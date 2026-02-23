package com.support.server.supportrosterserver.entity;

import java.time.LocalTime;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class RosterEntry {
    private String roleGroup;
    private String code;
    private String meaning;
    private LocalTime startTime;
    private LocalTime endTime;
    private String timezone;
    private Long staffId;
    private Boolean showOnRosterPage;
    private String remark;
}
