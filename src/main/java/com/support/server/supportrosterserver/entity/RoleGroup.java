package com.support.server.supportrosterserver.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoleGroup {
    private String id;
    private String name;
    private String category;
    private String region;
}
