package com.support.server.supportrosterserver.dto.contactinformation;

import java.util.List;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ContactInformationCreateRequest(
    @NotBlank String name,
    @Email String email,
    String xMatter,
    String gsd,
    String eim,
    List<String> roles,
    List<String> staffIds,
    List<ContactInformationLinkDto> links
) {
    public ContactInformationCreateRequest {
        email = normalizeOptional(email);
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
