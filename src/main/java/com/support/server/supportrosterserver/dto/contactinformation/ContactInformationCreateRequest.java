package com.support.server.supportrosterserver.dto.contactinformation;

import java.util.List;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

public record ContactInformationCreateRequest(
    @NotBlank String name,
    @NotBlank @Email String email,
    String xMatter,
    String gsd,
    String eim,
    @NotEmpty List<String> roles,
    @NotEmpty List<String> staffIds,
    List<ContactInformationLinkDto> links
) {
}
