package com.chndn.orderpay.payment.gateway;

import com.chndn.orderpay.payment.webhook.GatewayWebhook;
import com.chndn.orderpay.payment.webhook.WebhookSigner;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * A fake gateway for local development. After a short delay it calls this service's own webhook endpoint
 * with a correctly signed request, the way a real gateway would.
 *
 * Behaviour you can trigger from the amount:
 * <ul>
 *   <li>cents == 13 (for example 10.13): the charge FAILS with "card_declined"</li>
 *   <li>anything else: the charge SUCCEEDS</li>
 * </ul>
 * Set {@code mock-gateway.duplicate-webhooks=true} to deliver every webhook twice and watch the second one
 * being ignored.
 */
@Component
public class MockPaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(MockPaymentGateway.class);

    private final ObjectMapper objectMapper;
    private final HttpClient http = HttpClient.newHttpClient();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    /** References already accepted, so a repeated initiateCharge never schedules a second charge. */
    private final Set<String> accepted = ConcurrentHashMap.newKeySet();

    private final String webhookUrl;
    private final String secret;
    private final long delayMs;
    private final boolean duplicateWebhooks;

    public MockPaymentGateway(ObjectMapper objectMapper,
                              @Value("${server.port:8082}") int port,
                              @Value("${webhook.secret}") String secret,
                              @Value("${mock-gateway.delay-ms:2000}") long delayMs,
                              @Value("${mock-gateway.duplicate-webhooks:false}") boolean duplicateWebhooks) {
        this.objectMapper = objectMapper;
        this.webhookUrl = "http://localhost:" + port + "/webhooks/gateway";
        this.secret = secret;
        this.delayMs = delayMs;
        this.duplicateWebhooks = duplicateWebhooks;
    }

    @Override
    public void initiateCharge(String reference, BigDecimal amount, String currency) {
        if (!accepted.add(reference)) {
            log.info("[mock-gateway] charge {} already accepted, ignoring repeat request", reference);
            return;
        }
        boolean fail = amount.movePointRight(2).abs().remainder(BigDecimal.valueOf(100)).intValue() == 13;
        GatewayWebhook webhook = fail
                ? new GatewayWebhook(UUID.randomUUID().toString(), GatewayWebhook.CHARGE_FAILED, reference, "card_declined")
                : new GatewayWebhook(UUID.randomUUID().toString(), GatewayWebhook.CHARGE_SUCCEEDED, reference, null);

        log.info("[mock-gateway] accepted charge {} for {} {}, will call webhook in {} ms",
                reference, amount, currency, delayMs);
        scheduler.schedule(() -> deliver(webhook), delayMs, TimeUnit.MILLISECONDS);
        if (duplicateWebhooks) {
            scheduler.schedule(() -> deliver(webhook), delayMs + 500, TimeUnit.MILLISECONDS);
        }
    }

    private void deliver(GatewayWebhook webhook) {
        try {
            String body = objectMapper.writeValueAsString(webhook);
            long timestamp = Instant.now().getEpochSecond();
            HttpRequest request = HttpRequest.newBuilder(URI.create(webhookUrl))
                    .header("Content-Type", "application/json")
                    .header("X-Timestamp", Long.toString(timestamp))
                    .header("X-Signature", WebhookSigner.sign(secret, timestamp, body))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("[mock-gateway] delivered {} for {} -> HTTP {}", webhook.type(), webhook.reference(), response.statusCode());
        } catch (JsonProcessingException e) {
            log.error("[mock-gateway] could not serialize webhook", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("[mock-gateway] webhook delivery failed: {}", e.toString());
        }
    }

    @PreDestroy
    void shutdown() {
        scheduler.shutdownNow();
    }
}
