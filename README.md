# Fraud Detection Microservice

A production-grade, event-driven fraud detection platform built with **Java 21**, **Spring Boot 4**, **Spring Cloud**, **Apache Kafka**, and **Spring AI**. The system processes financial transactions in real time, scores them for fraud, delivers notifications, and provides AI-powered explanations via a local LLM — all observable end-to-end with OpenTelemetry, Jaeger, Prometheus, and Grafana.

---

## Architecture

```
                                   ┌──────────────────────┐
                  Client ─────────▶│     API Gateway      │
                                   │      port 8060        │
                                   └──────────┬───────────┘
                                              │  routes via Eureka
                 ┌────────────────────────────┼──────────────────────────┐
                 │                            │                          │
                 ▼                            ▼                          ▼
   ┌─────────────────────┐     ┌──────────────────────┐    ┌─────────────────────┐
   │  Transaction Service │     │ Fraud Analysis Svc   │    │ Notification Service│
   │      port 8080       │     │      port 8081        │    │      port 8082       │
   └──────────┬──────────┘     └──────────┬───────────┘    └──────────┬──────────┘
              │                           │                            │
              ▼                           ▼                            ▼
       PostgreSQL :5432           PostgreSQL :5433             PostgreSQL :5434
       (transactions)             (fraud_analysis)             (notifications)

                 │                            │                          │
                 └────────────────────────────┼──────────────────────────┘
                                              │
                                     ┌────────┴────────┐
                                     │      Kafka       │
                                     │    port 9092     │
                                     └────────┬────────┘
                                              │
                                              ▼
                                 ┌─────────────────────────┐
                                 │    AI Insight Service    │
                                 │        port 8083          │
                                 │  Spring AI + pgvector    │
                                 └──────────┬──────────────┘
                                            │
                          ┌─────────────────┴──────────────────┐
                          │                                     │
                          ▼                                     ▼
                 PostgreSQL+pgvector :5435              Ollama :11434
                    (ai_insights DB)              (llama3.2 + nomic-embed-text)


Platform Services:
   ┌──────────────────────┐        ┌──────────────────────┐
   │    Eureka Server     │        │    Config Server      │
   │      port 8761        │        │      port 8888         │
   └──────────────────────┘        └──────────────────────┘

Observability Stack:
   ┌─────────────────┐      ┌──────────────┐    ┌───────────────┐    ┌──────────────┐
   │ OTel Collector  │─────▶│    Jaeger    │    │  Prometheus   │───▶│   Grafana    │
   │ 4317/4318/8889  │      │  port 16686  │    │   port 9090    │    │  port 3000   │
   └─────────────────┘      └──────────────┘    └───────────────┘    └──────────────┘
```

---

## Event Flow

```
  POST /api/v1/transactions
          │
          ▼
  Transaction Service
  ┌─────────────────────────────┐
  │  Save transaction + outbox  │  ← single DB transaction (Outbox Pattern)
  │  event in one TX            │
  └──────────────┬──────────────┘
                 │ OutboxPoller (every 10s)
                 ▼
         topic: transaction-events
                 │
     ┌───────────┴──────────────┐
     │                          │
     ▼                          ▼
Fraud Analysis Svc         AI Insight Svc
(scores transaction)       (stores snapshot)
     │
     │ publishes
     ▼
  topic: fraud-results
     │
     ├──────────────────────────────────┐
     ▼                                  ▼
Transaction Service            Notification Service
(updates tx status)            (sends email/SMS alert)
                                        │
                                        ▼
                               topic: notification.completed
```

---

## Services

### Platform Services

| Service | Port | Role |
|---------|------|------|
| **Eureka Server** | 8761 | Service discovery — all services register here for dynamic lookup |
| **Config Server** | 8888 | Centralized config via Spring Cloud Config (native backend from `classpath:/configs/`) |
| **API Gateway** | 8060 | Single entry point; load-balanced routing via Eureka |

