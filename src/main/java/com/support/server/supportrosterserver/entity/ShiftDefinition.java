package com.support.server.supportrosterserver.entity;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ShiftDefinition {
    private String roleGroup;
    private String code;
    private String meaning;
    private String startTime;
    private String endTime;
    private String timezone;
    private Boolean showOnRosterPage;
    private String remark;
}
