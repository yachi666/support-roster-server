package com.support.server.supportrosterserver.auth;

public enum AccountStatus {
    PENDING_ACTIVATION,
    ACTIVE,
    DISABLED;

    public String getCode() {
        return name();
    }
}
