package com.support.server.supportrosterserver.dto.workspace;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceAccessPolicyUpdateRequest {

    @Valid
    @NotEmpty(message = "At least one workspace page policy is required.")
    private List<WorkspacePageAccessPolicyDto> pages;
}
