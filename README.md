# VibeVault Order Service

Order management microservice for the VibeVault e-commerce platform. Implements the Saga pattern for distributed transactions across cart, order, and payment services.

## Tech Stack

- **Runtime:** Java 21, Spring Boot 4.0.3
- **Database:** MySQL (RDS on EKS / local Docker)
- **Messaging:** Apache Kafka (consumer + producer, KRaft mode)
- **Auth:** OAuth2 Resource Server (JWT from userservice)
- **Migration:** Flyway
- **Infrastructure:** AWS EKS, Helm, GitHub Actions CI/CD

## API Endpoints

All endpoints require OAuth2 authentication. Orders are scoped to the authenticated user.

| Method | Endpoint | Description | Status |
|--------|----------|-------------|--------|
| `GET` | `/orders` | Get paginated orders for authenticated user | 200 |
| `GET` | `/orders/{orderId}` | Get order details (owner only, 404 for non-owner) | 200 |

## Kafka Events

### Consumes

| Topic | Event | Action |
|-------|-------|--------|
| `cart-events` | `CHECKOUT_INITIATED` | Create order with PENDING status |
| `payment-events` | `PAYMENT_CONFIRMED` | Update order to CONFIRMED |
| `payment-events` | `PAYMENT_FAILED` | Update order to CANCELLED |

### Produces to `order-events` topic

| Event | Trigger |
|-------|---------|
| `ORDER_CREATED` | New order created from cart checkout |
| `ORDER_CONFIRMED` | Payment confirmed via saga |
| `ORDER_CANCELLED` | Payment failed via saga |

## Saga Pattern

```
Cart checkout → CHECKOUT_INITIATED (cart-events)
  → Order Service creates order (PENDING) → ORDER_CREATED (order-events)
  → Payment Gateway processes payment
  → PAYMENT_CONFIRMED or PAYMENT_FAILED (payment-events)
  → Order Service updates status → ORDER_CONFIRMED or ORDER_CANCELLED
```

## Idempotency

- Orders are deduplicated via unique `cart_event_id` constraint
- Confirm/cancel operations are idempotent (already in target state → no-op)
- `@EntityGraph` for eager item fetch with `spring.jpa.open-in-view=false`

## Environment Variables

| Variable | Default | Description |
|---|---|---|
| `PORT` | `8083` | Server port |
| `DB_URL` | `jdbc:mysql://localhost:3306/orderservice` | MySQL JDBC URL |
| `DB_USERNAME` | `root` | Database username |
| `DB_PASSWORD` | `password` | Database password |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka broker address |
| `ISSUER_URI` | `http://localhost:8081` | OAuth2 issuer for JWT validation |

## Local Development

### Prerequisites
- Java 21
- Docker & Docker Compose
- Other services running (userservice, productservice, cartservice on vibevault-network)

### Run
```bash
docker network create vibevault-network 2>/dev/null || true
docker compose up --build
```

### Test
```bash
./scripts/test-order-apis.sh
```

**37 API integration tests** covering: health checks, order creation via Kafka, pagination, owner isolation, error handling, Kafka event verification.

## Unit Tests

**16 tests** (11 service + 4 controller + 1 context):
```bash
./mvnw verify
```

## Port

`8083` (configurable via `PORT` env var)
