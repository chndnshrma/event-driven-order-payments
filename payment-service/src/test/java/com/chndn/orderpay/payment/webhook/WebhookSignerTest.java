package com.chndn.orderpay.payment.webhook;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class WebhookSignerTest {

    private static final String SECRET = "test-secret";
    private static final String BODY = "{\"eventId\":\"e1\",\"type\":\"charge.succeeded\",\"reference\":\"ch_1\"}";
    private static final Duration TOLERANCE = Duration.ofMinutes(5);

    private final Instant now = Instant.parse("2026-10-27T10:00:00Z");

    @Test
    void validSignatureIsAccepted() {
        long ts = now.getEpochSecond();
        String sig = WebhookSigner.sign(SECRET, ts, BODY);

        assertTrue(WebhookSigner.verify(SECRET, Long.toString(ts), BODY, sig, now, TOLERANCE));
    }

    @Test
    void tamperedBodyIsRejected() {
        long ts = now.getEpochSecond();
        String sig = WebhookSigner.sign(SECRET, ts, BODY);

        assertFalse(WebhookSigner.verify(SECRET, Long.toString(ts), BODY.replace("ch_1", "ch_2"), sig, now, TOLERANCE));
    }

    @Test
    void wrongSecretIsRejected() {
        long ts = now.getEpochSecond();
        String sig = WebhookSigner.sign("other-secret", ts, BODY);

        assertFalse(WebhookSigner.verify(SECRET, Long.toString(ts), BODY, sig, now, TOLERANCE));
    }

    @Test
    void oldTimestampIsRejectedEvenWithAValidSignature() {
        long old = now.minus(Duration.ofMinutes(10)).getEpochSecond();
        String sig = WebhookSigner.sign(SECRET, old, BODY);

        assertFalse(WebhookSigner.verify(SECRET, Long.toString(old), BODY, sig, now, TOLERANCE));
    }

    @Test
    void timestampIsPartOfTheSignature() {
        long ts = now.getEpochSecond();
        String sig = WebhookSigner.sign(SECRET, ts, BODY);

        // Same body and signature, different timestamp header: must not verify.
        assertFalse(WebhookSigner.verify(SECRET, Long.toString(ts + 1), BODY, sig, now, TOLERANCE));
        assertNotEquals(sig, WebhookSigner.sign(SECRET, ts + 1, BODY));
    }

    @Test
    void missingOrMalformedHeadersAreRejected() {
        assertFalse(WebhookSigner.verify(SECRET, null, BODY, "sha256=abc", now, TOLERANCE));
        assertFalse(WebhookSigner.verify(SECRET, "123", BODY, null, now, TOLERANCE));
        assertFalse(WebhookSigner.verify(SECRET, "not-a-number", BODY, "sha256=abc", now, TOLERANCE));
    }
}
