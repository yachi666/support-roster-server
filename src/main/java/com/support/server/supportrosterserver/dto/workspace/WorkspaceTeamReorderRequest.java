package com.support.server.supportrosterserver.dto.workspace;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class WorkspaceTeamReorderRequest {

    @NotEmpty
    private List<@NotNull Long> teamIds;
}