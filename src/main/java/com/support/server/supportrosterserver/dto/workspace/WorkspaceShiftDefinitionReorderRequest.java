package com.support.server.supportrosterserver.dto.workspace;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceShiftDefinitionReorderRequest {

    @NotNull
    private Long teamId;

    @NotEmpty
    private List<@NotNull Long> shiftDefinitionIds;
}
