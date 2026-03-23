package com.support.server.supportrosterserver.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthCurrentTeamDto {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String name;
    private String color;
}
