package com.chndn.orderpay.payment.messaging;

import com.chndn.orderpay.events.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

@Configuration
public class KafkaConfig {

    @Bean
    NewTopic orderEventsTopic() {
        return TopicBuilder.name(Topics.ORDER_EVENTS).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic paymentEventsTopic() {
        return TopicBuilder.name(Topics.PAYMENT_EVENTS).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic orderEventsDeadLetterTopic() {
        return TopicBuilder.name(Topics.ORDER_EVENTS + Topics.DLT_SUFFIX).partitions(1).replicas(1).build();
    }

    /**
     * Retries with exponential backoff (1s, 2s, 4s), then moves the message to the dead letter topic so one
     * bad message cannot block the partition. Spring Boot applies this bean to the listener containers.
     */
    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> template) {
        var recoverer = new DeadLetterPublishingRecoverer(template,
                (record, ex) -> new TopicPartition(record.topic() + Topics.DLT_SUFFIX, -1));

        var backOff = new ExponentialBackOff(1_000L, 2.0);
        backOff.setMaxAttempts(3);

        var handler = new DefaultErrorHandler(recoverer, backOff);
        // A malformed payload will never succeed on retry, so send it straight to the DLT.
        handler.addNotRetryableExceptions(IllegalArgumentException.class);
        return handler;
    }
}
