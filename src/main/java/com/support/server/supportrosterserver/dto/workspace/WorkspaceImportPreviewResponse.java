package com.support.server.supportrosterserver.dto.workspace;

import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceImportPreviewResponse {
    private Integer year;
    private Integer month;
    private Integer totalRecords;
    private Integer validRecords;
    private Integer invalidRecords;
    private List<WorkspaceImportPreviewGroupDto> groups;
    private List<String> shiftCodeOptions;
    private Map<Long, List<String>> shiftCodeOptionsByTeam;
    private Map<String, String> shiftCodeColorMap;
    private Map<Long, Map<String, WorkspaceRosterShiftDetailDto>> shiftDetailsByTeam;
    private List<WorkspaceValidationIssueDto> issues;
    private List<String> newStaffIds;
    private List<String> newTeamNames;
    private String validationWarning;
}
