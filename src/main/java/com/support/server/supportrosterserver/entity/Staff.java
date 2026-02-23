package com.support.server.supportrosterserver.entity;

import java.util.List;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class Staff {
    private Long id;
    private String name;
    private String avatar;
    private String email;
    private String phone;
    private String slack;
    private List<String> roleGroups;

    public Staff(Long id, String name) {
        this.id = id;
        this.name = name;
    }
}
