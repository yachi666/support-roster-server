package com.support.server.supportrosterserver.service.workspace;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class LinuxPasswordSecretService {

    public static final String CURRENT_KEY_VERSION = "v1";

    private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_BYTES = 12;

    private final SecureRandom secureRandom = new SecureRandom();
    private final SecretKeySpec secretKey;

    public LinuxPasswordSecretService(
            @Value("${support.linux-passwords.secret-key:${SA_TOKEN_JWT_SECRET_KEY:support-linux-passwords-local-secret-change-before-production}}") String secretKeyMaterial) {
        this.secretKey = new SecretKeySpec(deriveKey(secretKeyMaterial), "AES");
    }

    public EncryptedSecret encrypt(String plaintext) {
        byte[] iv = new byte[IV_BYTES];
        secureRandom.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return new EncryptedSecret(
                Base64.getEncoder().encodeToString(ciphertext),
                Base64.getEncoder().encodeToString(iv),
                CURRENT_KEY_VERSION
            );
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Failed to encrypt linux password secret.", ex);
        }
    }

    public String decrypt(String ciphertext, String iv) {
        try {
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(
                Cipher.DECRYPT_MODE,
                secretKey,
                new GCMParameterSpec(GCM_TAG_BITS, Base64.getDecoder().decode(iv))
            );
            byte[] plaintext = cipher.doFinal(Base64.getDecoder().decode(ciphertext));
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new IllegalStateException("Failed to decrypt linux password secret.", ex);
        }
    }

    private byte[] deriveKey(String secretKeyMaterial) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(secretKeyMaterial.getBytes(StandardCharsets.UTF_8));
            return Arrays.copyOf(digest, 32);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Failed to initialize linux password secret key.", ex);
        }
    }

    public record EncryptedSecret(String ciphertext, String iv, String keyVersion) {
    }
}
