package com.support.server.supportrosterserver.config;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.support.server.supportrosterserver.service.workspace.WorkspaceAccessPolicyService;

import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class WorkspaceAccessInterceptor implements HandlerInterceptor {

    private final WorkspaceAccessPolicyService workspaceAccessPolicyService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (workspaceAccessPolicyService.isAnonymousReadAllowed(request.getMethod(), request.getRequestURI())) {
            return true;
        }
        StpUtil.checkLogin();
        return true;
    }
}
