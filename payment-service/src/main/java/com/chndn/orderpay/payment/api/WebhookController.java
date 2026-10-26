package com.chndn.orderpay.payment.api;

import com.chndn.orderpay.payment.service.PaymentService;
import com.chndn.orderpay.payment.webhook.GatewayWebhook;
import com.chndn.orderpay.payment.webhook.WebhookSigner;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;
    private final String secret;
    private final Duration tolerance;

    public WebhookController(PaymentService paymentService,
                             ObjectMapper objectMapper,
                             @Value("${webhook.secret}") String secret,
                             @Value("${webhook.tolerance-seconds:300}") long toleranceSeconds) {
        this.paymentService = paymentService;
        this.objectMapper = objectMapper;
        this.secret = secret;
        this.tolerance = Duration.ofSeconds(toleranceSeconds);
    }

    /**
     * The body is taken as a raw String on purpose: the signature is computed over the exact bytes the
     * gateway sent, so it must be verified before the JSON is parsed or re-serialized.
     */
    @PostMapping("/webhooks/gateway")
    public ResponseEntity<Void> receive(
            @RequestHeader(value = "X-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-Signature", required = false) String signature,
            @RequestBody String body) {

        if (!WebhookSigner.verify(secret, timestamp, body, signature, Instant.now(), tolerance)) {
            log.warn("Rejected webhook with invalid or expired signature");
            return ResponseEntity.status(401).build();
        }

        GatewayWebhook webhook;
        try {
            webhook = objectMapper.readValue(body, GatewayWebhook.class);
        } catch (JsonProcessingException e) {
            return ResponseEntity.badRequest().build();
        }
        if (webhook.eventId() == null || webhook.type() == null || webhook.reference() == null) {
            return ResponseEntity.badRequest().build();
        }

        paymentService.handleWebhook(webhook);
        // 2xx tells the gateway we have it. Non-2xx (for example 404 for an unknown reference) makes it retry.
        return ResponseEntity.ok().build();
    }
}
