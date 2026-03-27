package com.support.server.supportrosterserver.dto.workspace;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceValidationRemediationPreviewResponse {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long issueId;
    private String actionKey;
    private String title;
    private String summary;
    private String warning;
    private int recordCount;
    @JsonSerialize(contentUsing = ToStringSerializer.class)
    private List<Long> recordIds;
}
