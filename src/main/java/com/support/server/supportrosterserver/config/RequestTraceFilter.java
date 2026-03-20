package com.support.server.supportrosterserver.config;

import java.io.IOException;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class RequestTraceFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final Logger log = LogManager.getLogger(RequestTraceFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String traceId = resolveTraceId(request.getHeader(TRACE_ID_HEADER));
        long startNanos = System.nanoTime();

        ThreadContext.put("traceId", traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
            logRequest(request, response, durationMs);
            ThreadContext.remove("traceId");
        }
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

    private void logRequest(HttpServletRequest request, HttpServletResponse response, long durationMs) {
        String method = request.getMethod();
        String requestUri = request.getRequestURI();
        String queryString = request.getQueryString();
        String path = queryString == null || queryString.isBlank() ? requestUri : requestUri + "?" + queryString;
        int status = response.getStatus();

        if (requestUri.startsWith("/actuator/health")) {
            log.debug("{} {} -> {} ({} ms)", method, path, status, durationMs);
            return;
        }

        if (status >= 500) {
            log.error("{} {} -> {} ({} ms)", method, path, status, durationMs);
            return;
        }

        if (status >= 400) {
            log.warn("{} {} -> {} ({} ms)", method, path, status, durationMs);
            return;
        }

        log.info("{} {} -> {} ({} ms)", method, path, status, durationMs);
    }

    private String resolveTraceId(String incomingTraceId) {
        if (incomingTraceId != null && !incomingTraceId.isBlank()) {
            return incomingTraceId.trim();
        }
        return UUID.randomUUID().toString();
    }
}
