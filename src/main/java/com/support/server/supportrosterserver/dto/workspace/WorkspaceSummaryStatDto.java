package com.support.server.supportrosterserver.dto.workspace;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceSummaryStatDto {
    private String label;
    private String value;
    private String trend;
    private String status;
    private Integer progress;
}