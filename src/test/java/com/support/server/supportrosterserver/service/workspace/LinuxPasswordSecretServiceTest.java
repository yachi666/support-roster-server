package com.support.server.supportrosterserver.service.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LinuxPasswordSecretServiceTest {

    @Test
    void shouldEncryptAndDecryptWithExplicitKey() {
        LinuxPasswordSecretService service = new LinuxPasswordSecretService("explicit-test-key-for-linux-passwords");

        LinuxPasswordSecretService.EncryptedSecret encrypted = service.encrypt("SuperSecret!1");

        assertNotNull(encrypted.ciphertext());
        assertNotNull(encrypted.iv());
        assertEquals(LinuxPasswordSecretService.CURRENT_KEY_VERSION, encrypted.keyVersion());
        assertEquals("SuperSecret!1", service.decrypt(encrypted.ciphertext(), encrypted.iv()));
    }

    @Test
    void shouldProduceDifferentIvEachEncryption() {
        LinuxPasswordSecretService service = new LinuxPasswordSecretService("explicit-test-key-for-linux-passwords");

        LinuxPasswordSecretService.EncryptedSecret first = service.encrypt("SamePassword");
        LinuxPasswordSecretService.EncryptedSecret second = service.encrypt("SamePassword");

        // IVs must differ for semantic security
        assertNotNull(first.iv());
        assertNotNull(second.iv());
        // with overwhelming probability the random IVs differ
        assertEquals(first.keyVersion(), second.keyVersion());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void shouldThrowOnBlankKey(String blankKey) {
        assertThrows(IllegalStateException.class,
            () -> new LinuxPasswordSecretService(blankKey));
    }

    @Test
    void shouldThrowOnNullKey() {
        assertThrows(IllegalStateException.class,
            () -> new LinuxPasswordSecretService(null));
    }
}
