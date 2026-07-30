package com.laxman.codereviewassistant.security;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@Component
public class WebhookSignatureValidator {

    private static final String HMAC_ALGO = "HmacSHA256";

    public boolean isValid(String payload, String secret, String githubSignatureHeader) {
        if (githubSignatureHeader == null || !githubSignatureHeader.startsWith("sha256=")) {
            return false;
        }

        String expectedSignature = githubSignatureHeader.substring(7);
        String computedSignature = computeHmac(payload, secret);

        return constantTimeEquals(expectedSignature, computedSignature);
    }

    private String computeHmac(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGO));
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));

            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute HMAC", e);
        }
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}