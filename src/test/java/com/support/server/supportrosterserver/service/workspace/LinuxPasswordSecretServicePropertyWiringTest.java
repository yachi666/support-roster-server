package com.support.server.supportrosterserver.service.workspace;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * Verifies Spring property-wiring behaviour for LinuxPasswordSecretService:
 *   1. Non-local profiles must fail to start when support.linux-passwords.secret-key is absent,
 *      even when sa-token.jwt-secret-key is present (no silent fallback allowed).
 *   2. Non-local profiles must fail when the key is blank.
 *   3. The local profile satisfies the key requirement via application-local.yml.
 */
class LinuxPasswordSecretServicePropertyWiringTest {

    @Test
    void nonLocalContextRefusesStartupWhenLinuxPasswordKeyAbsent_evenWithJwtKeyPresent() {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        ctx.register(LinuxPasswordSecretService.class);
        // Enforce strict placeholder resolution (as Spring Boot does at startup)
        ctx.addBeanFactoryPostProcessor(new PropertySourcesPlaceholderConfigurer());
        // jwt key is present but linux-passwords key is intentionally absent
        ctx.getEnvironment().getPropertySources().addFirst(
            new MapPropertySource("non-local-sim",
                Map.of("sa-token.jwt-secret-key", "prod-jwt-secret-must-not-be-reused")));

        assertThrows(Exception.class, ctx::refresh,
            "Context must refuse startup when support.linux-passwords.secret-key is absent, " +
            "regardless of sa-token.jwt-secret-key being set");
    }

    @Test
    void nonLocalContextRefusesStartupWhenLinuxPasswordKeyIsBlank() {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        ctx.register(LinuxPasswordSecretService.class);
        // Enforce strict placeholder resolution (as Spring Boot does at startup)
        ctx.addBeanFactoryPostProcessor(new PropertySourcesPlaceholderConfigurer());
        ctx.getEnvironment().getPropertySources().addFirst(
            new MapPropertySource("non-local-blank",
                Map.of(
                    "support.linux-passwords.secret-key", "   ",
                    "sa-token.jwt-secret-key", "prod-jwt-secret")));

        assertThrows(Exception.class, ctx::refresh,
            "Context must refuse startup when support.linux-passwords.secret-key is blank");
    }

    @Test
    void localProfileStartsSuccessfullyUsingApplicationLocalYmlFallback() throws IOException {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        ctx.register(LinuxPasswordSecretService.class);

        // Load application-local.yml exactly as Spring does when the local profile is active
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        List<PropertySource<?>> sources = loader.load(
            "application-local", new ClassPathResource("application-local.yml"));
        sources.forEach(ps -> ctx.getEnvironment().getPropertySources().addLast(ps));

        assertDoesNotThrow(ctx::refresh,
            "Local profile must wire LinuxPasswordSecretService successfully via application-local.yml");

        LinuxPasswordSecretService service = ctx.getBean(LinuxPasswordSecretService.class);
        assertNotNull(service, "LinuxPasswordSecretService bean must exist when local profile is active");
        ctx.close();
    }
}
