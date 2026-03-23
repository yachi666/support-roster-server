package com.support.server.supportrosterserver.dto.workspace;

import java.util.List;

import lombok.Data;

@Data
public class WorkspaceAccountUpsertRequest {
    private Long staffRecordId;
    private String roleCode;
    private List<Long> editableTeamIds;
    private String notes;
}
