package com.chndn.orderpay.payment.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Body of a gateway callback. {@code eventId} is the gateway's own id for this notification and is what
 * we deduplicate on. {@code type} is "charge.succeeded" or "charge.failed".
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GatewayWebhook(String eventId, String type, String reference, String reason) {

    public static final String CHARGE_SUCCEEDED = "charge.succeeded";
    public static final String CHARGE_FAILED = "charge.failed";
}
