package com.support.server.supportrosterserver.dto.workspace;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkspacePageAccessPolicyDto {
    private String pageCode;
    private Boolean authRequired;
    private Boolean configurable;
}
