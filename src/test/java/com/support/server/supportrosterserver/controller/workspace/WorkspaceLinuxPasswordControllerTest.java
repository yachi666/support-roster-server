package com.support.server.supportrosterserver.controller.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.support.server.supportrosterserver.config.TrustedProxyProperties;
import com.support.server.supportrosterserver.service.workspace.WorkspaceLinuxPasswordService;

import jakarta.servlet.http.HttpServletRequest;

class WorkspaceLinuxPasswordControllerTest {

    private WorkspaceLinuxPasswordController buildController(List<String> trustedProxyIps) {
        WorkspaceLinuxPasswordService service = mock(WorkspaceLinuxPasswordService.class);
        TrustedProxyProperties props = new TrustedProxyProperties();
        props.setIps(trustedProxyIps);
        return new WorkspaceLinuxPasswordController(service, props);
    }

    @Test
    void shouldUseXForwardedForWhenRemoteAddrIsTrustedProxy() {
        WorkspaceLinuxPasswordController controller = buildController(List.of("127.0.0.1", "10.0.0.1"));
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("10.0.0.1");
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.5, 10.0.0.1");

        String ip = controller.resolveClientIp(request);

        assertEquals("203.0.113.5", ip);
    }

    @Test
    void shouldIgnoreXForwardedForWhenRemoteAddrIsNotTrustedProxy() {
        WorkspaceLinuxPasswordController controller = buildController(List.of("127.0.0.1"));
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("203.0.113.99");
        when(request.getHeader("X-Forwarded-For")).thenReturn("192.168.1.1");

        String ip = controller.resolveClientIp(request);

        // Untrusted remote addr: X-Forwarded-For ignored, use actual remoteAddr
        assertEquals("203.0.113.99", ip);
    }

    @Test
    void shouldUseRemoteAddrWhenXForwardedForAbsentEvenForTrustedProxy() {
        WorkspaceLinuxPasswordController controller = buildController(List.of("127.0.0.1"));
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);

        String ip = controller.resolveClientIp(request);

        assertEquals("127.0.0.1", ip);
    }

    @Test
    void shouldDefaultToLocalhostTrustedWhenUsingDefaultConfig() {
        // Default config only trusts 127.0.0.1 and ::1
        WorkspaceLinuxPasswordController controller = buildController(List.of("127.0.0.1", "::1"));
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("::1");
        when(request.getHeader("X-Forwarded-For")).thenReturn("10.20.30.40");

        String ip = controller.resolveClientIp(request);

        assertEquals("10.20.30.40", ip);
    }
}
