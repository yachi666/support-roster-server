package com.support.server.supportrosterserver.auth;

import com.support.server.supportrosterserver.exception.BadRequestException;

public enum AccountRole {
    ADMIN("admin"),
    EDITOR("editor"),
    READONLY("readonly");

    private final String code;

    AccountRole(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static AccountRole fromCode(String code) {
        for (AccountRole role : values()) {
            if (role.code.equalsIgnoreCase(code)) {
                return role;
            }
        }
        throw new BadRequestException("Unsupported role code: " + code);
    }
}
