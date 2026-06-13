# MarketMind

Autonomous merchant intelligence agent. Monitors competitor listings and social
sentiment, then makes guardrailed pricing and ad-bid decisions for an online seller.

This is the **foundation** (build-order steps 1–3): database, seed data, Spring Boot
skeleton, and read-only REST endpoints. No AI or sponsor calls yet — everything runs
on seed data so the data path is provable before anything live is wired in.

## Run it

```bash
# 1. Postgres up
docker compose up -d

# 2. Backend (from backend/)
cd backend
./mvnw spring-boot:run        # or: mvn spring-boot:run
```

On boot, Hibernate is disabled for DDL — `db/schema.sql` drops + recreates the tables
and `db/seed.sql` loads mock data, every time. So each restart is a clean demo state.

## Verify the data path

```bash
curl http://localhost:8080/api/products
curl http://localhost:8080/api/dashboard/overview
curl http://localhost:8080/api/dashboard/competitors/SKU-001
curl http://localhost:8080/api/actions
curl http://localhost:8080/api/guardrails/blocks
```

`overview` should return 3 products, each with its latest sentiment and competitor
snapshots. `actions` should return the 5 pre-baked overnight entries (one blocked).

## What's next

- `GuardrailEngine` — deterministic pre/post checks (pure logic, unit tested)
- `KimiReasoningService` — strategy reasoning returning structured JSON
- `AgentCycleService` — wire the full loop on mock data
- `POST /api/agent/run/{productId}` — run one cycle end to end
- Then live sponsor APIs, one at a time, each with the mock as fallback

## Layout

```
marketmind/
├── docker-compose.yml        Postgres 15
├── .env.example              sponsor keys (copy to .env when ready)
└── backend/
    ├── pom.xml
    └── src/main/
        ├── java/com/marketmind/
        │   ├── MarketMindApplication.java
        │   ├── config/        CORS
        │   ├── controller/    read-only GET endpoints
        │   ├── dto/           response shapes
        │   ├── model/         JPA entities + enums
        │   └── repository/    Spring Data repositories
        └── resources/
            ├── application.yml
            └── db/            schema.sql + seed.sql
```
