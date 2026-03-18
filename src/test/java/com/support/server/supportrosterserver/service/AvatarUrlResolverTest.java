package com.support.server.supportrosterserver.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class AvatarUrlResolverTest {

    @Test
    void shouldBuildAvatarUrlUsingFirstFourCharactersOfStaffCode() {
        AvatarUrlResolver resolver = new AvatarUrlResolver("https://photos.global.image/casual/square");

        String avatarUrl = resolver.resolve("ABCD12345");

        assertEquals("https://photos.global.image/casual/square/ABCD/ABCD12345.jpg", avatarUrl);
    }

    @Test
    void shouldUseWholeStaffCodeWhenShorterThanFourCharacters() {
        AvatarUrlResolver resolver = new AvatarUrlResolver("https://photos.global.image/casual/square");

        String avatarUrl = resolver.resolve("123");

        assertEquals("https://photos.global.image/casual/square/123/123.jpg", avatarUrl);
    }

    @Test
    void shouldTrimTrailingSlashFromConfiguredBaseUrl() {
        AvatarUrlResolver resolver = new AvatarUrlResolver("https://photos.global.image/casual/square/");

        String avatarUrl = resolver.resolve("10001");

        assertEquals("https://photos.global.image/casual/square/1000/10001.jpg", avatarUrl);
    }

    @Test
    void shouldReturnNullWhenStaffCodeIsBlank() {
        AvatarUrlResolver resolver = new AvatarUrlResolver("https://photos.global.image/casual/square");

        assertNull(resolver.resolve("  "));
    }
}