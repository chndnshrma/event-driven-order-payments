package com.chndn.orderpay.order.api;

import com.chndn.orderpay.order.service.CreateOrderCommand;
import com.chndn.orderpay.order.service.CreateOrderResult;
import com.chndn.orderpay.order.service.OrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orders")
@Validated
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * Clients must send an Idempotency-Key header. Retrying the same request with the same key returns the
     * original order (200) instead of creating a second one (first call returns 201).
     */
    @PostMapping
    public ResponseEntity<OrderResponse> create(
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 100) String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request) {

        CreateOrderResult result = orderService.createOrder(
                idempotencyKey,
                new CreateOrderCommand(request.customerId(), request.amount(), request.currency().toUpperCase()));

        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(OrderResponse.from(result.order()));
    }

    @GetMapping("/{id}")
    public OrderResponse get(@PathVariable UUID id) {
        return OrderResponse.from(orderService.getOrder(id));
    }
}