**API Gateway routes:**
- `/api/v1/transactions/**` → `transaction-service`
- `/api/v1/fraud/**` → `fraud-analysis-service`
- `/api/v1/notifications/**` → `notification-service`

---

### Business Services

#### Transaction Service — port 8080

Handles transaction creation and full lifecycle management. Guarantees reliable event publishing via the **Transactional Outbox Pattern**: transaction data and the outbox event are written in the same database transaction, eliminating any possibility of a lost event.

| Component | Responsibility |
|-----------|---------------|
| `TransactionController` | REST API — create and query transactions |
| `OutboxPoller` | Polls `outbox_events` every 10s, publishes to Kafka, marks as published |
| `FraudResultConsumer` | Consumes `fraud-results`, updates transaction status |
| `SagaTimeoutJob` | Recovers stale `PENDING` transactions after 30s timeout |

---

#### Fraud Analysis Service — port 8081

Consumes transaction events, runs fraud rules, and publishes scored results. Uses a `processed_events` table (keyed on `topic-partition-offset`) to ensure **idempotent processing** — safe against Kafka rebalances and duplicate deliveries.

| Component | Responsibility |
|-----------|---------------|
| `TransactionEventConsumer` | Consumes `transaction-events` with idempotency check |
| `FraudAnalysisServiceImpl` | Fraud scoring logic (amount thresholds, country rules, etc.) |
| `FraudResultPublisher` | Publishes scored results to `fraud-results` topic |

---

#### Notification Service — port 8082

Listens to fraud results and dispatches multi-channel notifications. Email delivery is protected by a **Resilience4j circuit breaker and retry**. Permanently failed notifications are persisted to a `failed_notifications` table and replayed by a scheduled job with exponential backoff — up to 10 attempts before entering `DEAD_LETTER` state.

| Component | Responsibility |
|-----------|---------------|
| `FraudResultConsumer` | Idempotent consumer for `fraud-results` |
| `NotificationManager` | Routes to enabled channels (email, SMS) |
| `EmailService` | SMTP via Gmail — `@CircuitBreaker` (sliding window 10, threshold 50%, 30s wait) + `@Retry` (3 attempts, 2s initial, ×2 backoff) |
| `FailedNotificationRetryJob` | Scheduled retry: 60s → 7200s exponential backoff, max 10 attempts → `DEAD_LETTER` |
| `NotificationCompletedPublisher` | Publishes `SENT` / `SKIPPED` / `DEFERRED` status events |

---

#### AI Insight Service — port 8083

Provides AI-powered explanations for flagged transactions using **Retrieval-Augmented Generation (RAG)**. On startup, fraud rules and historical case analyses are embedded and stored in **pgvector**. When queried, the service retrieves the most relevant context and generates a structured explanation via a local LLM (Ollama).

| Component | Responsibility |
|-----------|---------------|
| `TransactionEventConsumer` | Consumes `transaction-events`, maintains a `FraudSnapshot` per transaction |
| `FraudKnowledgeIngestionService` | Embeds fraud rules and past cases into pgvector on startup |
| `FraudExplanationService` | Similarity search + LLM call → structured explanation |
| `InsightController` | `GET /api/v1/insights/transaction/{id}` |

**LLM stack:** Ollama running locally with `llama3.2` (chat) and `nomic-embed-text` (768-dim embeddings). The vector store uses HNSW index with cosine distance.

---

## Tech Stack

| Technology | Version | Purpose |
|------------|---------|---------|
| Java | 21 | Language — Virtual Threads enabled across all services |
| Spring Boot | 4.0.5 | Application framework |
| Spring Cloud | 2025.1.1 | Gateway, Config Server, Eureka |
| Spring AI | 2.0.0-M3 | RAG, Chat client, pgvector store |
| Apache Kafka | 3.7.0 (KRaft) | Event streaming — no ZooKeeper |
| PostgreSQL | 16 | Persistence — dedicated database per service |
| pgvector | pg16 | Vector similarity search (AI Insight Service) |
| Flyway | — | Automatic database migrations |
| Resilience4j | — | Circuit breaker + retry (Notification Service) |
| OpenTelemetry | Java agent | Distributed tracing + metrics export (OTLP) |
| Jaeger | 1.54 | Trace storage and UI |
| Prometheus | 2.50.0 | Metrics scraping and storage |
| Grafana | 10.3.1 | Dashboards (auto-provisioned datasources) |
| Ollama | local | LLM inference — `llama3.2`, `nomic-embed-text` |
| Maven | — | Build tool (wrapper included) |

