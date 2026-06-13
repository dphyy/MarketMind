# MarketMind — Claude Code Instructions

Read this file and `marketmind_agent_brief.md` before doing anything.

---

## What this project is

MarketMind is an autonomous pricing agent for Shopee/Lazada sellers.
Every 15 minutes it scrapes competitors, reads social sentiment, and makes
guardrailed pricing + ad-bid decisions. The seller sets hard limits; the AI
can never break them.

Full spec, data contracts, and demo scenario: `marketmind_agent_brief.md`

---

## Project layout

```
marketmind/                   ← you are here (project root)
├── CLAUDE.md                 ← this file
├── marketmind_agent_brief.md ← full spec, read it
├── docker-compose.yml        ← Postgres 15
├── .env.example              ← copy to .env for live sponsor keys
└── backend/                  ← Spring Boot 3.3 / Java 21
    ├── pom.xml
    └── src/main/
        ├── java/com/marketmind/
        │   ├── config/       CORS (WebConfig.java)
        │   ├── controller/   DashboardController.java — GET endpoints only, WORKING
        │   ├── dto/          ProductOverview.java
        │   ├── model/        5 entities + 2 enums — DO NOT CHANGE
        │   └── repository/   5 repositories — DO NOT CHANGE
        └── resources/
            ├── application.yml
            └── db/
                ├── schema.sql   drops + recreates tables on every boot
                └── seed.sql     3 SKUs + competitors + sentiment + action log
```

---

## What is already built (DO NOT touch these)

- Postgres schema and seed data (`db/schema.sql`, `db/seed.sql`)
- All 5 JPA entities: `Product`, `CompetitorSnapshot`, `SentimentEvent`, `ActionLog`, `GuardrailBlock`
- All 2 enums: `StrategyMode`, `ActionType`
- All 5 repositories
- `DashboardController` — GET endpoints at `/api/products`, `/api/dashboard/overview`,
  `/api/dashboard/competitors/{productId}`, `/api/actions`, `/api/guardrails/blocks`
- CORS config (allows `localhost:5173` and `localhost:3000`)

Build on this. Do not regenerate, rename, restructure, or re-create any of it.

---

## Build order — pick up from step 4

- [x] 1. Postgres + schema + seed
- [x] 2. Spring Boot scaffolded
- [x] 3. Repositories + GET endpoints
- [ ] 4. `GuardrailEngine` + JUnit tests
- [ ] 5. `SignalAggregatorService`, `SentimentService`, `KimiReasoningService`
- [ ] 6. `ActionExecutorService` (Daytona), `MorningBriefService`
- [ ] 7. `AgentCycleService` — full loop on mock data
- [ ] 8. `AgentController` (POST /api/agent/run/{productId} + /run-all), `MorningBriefController`
- [ ] 9. `BrightDataService`, `SenseNovaService` (both with fallbacks)
- [ ] 10. React frontend — 3 screens (Dashboard, MorningBrief, GuardrailLog)
- [ ] 11. Guaranteed demo reproducible end-to-end

---

## Hard rules — these override the brief where they conflict

### 1. Mock-first
Every external/sponsor call must have a seed-data fallback and must NEVER crash
the agent cycle. The whole app must run end-to-end with NO API keys set.
Log `[LIVE]` or `[MOCK]` for every external call.

### 2. Guardrail integrity (the core pitch)
`GuardrailEngine` is pure deterministic Java — zero AI.
- Pre-check: if stock <= reserveMin, FORCE mode = BUNKER regardless of anything.
- Post-check: validate Kimi's final proposed action against all rules.
- If pre-check forced a mode, post-check must RE-ENFORCE it on Kimi's output.
  Do not just inject it into the prompt and hope. The deterministic layer always wins.
- A guardrail block is a HARD BLOCK (do not silently clamp). Log to `guardrail_blocks`.

### 3. Velocity cap — one consistent reference price
Compute cumulative price change from the **day-open price** (first recorded price
today, falling back to `your_price` if no changes yet today).
This percentage is what gets checked against `max_daily_price_change_pct`.
The engine, seed data, and demo script must all agree on this number.

### 4. Kimi routing
```
if TOKENROUTER_BASE_URL is set → call Kimi via TokenRouter (drop-in base-url swap)
else if KIMI_API_KEY is set    → call Kimi directly (api.moonshot.cn/v1, model kimi-k2.6)
else                           → rule-based fallback (no AI)
```
Parameterise base-url and model in `application.yml`. Never hardcode them.

### 5. Sponsor integrations
Integrate these 5 genuinely (each behind a mock fallback):
- **Bright Data** — scrape competitor listings
- **SenseNova U1** — extract visual signals from listing images
- **Kimi K2.6** — strategy reasoning (JSON) + morning brief (prose)
- **TokenRouter** — route Kimi calls (drop-in base-url swap, no other changes)
- **Daytona** — run the EXECUTE step as a real isolated sandbox job; store `daytona_job_id`

Skip: Nosana, VideoDB. Do not integrate them.

Terminal 3: optional. If `TERMINAL3_API_KEY` is set, sign one executed action per
cycle and store the attestation in `action_log.daytona_job_id` (or a new column).
If not set, a clearly-marked stub comment is fine.

---

## The guaranteed demo (SKU-001 → HARVEST)

This scenario must reliably reproduce when "Run Agent Cycle" is clicked:

1. Bright Data scrape (or mock fallback): competitor AudioZone SG → `CRITICALLY_LOW` stock
2. SenseNova (or fallback): detects `clearance_banner` on listing image
3. Signal aggregator: high-confidence Harvest signal
4. Kimi (or fallback): recommends `HARVEST` + price $49.90 → $54.90
5. Guardrail post-check: $54.90 is within floor ($38) and ceiling ($65) → **ALLOWED**
6. Action logged: mode `HOLD → HARVEST`, price `49.90 → 54.90`
7. UI refreshes: mode badge flips green HARVEST, action appears in timeline with Kimi explanation

The blocked action (+25.9% over velocity cap, blocked at $62.80) must also be
reproducible from the seed data on the GuardrailLog screen.

---

## Tech stack

| Layer | Tech |
|---|---|
| Backend | Java 21, Spring Boot 3.3, Maven |
| Database | PostgreSQL 15 (Docker) |
| Frontend | React 18, Vite, Tailwind CSS, React Query |
| Strategy AI | Kimi K2.6 (OpenAI-compatible, via TokenRouter if key set) |
| Scraping | Bright Data Web Unlocker API |
| Visual AI | SenseNova U1 multimodal API |
| Sandbox | Daytona |

---

## Commands

```bash
# Start the database (from project root)
docker compose up -d

# Start the backend (from backend/)
cd backend && mvn spring-boot:run

# Start the frontend (from frontend/ — once scaffolded)
cd frontend && npm run dev
```

The backend runs on port 8080. The frontend runs on port 5173.
Each backend boot drops + reseeds all tables (clean demo state).

---

## Interaction policy

- Work autonomously. Make reasonable decisions without asking.
- Record every non-obvious decision in `CHANGES.md` in the project root.
- Stop to ask ONLY if:
  (a) a sponsor API key/endpoint is needed to proceed (use the mock path instead), or
  (b) an ambiguity would materially change the architecture.
- Do not ask for confirmation on routine file creation or standard library choices.
- Compile after each service. Run GuardrailEngine tests before moving on.
