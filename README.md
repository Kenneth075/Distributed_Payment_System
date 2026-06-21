# Payment Authorization Service

A production-grade distributed payment authorization backend built with Java Spring Boot, PostgreSQL, and Kubernetes.

## Phase Progress
- [x] **Phase 1** — Core API, DB schema, Idempotency, Card validation, Daily limits
- [x] **Phase 2** — Ledger mock integration with Resilience4j (retry, circuit breaker, timeout)
- [x] **Phase 3** — Saga pattern with compensating transactions
- [ ] **Phase 4** — Docker + Kubernetes manifests (Kind cluster)
- [ ] **Phase 5** — Prometheus + Grafana observability (RED method)
- [ ] **Phase 6** — Full test suite + RUNBOOK.md

---

## Quick Start (Local)

### Prerequisites
- Java 17+
- Maven 3.9+
- Docker + Docker Compose

### Run with Docker Compose

```bash
# Start PostgreSQL and the app
docker-compose up --build

# Verify it's healthy
curl http://localhost:8080/actuator/health
```

### Run locally (without Docker)

```bash
# Start only PostgreSQL in Docker
docker-compose up postgres -d

# Run the app
./mvnw spring-boot:run
```

---

## API Reference

### POST /api/v1/payments/authorize

Authorize a payment. Requires the `Idempotency-Key` header.

**Headers:**
| Header | Required | Description |
|--------|----------|-------------|
| `Idempotency-Key` | Yes | Client-generated UUID. Sending the same key twice returns the original result. |
| `Content-Type` | Yes | `application/json` |

**Request Body:**
```json
{
  "cardId": "a1b2c3d4-0000-0000-0000-000000000001",
  "amount": 150.00,
  "currency": "USD",
  "merchantId": "merchant-001",
  "description": "Order #12345"
}
```

**Responses:**
| HTTP Status | Meaning |
|-------------|---------|
| 201 Created | New payment processed (check `status` field for AUTHORIZED or DECLINED) |
| 200 OK | Idempotent replay — same key seen before |
| 400 Bad Request | Validation error or missing header |
| 404 Not Found | Card ID does not exist |
| 409 Conflict | Idempotency key reused with different request params |

---

## curl Examples

### Authorize a payment
```bash
curl -X POST http://localhost:8080/api/v1/payments/authorize \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{
    "cardId": "a1b2c3d4-0000-0000-0000-000000000001",
    "amount": 150.00,
    "currency": "USD",
    "merchantId": "merchant-001",
    "description": "Order #12345"
  }'
```

### Test idempotency (use the SAME key twice)
```bash
KEY="my-fixed-key-001"

# First call → 201
curl -X POST http://localhost:8080/api/v1/payments/authorize \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $KEY" \
  -d '{"cardId":"a1b2c3d4-0000-0000-0000-000000000001","amount":100,"currency":"USD","merchantId":"m-1"}'

# Second call with same key → 200, idempotentReplay: true
curl -X POST http://localhost:8080/api/v1/payments/authorize \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $KEY" \
  -d '{"cardId":"a1b2c3d4-0000-0000-0000-000000000001","amount":100,"currency":"USD","merchantId":"m-1"}'
```

### Test inactive card (seed card 3)
```bash
curl -X POST http://localhost:8080/api/v1/payments/authorize \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{
    "cardId": "a1b2c3d4-0000-0000-0000-000000000003",
    "amount": 50.00,
    "currency": "USD",
    "merchantId": "merchant-001"
  }'
```

### Test daily limit exceeded (card 2 has $1000 limit)
```bash
curl -X POST http://localhost:8080/api/v1/payments/authorize \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{
    "cardId": "a1b2c3d4-0000-0000-0000-000000000002",
    "amount": 1500.00,
    "currency": "USD",
    "merchantId": "merchant-001"
  }'
```

---

## Phase 3 — Saga Pattern (Settlement + Compensation)

### Full end-to-end happy path
```bash
# 1. Authorize
KEY=$(uuidgen)
PAYMENT_ID=$(curl -s -X POST http://localhost:8080/api/v1/payments/authorize \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $KEY" \
  -d '{"cardId":"a1b2c3d4-0000-0000-0000-000000000001","amount":100,"currency":"USD","merchantId":"m-1"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['paymentId'])")

echo "PaymentId: $PAYMENT_ID"

# 2. Settle (use the paymentId from step 1)
curl -X POST http://localhost:8080/api/v1/payments/$PAYMENT_ID/settle \
  -H "Content-Type: application/json" \
  -d '{"merchantId":"merchant-001","merchantReference":"POS-12345"}'
# → {"status":"SETTLED", "settledAt":"..."}
```

