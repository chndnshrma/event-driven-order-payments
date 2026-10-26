package com.chndn.orderpay.payment.api;

import com.chndn.orderpay.payment.domain.Payment;
import com.chndn.orderpay.payment.service.PaymentService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping("/by-order/{orderId}")
    public PaymentResponse getByOrder(@PathVariable UUID orderId) {
        return PaymentResponse.from(paymentService.getByOrderId(orderId));
    }

    public record PaymentResponse(
            UUID id,
            UUID orderId,
            BigDecimal amount,
            String currency,
            String status,
            String gatewayReference,
            String failureReason,
            Instant createdAt,
            Instant updatedAt) {

        static PaymentResponse from(Payment p) {
            return new PaymentResponse(p.getId(), p.getOrderId(), p.getAmount(), p.getCurrency(),
                    p.getStatus().name(), p.getGatewayReference(), p.getFailureReason(),
                    p.getCreatedAt(), p.getUpdatedAt());
        }
    }
}
