# Order & Payments Platform

An event-driven order and payments backend built with Spring Boot, Kafka and PostgreSQL.
Two independent services talk through events and stay correct when messages are duplicated, retried or arrive late.

```
 Client
   |  POST /orders  (Idempotency-Key header)
   v
+----------------+  OrderCreated    +------------------+   charge request    +-----------------+
| order-service  | ---------------> | payment-service  | ------------------> | payment gateway |
| (port 8081)    |   Kafka          | (port 8082)      |                     | (mock locally)  |
|                | <--------------- |                  | <------------------ |                 |
+----------------+  PaymentCompleted+------------------+  signed webhook     +-----------------+
   |  own schema      PaymentFailed     |  own schema         (HMAC + timestamp)
   v                                    v
 PostgreSQL (order_service)         PostgreSQL (payment_service)
```

**Happy path:** order created (`PENDING_PAYMENT`) -> `OrderCreated` event -> payment created and gateway charged ->
gateway calls the webhook -> `PaymentCompleted` event -> order becomes `PAID`.

**Failure path (saga):** gateway reports a failure -> `PaymentFailed` event -> order is `CANCELLED` with the reason.
This is the compensating action: there is no distributed transaction, so each service undoes its own part.

## What this project demonstrates

| Concept | Where it lives |
|---|---|
| Idempotent API (retries never create two orders) | `OrderService.createOrder`, unique `idempotency_key`, `Idempotency-Key` header |
| Transactional outbox (DB write and event publish cannot half-fail) | `OutboxEvent`, `OutboxPublisher` in both services |
| Idempotent consumers (duplicate events are harmless) | `ProcessedEventStore` (event-id table), `findOrCreateForOrder` (unique `order_id`) |
| Saga with compensation | `OrderService.onPaymentFailed` |
| State machines | `OrderStatus`, `PaymentStatus` (`canTransitionTo`) |
| Optimistic locking | `@Version` on `Order` and `Payment` |
| Webhook security: HMAC signature, replay protection | `WebhookSigner`, `WebhookController` |
| Webhook idempotency | `ProcessedWebhookStore`, `PaymentService.handleWebhook` |
| Retries with backoff and dead letter topics | `KafkaConfig` (`order-events.DLT`, `payment-events.DLT`) |
| External call kept out of the DB transaction | `OrderEventListener` |
| Database per service (schema per service on one instance) | `order_service` / `payment_service` schemas, Flyway migrations |
| CI | `.github/workflows/ci.yml` |

## Run it locally

Requirements: Java 21, Maven 3.9+, Docker.

```bash
# 1. Infrastructure. Pick ONE database option.
docker compose up -d kafka                       # Kafka only, if you use Supabase for Postgres
docker compose --profile local-db up -d          # Kafka + a local Postgres

# 2. (Supabase only) set these in the shell you start the services from, see .env.example
export DB_HOST=... DB_PORT=5432 DB_NAME=postgres DB_USERNAME=postgres DB_PASSWORD=... DB_SSLMODE=require

# 3. Build and test
mvn clean verify

# 4. Start both services, each in its own terminal
mvn -pl order-service spring-boot:run
mvn -pl payment-service spring-boot:run
```

Supabase notes: use the direct or session-mode connection (port 5432), not the transaction pooler (6543).
Flyway creates the `order_service` and `payment_service` schemas by itself.

## Try it

```bash
# Create an order. Success amounts are anything except cents == 13.
curl -i -X POST localhost:8081/orders \
  -H 'Content-Type: application/json' -H 'Idempotency-Key: demo-1' \
  -d '{"customerId":"cust-1","amount":49.99,"currency":"USD"}'
# -> 201, status PENDING_PAYMENT

# Wait ~3 seconds, then read it back (use the id from the response)
curl localhost:8081/orders/<order-id>           # status PAID
curl localhost:8082/payments/by-order/<order-id>

# Retry the SAME request: 200 and the same order, nothing new is created
curl -i -X POST localhost:8081/orders \
  -H 'Content-Type: application/json' -H 'Idempotency-Key: demo-1' \
  -d '{"customerId":"cust-1","amount":49.99,"currency":"USD"}'

# Same key, different body: 422
curl -i -X POST localhost:8081/orders \
  -H 'Content-Type: application/json' -H 'Idempotency-Key: demo-1' \
  -d '{"customerId":"cust-1","amount":999.00,"currency":"USD"}'

# Failure path: an amount ending in .13 is declined by the mock gateway -> order CANCELLED (card_declined)
curl -X POST localhost:8081/orders \
  -H 'Content-Type: application/json' -H 'Idempotency-Key: demo-2' \
  -d '{"customerId":"cust-1","amount":10.13,"currency":"USD"}'
```

Other things worth trying:
- Start payment-service with `MOCK_GATEWAY_DUPLICATE_WEBHOOKS=true` and watch the log: the second delivery is ignored.
- Call the webhook with a wrong signature and get `401`:
  `curl -i -X POST localhost:8082/webhooks/gateway -H 'X-Timestamp: 1' -H 'X-Signature: sha256=bad' -d '{}'`
- Stop payment-service, create orders, start it again: the events were waiting in Kafka and get processed.
- Stop Kafka, create an order, start Kafka: the outbox publisher delivers it once Kafka is back.

## Project layout

```
common-events/     event records, topic names (the contract between the services)
order-service/     REST API, order state machine, outbox, payment event consumer
payment-service/   order event consumer, payments, webhook endpoint, mock gateway, outbox
```

## Verification status (read this first)

This code was written in an environment that could not download Maven dependencies, so the full Spring build
has **not been compiled or run end to end**. What has been checked:

- Verified by running: the order and payment state machines, the webhook signing and verification
  (including a match against an independent OpenSSL HMAC), and the mock gateway's failure rule.
- Verified against a real PostgreSQL 16: both Flyway migrations apply cleanly, the atomic
  `INSERT ... ON CONFLICT DO NOTHING` dedupe behaves as intended, and the unique constraints reject duplicate
  idempotency keys and a second payment for the same order.
- Not verified: Spring wiring, JPA mapping against the schema (`ddl-auto: validate` will report any mismatch at
  startup), Kafka consumers and error handler, the controllers.

So the first `mvn clean verify` and first start-up are the real test. If something fails, the error message
should point at the exact line; paste it back and it can be fixed quickly.

## Known gaps and good next steps

1. **Integration tests with Testcontainers** (Kafka + Postgres): the highest-value addition, and the natural
   next step for the resume.
2. **Multiple outbox publishers**: use `SELECT ... FOR UPDATE SKIP LOCKED` to claim rows safely.
3. **Refund flow**: if payment completes for an order that was already cancelled, it is logged as an error for
   manual review (`OrderService.onPaymentCompleted`).
4. **Pending-payment timeout**: cancel orders whose payment never answers.
5. **Correlation IDs** in logs to trace one order across both services, plus metrics.
6. **A real gateway** (Stripe or Razorpay test mode) behind the existing `PaymentGateway` interface.
7. **Dockerfiles and AWS deployment.**
8. **AI support agent** (tools + RAG) that reads order and payment data through these services.
