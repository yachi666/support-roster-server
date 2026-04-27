package com.support.server.supportrosterserver.dto.workspace;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class WorkspaceStaffBatchCreateRequest {

    @NotEmpty(message = "At least one staff ID is required.")
    private List<@NotBlank(message = "Staff ID is required.") String> staffIds;

    @NotNull(message = "Team is required.")
    private Long teamId;

    private String timezone;

    private String status;

    private String notes;
}
