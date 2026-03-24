package com.support.server.supportrosterserver.service.auth;

import org.springframework.stereotype.Service;

import com.support.server.supportrosterserver.entity.auth.WorkspaceAccountEntity;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.stp.SaLoginModel;
import cn.dev33.satoken.stp.StpUtil;

@Service
public class AuthTokenVersionService {

    public static final long INITIAL_TOKEN_VERSION = 1L;
    static final String TOKEN_VERSION_EXTRA_KEY = "tokenVersion";

    public long currentTokenVersion(WorkspaceAccountEntity account) {
        if (account == null || account.getTokenVersion() == null || account.getTokenVersion() < INITIAL_TOKEN_VERSION) {
            return INITIAL_TOKEN_VERSION;
        }
        return account.getTokenVersion();
    }

    public long bumpTokenVersion(WorkspaceAccountEntity account) {
        long nextVersion = currentTokenVersion(account) + 1;
        account.setTokenVersion(nextVersion);
        return nextVersion;
    }

    public SaLoginModel createLoginModel(WorkspaceAccountEntity account) {
        return SaLoginModel.create()
            .setExtra(TOKEN_VERSION_EXTRA_KEY, currentTokenVersion(account));
    }

    public void validateCurrentTokenVersion(WorkspaceAccountEntity account) {
        Object rawTokenVersion = StpUtil.getExtra(TOKEN_VERSION_EXTRA_KEY);
        try {
            if (!isCurrentTokenVersion(account, rawTokenVersion)) {
                expireCurrentLogin(NotLoginException.TOKEN_TIMEOUT, "Login state has expired.");
            }
        } catch (IllegalArgumentException ex) {
            expireCurrentLogin(NotLoginException.INVALID_TOKEN, ex.getMessage());
        }
    }

    boolean isCurrentTokenVersion(WorkspaceAccountEntity account, Object rawTokenVersion) {
        return parseTokenVersion(rawTokenVersion) == currentTokenVersion(account);
    }

    long parseTokenVersion(Object rawTokenVersion) {
        if (rawTokenVersion instanceof Number number) {
            return number.longValue();
        }
        if (rawTokenVersion instanceof String text) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Token payload is invalid.", ex);
            }
        }
        throw new IllegalArgumentException("Token payload is invalid.");
    }

    private void expireCurrentLogin(String type, String message) {
        String tokenValue = StpUtil.getTokenValue();
        StpUtil.logout();
        throw NotLoginException.newInstance(StpUtil.getLoginType(), type, message, tokenValue);
    }
}
