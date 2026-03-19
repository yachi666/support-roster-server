package com.support.server.supportrosterserver.dto.workspace;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class WorkspaceStaffUpsertRequest {
    @NotBlank
    private String staffCode;

    @NotBlank
    private String name;

    private String email;

    private String phone;

    private String slack;

    private String region;

    private String timezone;

    private String roleName;

    @NotNull
    private Long teamId;

    private String status;

    private String avatar;

    private String notes;
}