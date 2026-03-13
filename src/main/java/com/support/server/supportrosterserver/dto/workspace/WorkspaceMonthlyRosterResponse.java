package com.support.server.supportrosterserver.dto.workspace;

import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceMonthlyRosterResponse {
    private Integer year;
    private Integer month;
    private List<WorkspaceRosterGroupDto> groups;
    private List<String> shiftCodeOptions;
    private Map<Long, List<String>> shiftCodeOptionsByRoleGroup;
    private Map<String, String> shiftCodeColorMap;
    private String validationWarning;
}