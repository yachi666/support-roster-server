package com.support.server.supportrosterserver.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * Configures the set of trusted proxy IP addresses whose X-Forwarded-For headers
 * are accepted when resolving the real client IP address.
 *
 * Only requests arriving from these IPs will have X-Forwarded-For trusted.
 * Defaults to localhost only to prevent header spoofing from untrusted networks.
 */
@Component
@ConfigurationProperties(prefix = "support.trusted-proxies")
@Getter
@Setter
public class TrustedProxyProperties {

    /**
     * List of trusted proxy IP addresses. Only requests from these IPs will have
     * their X-Forwarded-For header trusted. Defaults to loopback addresses.
     */
    private List<String> ips = List.of("127.0.0.1", "::1");
}
