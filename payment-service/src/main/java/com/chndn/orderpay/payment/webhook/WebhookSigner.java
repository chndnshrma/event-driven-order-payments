package com.chndn.orderpay.payment.webhook;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * HMAC-SHA256 webhook signatures, in the style used by Stripe and others.
 *
 * The signed message is {@code "<timestamp>.<raw body>"}. Signing the timestamp together with the body
 * means an attacker who captures a valid webhook cannot replay it later: the timestamp would be too old.
 * Pure Java on purpose, so it is easy to unit test.
 */
public final class WebhookSigner {

    private static final String ALGORITHM = "HmacSHA256";
    private static final String PREFIX = "sha256=";

    private WebhookSigner() {
    }

    public static String sign(String secret, long timestampSeconds, String body) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            byte[] digest = mac.doFinal((timestampSeconds + "." + body).getBytes(StandardCharsets.UTF_8));
            return PREFIX + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA256 is not available", e);
        }
    }

    /**
     * @return true only if the timestamp is within the tolerance window and the signature matches the body.
     */
    public static boolean verify(String secret,
                                 String timestampHeader,
                                 String body,
                                 String signatureHeader,
                                 Instant now,
                                 Duration tolerance) {
        if (timestampHeader == null || signatureHeader == null || body == null) {
            return false;
        }
        long timestamp;
        try {
            timestamp = Long.parseLong(timestampHeader.trim());
        } catch (NumberFormatException e) {
            return false;
        }
        if (Math.abs(now.getEpochSecond() - timestamp) > tolerance.toSeconds()) {
            return false;
        }
        String expected = sign(secret, timestamp, body);
        // Constant-time comparison, so response timing does not leak how many characters matched.
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signatureHeader.trim().getBytes(StandardCharsets.UTF_8));
    }
}
