package com.support.server.supportrosterserver.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StaffDto {
    private Long id;
    private String name;
    private String avatar;
    private String email;
    private String phone;
    private String slack;
    private List<String> roleGroups;
}
