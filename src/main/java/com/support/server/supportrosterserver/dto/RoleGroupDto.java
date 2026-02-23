package com.support.server.supportrosterserver.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoleGroupDto {
    private String id;
    private String name;
    private String category;
    private String region;
}
