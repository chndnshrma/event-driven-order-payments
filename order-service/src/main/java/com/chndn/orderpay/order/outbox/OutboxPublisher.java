package com.chndn.orderpay.order.outbox;

import com.chndn.orderpay.events.EventTypes;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Relays outbox rows to Kafka. Delivery is at-least-once: if the process dies after Kafka accepted a
 * message but before the row is marked published, the row is sent again on the next poll. That is safe
 * because every consumer deduplicates on the event id.
 *
 * Runs as a single instance. To scale out, claim rows with "FOR UPDATE SKIP LOCKED".
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxRepository outbox;
    private final KafkaTemplate<String, String> kafka;
    private final int batchSize;

    public OutboxPublisher(OutboxRepository outbox,
                           KafkaTemplate<String, String> kafka,
                           @Value("${outbox.batch-size:50}") int batchSize) {
        this.outbox = outbox;
        this.kafka = kafka;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${outbox.poll-interval-ms:1000}")
    public void publishPending() {
        List<OutboxEvent> pending = outbox.findUnpublished(PageRequest.of(0, batchSize));
        for (OutboxEvent event : pending) {
            try {
                ProducerRecord<String, String> record =
                        new ProducerRecord<>(event.getTopic(), event.getAggregateId().toString(), event.getPayload());
                record.headers().add(EventTypes.HEADER_EVENT_TYPE, event.getEventType().getBytes(StandardCharsets.UTF_8));

                // Wait for the broker ack before marking the row as published.
                kafka.send(record).get(10, TimeUnit.SECONDS);

                event.markPublished();
                outbox.save(event);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                // Stop this batch to keep ordering; the same rows are retried on the next poll.
                log.warn("Could not publish outbox event {} ({}), will retry: {}",
                        event.getId(), event.getEventType(), e.toString());
                return;
            }
        }
    }
}