### Trigger saga compensation (settlement fails → funds released)
```bash
# 1. Authorize normally
KEY=$(uuidgen)
PAYMENT_ID=$(curl -s -X POST http://localhost:8080/api/v1/payments/authorize \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $KEY" \
  -d '{"cardId":"a1b2c3d4-0000-0000-0000-000000000001","amount":100,"currency":"USD","merchantId":"m-1"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['paymentId'])")

# 2. Put ledger-mock in settle-fail mode
curl "http://localhost:9090/ledger/admin/fail-settle?enabled=true"

# 3. Try to settle — will fail → saga compensation triggers automatically
curl -X POST http://localhost:8080/api/v1/payments/$PAYMENT_ID/settle \
  -H "Content-Type: application/json" \
  -d '{"merchantId":"merchant-001"}'
# → {"status":"COMPENSATED", "compensationReason":"Settlement failed: ..."}

# 4. Reset ledger-mock
curl http://localhost:9090/ledger/admin/reset
```

### Test state guards
```bash
# Try to settle the same payment twice → 409 Conflict
curl -X POST http://localhost:8080/api/v1/payments/$PAYMENT_ID/settle \
  -H "Content-Type: application/json" \
  -d '{"merchantId":"merchant-001"}'
# → 409 {"error":"Conflict", "message":"Payment ... cannot be processed: expected AUTHORIZED but was SETTLED"}
```

---

## Phase 2 — Resilience Scenarios (ledger-mock)

The ledger-mock supports query parameters to drive every Resilience4j path.

### Test normal flow (ledger succeeds)
```bash
curl -X POST http://localhost:8080/api/v1/payments/authorize \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{"cardId":"a1b2c3d4-0000-0000-0000-000000000001","amount":100,"currency":"USD","merchantId":"m-1"}'
```

### Test retry success — ledger fails twice, succeeds on 3rd attempt
```bash
# First authorise with ?failTimes=2 on the ledger side
curl "http://localhost:9090/ledger/reserve?failTimes=2"   # prime the counter

# Now trigger a payment — Resilience4j retries twice and succeeds
curl -X POST http://localhost:8080/api/v1/payments/authorize \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{"cardId":"a1b2c3d4-0000-0000-0000-000000000001","amount":100,"currency":"USD","merchantId":"m-1"}'
```

### Test circuit breaker — open the circuit with sustained failures
```bash
# Send 6+ payments while ledger is returning 500s
# The payment-auth-service will stop calling ledger after threshold
for i in {1..8}; do
  curl -s -X POST http://localhost:8080/api/v1/payments/authorize \
    -H "Content-Type: application/json" \
    -H "Idempotency-Key: key-$i" \
    -d '{"cardId":"a1b2c3d4-0000-0000-0000-000000000001","amount":10,"currency":"USD","merchantId":"m-1"}' \
    | python3 -m json.tool
  sleep 0.5
done
# Circuit opens after ~5 failures; subsequent calls are short-circuited immediately
```

### Test TimeLimiter — ledger responds slowly
```bash
# ledger-mock will sleep 3 seconds; Resilience4j deadline is 2s → timeout
curl "http://localhost:9090/ledger/reserve?delay=3000"    # Not needed - pass delay in body; see ledger-mock

curl -X POST http://localhost:8080/api/v1/payments/authorize \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{"cardId":"a1b2c3d4-0000-0000-0000-000000000001","amount":100,"currency":"USD","merchantId":"m-1"}'
# → DECLINED with "could not be processed"
```

### Reset ledger failure counters between tests
```bash
curl http://localhost:9090/ledger/admin/reset
```

| Card ID | Holder | Daily Limit | Active |
|---------|--------|-------------|--------|
| `a1b2c3d4-0000-0000-0000-000000000001` | Kenneth Demo | $5,000 | Yes |
| `a1b2c3d4-0000-0000-0000-000000000002` | Test User Two | $1,000 | Yes |
| `a1b2c3d4-0000-0000-0000-000000000003` | Blocked User | $5,000 | **No** |

---

## Running Tests

```bash
# Unit tests only (fast, no DB required)
./mvnw test

# Build + test
./mvnw verify
```