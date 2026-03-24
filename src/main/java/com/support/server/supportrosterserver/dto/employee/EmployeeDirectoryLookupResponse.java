package com.support.server.supportrosterserver.dto.employee;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EmployeeDirectoryLookupResponse(
    String city,
    String country,
    String displayName,
    String emailAddress,
    String roleFromLDAP
) {
}
