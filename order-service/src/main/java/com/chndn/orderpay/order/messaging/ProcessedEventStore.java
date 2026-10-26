package com.chndn.orderpay.order.messaging;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Consumer-side deduplication. {@link #markProcessed} must be called inside the same transaction as the
 * business change: if that transaction rolls back, the "processed" marker rolls back with it, so the
 * event is handled again on redelivery instead of being lost.
 */
@Component
public class ProcessedEventStore {

    private final JdbcTemplate jdbc;

    public ProcessedEventStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * @return true if this call claimed the event (first time seen), false if it was already processed.
     *         The insert is atomic, so two concurrent deliveries of the same event cannot both win.
     */
    public boolean markProcessed(UUID eventId) {
        int inserted = jdbc.update(
                "INSERT INTO processed_events (event_id) VALUES (?) ON CONFLICT (event_id) DO NOTHING",
                eventId);
        return inserted == 1;
    }
}
