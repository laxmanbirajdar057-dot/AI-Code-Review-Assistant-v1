package com.laxman.codereviewassistant.security;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Encrypts/decrypts small secrets (currently: Repository.webhookSecret) before
 * they are persisted, using AES-256-GCM.
 *
 * Fix: RepositoryService previously stored the GitHub webhook secret in
 * plaintext despite a comment claiming it was encrypted. If the database were
 * ever exposed, every registered repo's webhook secret would leak, letting an
 * attacker forge webhook payloads that pass signature validation.
 *
 * Key comes from WEBHOOK_SECRET_ENCRYPTION_KEY, a base64-encoded 256-bit
 * (32-byte) key. Generate one with: openssl rand -base64 32
 *
 * Output format: base64( 12-byte random IV || ciphertext+authTag ).
 * The IV is stored alongside the ciphertext (this is standard practice for
 * GCM — the IV isn't secret, it just must never repeat for a given key).
 */
@Component
public class EncryptionService {

    private static final String ALGO = "AES/GCM/NoPadding";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom secureRandom = new SecureRandom();

    public EncryptionService(@Value("${webhook.secret.encryption.key}") String base64Key) {
        byte[] decoded = Base64.getDecoder().decode(base64Key);
        this.key = new SecretKeySpec(decoded, "AES");
    }

    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] cipherText = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            ByteBuffer buffer = ByteBuffer.allocate(iv.length + cipherText.length);
            buffer.put(iv);
            buffer.put(cipherText);

            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt secret", e);
        }
    }

    public String decrypt(String encoded) {
        try {
            byte[] combined = Base64.getDecoder().decode(encoded);
            ByteBuffer buffer = ByteBuffer.wrap(combined);

            byte[] iv = new byte[IV_LENGTH_BYTES];
            buffer.get(iv);
            byte[] cipherText = new byte[buffer.remaining()];
            buffer.get(cipherText);

            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] plainBytes = cipher.doFinal(cipherText);

            return new String(plainBytes, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decrypt secret", e);
        }
    }
}
