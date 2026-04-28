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

    public String resolve(String staffId) {
        if (!StringUtils.hasText(staffId)) {
            return null;
        }

        String normalizedStaffId = staffId.trim();
        String segment = normalizedStaffId.length() <= 4 ? normalizedStaffId : normalizedStaffId.substring(0, 4);
        return baseUrl + "/" + segment + "/" + normalizedStaffId + ".jpg";
    }

    private String trimTrailingSlash(String value) {
        String trimmed = value.trim();
        if (trimmed.endsWith("/")) {
            return trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}