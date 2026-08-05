package com.laxman.codereviewassistant.security;

import java.nio.charset.StandardCharsets;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class WebhookSignatureValidatorTest {

    private final WebhookSignatureValidator validator = new WebhookSignatureValidator();
    private static final String SECRET = "test-secret";
    private static final String PAYLOAD = "{\"action\":\"opened\",\"number\":42}";

    @Test
    void acceptsASignatureComputedWithTheCorrectSecret() {
        String header = "sha256=" + hmacHex(PAYLOAD, SECRET);

        assertTrue(validator.isValid(PAYLOAD, SECRET, header));
    }

    @Test
    void rejectsASignatureComputedWithTheWrongSecret() {
        String header = "sha256=" + hmacHex(PAYLOAD, "a-different-secret");

        assertFalse(validator.isValid(PAYLOAD, SECRET, header));
    }

    @Test
    void rejectsAPayloadThatWasTamperedWithAfterSigning() {
        // signature was computed for the original payload...
        String header = "sha256=" + hmacHex(PAYLOAD, SECRET);
        // ...but the body GitHub actually sent has been altered in transit
        String tamperedPayload = "{\"action\":\"opened\",\"number\":9999}";

        assertFalse(validator.isValid(tamperedPayload, SECRET, header));
    }

    @Test
    void rejectsAHeaderMissingTheShaPrefix() {
        String rawHexOnly = hmacHex(PAYLOAD, SECRET); // missing "sha256=" prefix

        assertFalse(validator.isValid(PAYLOAD, SECRET, rawHexOnly));
    }

    @Test
    void rejectsANullHeader() {
        assertFalse(validator.isValid(PAYLOAD, SECRET, null));
    }

    /**
     * Re-implements the HMAC computation independently of WebhookSignatureValidator
     * so the test isn't just calling the same code it's supposed to verify.
     */
    private String hmacHex(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
