package com.laxman.codereviewassistant.security;

import java.util.Base64;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EncryptionServiceTest {

    // A fixed 32-byte (256-bit) test key, base64-encoded — same shape as a real
    // WEBHOOK_SECRET_ENCRYPTION_KEY, but obviously not one used anywhere real.
    private static final String TEST_KEY =
            Base64.getEncoder().encodeToString("01234567890123456789012345678901".getBytes());

    private final EncryptionService encryptionService = new EncryptionService(TEST_KEY);

    @Test
    void decryptReturnsTheOriginalPlaintext() {
        String plaintext = "super-secret-webhook-value";

        String encrypted = encryptionService.encrypt(plaintext);
        String decrypted = encryptionService.decrypt(encrypted);

        assertEquals(plaintext, decrypted);
    }

    @Test
    void encryptingTheSameValueTwiceProducesDifferentCiphertext() {
        // GCM uses a random IV each call, so identical plaintext must not
        // produce identical ciphertext — otherwise an attacker could spot
        // repeated secrets across rows just by comparing bytes.
        String plaintext = "same-value";

        String first = encryptionService.encrypt(plaintext);
        String second = encryptionService.encrypt(plaintext);

        assertNotEquals(first, second);
        assertEquals(plaintext, encryptionService.decrypt(first));
        assertEquals(plaintext, encryptionService.decrypt(second));
    }

    @Test
    void decryptingTamperedCiphertextFails() {
        String encrypted = encryptionService.encrypt("some-secret");
        byte[] raw = Base64.getDecoder().decode(encrypted);
        // flip a byte well past the IV (first 12 bytes) so we're corrupting
        // the ciphertext/auth-tag, not just the IV
        raw[raw.length - 1] ^= 0x01;
        String tampered = Base64.getEncoder().encodeToString(raw);

        assertThrows(IllegalStateException.class, () -> encryptionService.decrypt(tampered));
    }

    @Test
    void roundTripWorksForAVarietyOfInputs() {
        IntStream.of(0, 1, 16, 100).forEach(len -> {
            String plaintext = "x".repeat(len);
            assertEquals(plaintext, encryptionService.decrypt(encryptionService.encrypt(plaintext)));
        });
    }
}
