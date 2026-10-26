package com.chndn.orderpay.payment.webhook;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Webhook deduplication. Call {@link #markProcessed} inside the same transaction as the payment update:
 * if that transaction rolls back, the marker rolls back too, so the gateway's retry is processed normally.
 */
@Component
public class ProcessedWebhookStore {

    private final JdbcTemplate jdbc;

    public ProcessedWebhookStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** @return true if this call claimed the event, false if the same event id was already processed. */
    public boolean markProcessed(String eventId) {
        int inserted = jdbc.update(
                "INSERT INTO processed_webhooks (event_id) VALUES (?) ON CONFLICT (event_id) DO NOTHING",
                eventId);
        return inserted == 1;
    }
}
