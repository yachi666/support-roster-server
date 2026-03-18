package com.support.server.supportrosterserver.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AvatarUrlResolver {

    private final String baseUrl;

    public AvatarUrlResolver(@Value("${support.avatar.base-url}") String baseUrl) {
        if (!StringUtils.hasText(baseUrl)) {
            throw new IllegalArgumentException("support.avatar.base-url must not be blank");
        }
        this.baseUrl = trimTrailingSlash(baseUrl);
    }

    public String resolve(String staffCode) {
        if (!StringUtils.hasText(staffCode)) {
            return null;
        }

        String normalizedStaffCode = staffCode.trim();
        String segment = normalizedStaffCode.length() <= 4 ? normalizedStaffCode : normalizedStaffCode.substring(0, 4);
        return baseUrl + "/" + segment + "/" + normalizedStaffCode + ".jpg";
    }

    private String trimTrailingSlash(String value) {
        String trimmed = value.trim();
        if (trimmed.endsWith("/")) {
            return trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}