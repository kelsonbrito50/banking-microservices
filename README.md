# 🏦 Banking Microservices Platform

Full-stack banking application demonstrating production-grade microservice patterns.

**Stack:** Java 21 · Spring Boot 3 · Spring Cloud Gateway · Kafka · PostgreSQL · Redis · Docker · Angular 18

## Architecture

```
Angular (4200) → API Gateway (8080) → Auth (8081) | Account (8082) | Transaction (8083)
                                            ↓ Kafka events ↓
                                      Notification Service (8084)
```

- **Database per service** — services communicate only via REST or Kafka events
- **JWT auth at the gateway** — identity propagated downstream via `X-User-*` headers (client copies are stripped, so they can't be spoofed); services enforce per-user ownership on every account and transfer operation
- **Internal-only endpoints** — debit/credit live under `/internal/**`, which the gateway never routes; in Docker only the gateway is exposed
- **Rate limiting** on transfers (Redis-backed, 5 req/s per user)
- **Account lockout** after 5 failed logins

## Key patterns (talk about these in interviews)

| Pattern | Where | Why |
|---|---|---|
| Saga (orchestration) | `TransferService` | Debit + credit across services with compensating rollback; each state change commits in its own DB tx so FAILED records and outbox events survive, and a failed compensation lands in `COMPENSATION_REQUIRED` with an ops alert event |
| Outbox | `OutboxEvent` + `OutboxPublisher` | Event saved in same DB tx as the status change, marked published only after the Kafka broker acks — never lost if Kafka is down |
| Idempotency keys | `TransferController` | Retrying a transfer never charges twice (unique constraint resolves the concurrent-retry race) |
| Pessimistic locking | `AccountRepository` | Two concurrent transfers can't read the same balance |
| Cache invalidation | `AccountService` | Redis balance cache evicted on every mutation |

## Run it

```bash
# 1. Infrastructure
docker compose up -d postgres-auth postgres-account postgres-transaction redis kafka

# 2. Each service (separate terminals)
cd auth-service && mvn spring-boot:run
cd account-service && mvn spring-boot:run
cd transaction-service && mvn spring-boot:run
cd notification-service && mvn spring-boot:run
cd api-gateway && mvn spring-boot:run

# 3. Frontend
cd frontend && npm install && npm start
```

## Try it

```bash
# Register + login
curl -X POST localhost:8080/api/auth/register -H 'Content-Type: application/json' \
  -d '{"email":"me@test.com","password":"password123"}'
TOKEN=$(curl -s -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"me@test.com","password":"password123"}' | jq -r .accessToken)

# Create two accounts (owner comes from the JWT, not the body)
curl -X POST localhost:8080/api/accounts -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"initialDeposit":1000}'
curl -X POST localhost:8080/api/accounts -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"initialDeposit":0}'

# List my accounts
curl localhost:8080/api/accounts -H "Authorization: Bearer $TOKEN"

# Transfer (replace account numbers from responses above)
curl -X POST localhost:8080/api/transfers -H "Authorization: Bearer $TOKEN" \
  -H "Idempotency-Key: $(uuidgen)" -H 'Content-Type: application/json' \
  -d '{"fromAccount":"AAA","toAccount":"BBB","amount":250}'
```

## Roadmap
- [ ] WebSocket real-time transaction feed
- [ ] Refresh tokens + token blacklist in Redis
- [ ] Chart.js dashboard visualizations
- [ ] Integration tests with Testcontainers
- [ ] GitHub Actions CI + deploy to Railway