---

## Getting Started

### Prerequisites

- **Java 21+**
- **Docker & Docker Compose**
- **Ollama** (for AI Insight Service) — [ollama.com](https://ollama.com)

### 1. Pull Required Ollama Models

```bash
ollama pull llama3.2
ollama pull nomic-embed-text
```

> If you want to skip the AI service, see [Running Without AI](#running-without-ai).

### 2. Start Infrastructure

```bash
docker-compose up -d
```

This starts:

| Container | Port(s) | Notes |
|-----------|---------|-------|
| PostgreSQL (transactions) | 5432 | |
| PostgreSQL (fraud_analysis) | 5433 | |
| PostgreSQL (notifications) | 5434 | |
| PostgreSQL + pgvector (ai_insights) | 5435 | |
| Kafka (KRaft mode) | 9092 | No ZooKeeper |
| OTel Collector | 4317, 4318, 8889 | OTLP gRPC / HTTP / Prometheus exporter |
| Jaeger | 16686 | Trace UI |
| Prometheus | 9090 | Metrics UI |
| Grafana | 3000 | Dashboards (admin/admin) |

### 3. Start Platform Services (in order)

```bash
# Terminal 1 — Eureka Server (service discovery must be first)
cd eureka-server && ./mvnw spring-boot:run

# Terminal 2 — Config Server
cd config-server && ./mvnw spring-boot:run

# Terminal 3 — API Gateway
cd api-gateway && ./mvnw spring-boot:run
```

### 4. Start Business Services

Run each in a separate terminal. Attach the OpenTelemetry agent to get distributed traces in Jaeger:

```bash
# Transaction Service
export OTEL_SERVICE_NAME=transaction-service
export OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4317
export OTEL_METRICS_EXPORTER=none
cd transaction-service && ./mvnw spring-boot:run \
  -Dspring-boot.run.jvmArguments="-javaagent:../opentelemetry-javaagent.jar"

# Fraud Analysis Service
export OTEL_SERVICE_NAME=fraud-analysis-service
cd fraud-analysis-service && ./mvnw spring-boot:run \
  -Dspring-boot.run.jvmArguments="-javaagent:../opentelemetry-javaagent.jar"

# Notification Service
export OTEL_SERVICE_NAME=notification-service
cd notification-service && ./mvnw spring-boot:run \
  -Dspring-boot.run.jvmArguments="-javaagent:../opentelemetry-javaagent.jar"

# AI Insight Service
export OTEL_SERVICE_NAME=ai-insight-service
cd ai-insight-service && ./mvnw spring-boot:run \
  -Dspring-boot.run.jvmArguments="-javaagent:../opentelemetry-javaagent.jar"
```

> Without `OTEL_*` env vars, services still start and expose metrics on `/actuator/prometheus` — traces just won't appear in Jaeger.

### 5. Configure Notification Email (Optional)

To receive email alerts for flagged transactions:

```bash
export MAIL_USERNAME=your-email@gmail.com
export MAIL_PASSWORD=your-gmail-app-password   # not your account password
export NOTIFICATION_RECIPIENT=recipient@example.com
```

### Running Without AI

To skip the AI Insight Service entirely, simply don't start it. All other services are fully independent. You can also skip the `postgres-ai` container — remove it from `docker-compose.yml` or start only the containers you need:

```bash
docker-compose up -d postgres-transaction postgres-fraud postgres-notification kafka otel-collector jaeger prometheus grafana
```

---

## API Reference

All requests go through the API Gateway on port `8060`.

### Request Fields

| Field | Type | Required | Validation |
|-------|------|----------|------------|
| `accountId` | Long | yes | non-null |
| `amount` | BigDecimal | yes | positive |
| `currency` | String | yes | exactly 3 chars (ISO 4217) |
| `merchantName` | String | yes | non-blank |
| `country` | String | no | exactly 2 chars (ISO 3166) |

### Fraud Rules

| Rule | Condition | Score |
|------|-----------|-------|
| High amount | `amount > 10,000` | +70 |
| High-risk country | `country` in `XX`, `YY`, `ZZ` | +80 |

Score ≥ 70 → `FLAGGED`, otherwise → `APPROVED`.

---

### Create a Normal Transaction

```bash
curl -X POST http://localhost:8060/api/v1/transactions \
  -H "Content-Type: application/json" \
  -d '{
    "accountId": 1001,
    "amount": 249.99,
    "currency": "USD",
    "merchantName": "Starbucks Istanbul",
    "country": "TR"
  }'
```

**Response** `201 Created`:
```json
{
  "id": 1,
  "accountId": 1001,
  "amount": 249.9900,
  "currency": "USD",
  "merchantName": "Starbucks Istanbul",
  "country": "TR",
  "createdAt": "2026-04-27T19:00:00Z",
  "status": "PENDING"
}
```

After ~1–2 seconds the `OutboxPoller` publishes the event, the Fraud Analysis Service scores it, and the status updates to `APPROVED`.

---

### Create a Flagged Transaction (high amount)

```bash
curl -X POST http://localhost:8060/api/v1/transactions \
  -H "Content-Type: application/json" \
  -d '{
    "accountId": 2002,
    "amount": 15000.00,
    "currency": "USD",
    "merchantName": "Unknown Electronics Store",
    "country": "DE"
  }'
```

**Response** `201 Created` (status starts as `PENDING`, updates to `FLAGGED` after scoring):
```json
{
  "id": 2,
  "accountId": 2002,
  "amount": 15000.0000,
  "currency": "USD",
  "merchantName": "Unknown Electronics Store",
  "country": "DE",
  "createdAt": "2026-04-27T19:00:05Z",
  "status": "PENDING"
}
```

---

### Create a Flagged Transaction (high-risk country)

```bash
curl -X POST http://localhost:8060/api/v1/transactions \
  -H "Content-Type: application/json" \
  -d '{
    "accountId": 3003,
    "amount": 500.00,
    "currency": "EUR",
    "merchantName": "Crypto Exchange Ltd",
    "country": "XX"
  }'
```

---

### Get Transaction by ID

```bash
curl http://localhost:8060/api/v1/transactions/1
```

**Response** `200 OK`:
```json
{
  "id": 1,
  "accountId": 1001,
  "amount": 249.9900,
  "currency": "USD",
  "merchantName": "Starbucks Istanbul",
  "country": "TR",
  "createdAt": "2026-04-27T19:00:00Z",
  "status": "APPROVED"
}
```

---

### List All Transactions

```bash
curl http://localhost:8060/api/v1/transactions
```

---

### Get AI Explanation for a Flagged Transaction

Direct call to the AI Insight Service (not routed via Gateway):

```bash
curl http://localhost:8083/api/v1/insights/transaction/2
```

**Response** `200 OK` (once fraud result is received):
```json
{
  "transactionId": 2,
  "body": {
    "summary": "Transaction flagged due to amount exceeding the high-value threshold of $10,000.",
    "rulesTriggered": [
      {
        "id": "RULE-001",
        "name": "High-value transaction",
        "why": "Amount of $15,000 exceeds the $10,000 single-transaction threshold"
      }
    ],
    "riskAssessment": "High risk — amount is 50% above threshold with an unrecognized merchant.",
    "recommendedAction": "Escalate to fraud team for manual review per PROC-001."
  },
  "sourceIds": ["RULE-001", "PROC-001"]
}
```

**Response** `202 Accepted` (fraud result not yet received — retry after ~2s):
```json
{
  "transactionId": 2,
  "message": "Fraud result not yet received, try again"
}
```

---

## Kafka Topics

| Topic | Producer | Consumers |
|-------|----------|-----------|
| `transaction-events` | Transaction Service (OutboxPoller) | Fraud Analysis Service, AI Insight Service |
| `fraud-results` | Fraud Analysis Service | Transaction Service, Notification Service |
| `notification.completed` | Notification Service | — |

---

## Observability

| Tool | URL | What to look for |
|------|-----|-----------------|
| **Jaeger** | http://localhost:16686 | End-to-end traces: `API Gateway → Transaction Service → Kafka → Fraud Analysis → Notification Service` |
| **Prometheus** | http://localhost:9090 | Raw metrics, PromQL queries |
| **Grafana** | http://localhost:3000 | Dashboards — Prometheus and Jaeger are auto-provisioned as datasources |

**Useful Prometheus queries:**

```promql
# Circuit breaker state (0=CLOSED, 1=OPEN, 2=HALF_OPEN)
resilience4j_circuitbreaker_state{name="emailService"}

# Failed notification retries over time
rate(fraud_notifications_retry_total[5m])

# Transaction processing latency P99
histogram_quantile(0.99, rate(http_server_requests_seconds_bucket{uri="/api/v1/transactions"}[5m]))
```

**Actuator endpoints exposed on every service:**
`/actuator/health`, `/actuator/prometheus`, `/actuator/metrics`, `/actuator/circuitbreakers`, `/actuator/circuitbreakerevents`, `/actuator/retries`

---

## Key Patterns

| Pattern | Where | Description |
|---------|-------|-------------|
| **Transactional Outbox** | Transaction Service | Transaction + `outbox_events` written in one DB transaction; `OutboxPoller` publishes to Kafka and marks rows as published |
| **Idempotent Consumers** | Fraud Analysis, Notification, AI Insight | `processed_events` table keyed on `topic-partition-offset` prevents duplicate processing on Kafka rebalances |
| **Choreographed Saga** | All business services | Services react to events independently; no central orchestrator |
| **Saga Timeout** | Transaction Service | `SagaTimeoutJob` marks `PENDING` transactions as `REVIEW_NEEDED` after 30s if no fraud result arrives |
| **Circuit Breaker + Retry** | Notification Service | Resilience4j on `EmailService`: sliding window 10, 50% failure threshold, 30s wait in OPEN; 3 retries with 2× exponential backoff |
| **DB-backed Dead Letter Queue** | Notification Service | Failed notifications persist in `failed_notifications`; `FailedNotificationRetryJob` replays with exponential backoff (60s → 7200s) up to 10 attempts, then `DEAD_LETTER` |
| **Database per Service** | All services | Each service owns its schema and PostgreSQL instance — no shared database |
| **RAG (Retrieval-Augmented Generation)** | AI Insight Service | Fraud rules and past cases embedded into pgvector; similarity search provides grounded context to the LLM |
| **Virtual Threads** | All services | `spring.threads.virtual.enabled: true` — Java 21 virtual threads for high-concurrency I/O |
| **Centralized Configuration** | Config Server | Shared `application.yaml` + per-service `{name}.yaml` served by Spring Cloud Config |

---

## Project Structure

```
fraud-detection-microservice/
├── docker-compose.yml
├── opentelemetry-javaagent.jar
├── config/
│   ├── otel-collector-config.yml
│   ├── prometheus.yml
│   └── grafana-datasources.yml
├── eureka-server/
├── config-server/
│   └── src/main/resources/configs/
│       ├── application.yaml                # Shared: Kafka, JPA, Flyway, Eureka, Actuator
│       ├── transaction-service.yaml
│       ├── fraud-analysis-service.yaml
│       ├── notification-service.yaml       # + Resilience4j, SMTP, retry schedule
│       └── ai-insight-service.yaml         # + Spring AI, Ollama, pgvector
├── api-gateway/
├── transaction-service/
├── fraud-analysis-service/
├── notification-service/
└── ai-insight-service/
```
