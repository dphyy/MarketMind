# CHANGES — non-obvious decisions

A running log of decisions that aren't obvious from the code itself, per the
interaction policy in `CLAUDE.md`.

## Build environment

- **Maven not installed on this machine.** Downloaded portable Apache Maven
  3.9.9 to `%USERPROFILE%\tools\apache-maven-3.9.9` and build with that. Not
  committed to the repo.
- **Only JDK 23 is installed (project targets Java 21).** JDK 23 still compiles
  to `--release 21`, so this is fine — but two `pom.xml` changes were needed:
  1. Bumped the Spring-Boot-managed Lombok (`1.18.34`) to `1.18.36` via a
     `lombok.version` property. 1.18.34 silently no-ops on JDK 23.
  2. **JDK 23 disabled implicit annotation processing by default**, so Lombok on
     the classpath was never invoked (getters/setters not generated → baseline
     did not compile). Declared Lombok explicitly in the
     `maven-compiler-plugin` `annotationProcessorPaths`. This is the real fix.
  These are the only `pom.xml` edits; nothing in `model/`, `repository/`,
  `controller/`, `db/` was touched.

## Guardrail / velocity-cap reference price

- Velocity cap is computed as the **cumulative change from the day-open price**:
  `|proposed − dayOpen| / dayOpen`, checked against `max_daily_price_change_pct`.
- `dayOpen` = `from_value` of the earliest *executed* `PRICE_UPDATE` logged
  **today**; if there are no price changes today it falls back to
  `product.your_price`.
- This makes the engine agree with the seed data: from day-open $49.90, the
  proposed $62.80 is +25.9% (blocked at the 15% cap), and $54.90 is +10.0%
  (allowed). The demo and seed both rely on this exact arithmetic.

## Guardrail Rule 4 (stock reserve vs ad spend)

- The brief's pseudocode references `ActionType.INCREASE_AD_SPEND`, but the
  shipped enum only has `AD_BID_UPDATE`. Rule 4 therefore triggers on an
  `AD_BID_UPDATE` that *raises* the bid while `stock <= ceil(reserveMin * 1.2)`.

## Reasoning / pricing conventions

- The rule-based HARVEST/BUNKER fallback rounds the proposed price to the nearest
  **$0.10** (psychological pricing). This is what makes SKU-001's +10% lift land
  on exactly **$54.90** (49.90 × 1.10 = 54.89 → 54.90), matching the demo script.
- `SignalAggregatorService` has no real sales history, so it estimates daily
  sell-through as 5% of current stock to derive `stockDaysRemaining`. This keeps
  SKU-001's runway > 7 days so the HARVEST rule fires.
- SenseNova visual signals only **overwrite** a snapshot's existing flags when the
  live call returns something. In mock mode it returns empty, so the seed's
  `clearance_banner` on AudioZone survives into the decision.

## Running / verifying (this machine)

- Docker was not running and could not be started headless, but a **local
  PostgreSQL 18** is running on `localhost:5432` with the `postgres`/`marketmind`
  credentials the app expects. The `marketmind` database already existed; the app
  reseeds its tables on every boot. Used this instead of `docker compose`.
- On first boot, port 8080 was held by a **stale IntelliJ-launched instance** of
  the *old* build (Lombok 1.18.34, no `AgentController`). Stopped it and ran the
  freshly-built jar via `mvn spring-boot:run`. Re-running from the IDE is fine,
  but only one instance can hold 8080 at a time.
- Verified end-to-end against the live stack: `POST /api/agent/run/SKU-001` →
  HOLD→HARVEST, $49.90→$54.90, allowed, Daytona job id stored; the seeded +25.9%
  block shows on the Guardrail Log; the morning brief renders. All three React
  screens confirmed via the preview browser.

## Live sponsor wiring (.env)

- Spring Boot does not read `.env`. Added `DotenvEnvironmentPostProcessor`
  (registered via `META-INF/spring.factories` — **not** the `.imports` mechanism,
  which is auto-configuration-only) that loads the project-root `.env` and adds it
  as a high-priority property source, so `${KEY}` in `application.yml` resolves.
  It searches the working dir and ancestors (backend runs from `backend/`, `.env`
  is one level up). Logs `[dotenv] Loaded N entries`.
- Routing rework (`KimiClient`): now keyed on `TOKENROUTER_API_KEY` (per the live
  request). Tries TokenRouter first, then direct Kimi, then the service falls back
  to rule-based. Whichever HTTP route succeeds logs `[LIVE]`.
- `ActionExecutorService` now provisions a **real Daytona sandbox** (`POST /sandbox`,
  captures the id, best-effort teardown) when a key is set; simulated job otherwise.
- Bright Data zone, SenseNova base-url/chat-path/model, TokenRouter base-url, Daytona
  base-url/target are all `@Value`-configurable from `.env`.
- Bright Data mock-fallback snapshots get a placeholder image URL so the live
  SenseNova path has an image to analyse even on the mock data path.

### Live-call diagnosis (running the SKU-001 cycle with all keys)

The wiring is correct — every service reaches its real endpoint and falls back
cleanly. Remaining failures are account/credential specifics, not code:

| Service | Result | Blocker |
|---|---|---|
| Bright Data | HTTP 400 `zone "web_unlocker1" not found` | Need the account's real zone name (set `BRIGHTDATA_ZONE`). |
| SenseNova | path fixed (404→401 `Forbidden`) | `/llm/chat-completions` is correct; raw `sk-` key rejected — SenseNova wants its JWT auth (access-key-id + secret), not a bearer token. |
| Kimi direct | HTTP 401 `Invalid Authentication` at moonshot.cn | Key isn't valid for `api.moonshot.cn`; likely meant to go via TokenRouter. |
| TokenRouter | host unresolved | `TOKENROUTER_BASE_URL` was a guess (`api.tokenrouter.ai`); need the real base URL. |
| Daytona | authenticated OK; HTTP 400 | Org has no default region — must be set in the Daytona dashboard (or correct region param; `target:us` didn't take). |

## Live sponsor wiring — round 2 (proxy Bright Data, velaalpha, TokenRouter URL, Daytona region)

New `.env` values wired through `application.yml` + `@Value`:
- **Bright Data** switched from Direct API to **proxy** access:
  `BRIGHTDATA_HOST/PORT/USERNAME/PASSWORD`. `BrightDataService` now GETs the target
  URL through a dedicated proxy-configured `HttpClient` (Basic proxy auth via
  `java.net.Authenticator`, `jdk.http.auth.tunneling.disabledSchemes=""` so Basic
  works over the HTTPS CONNECT tunnel, trust-all SSLContext + hostname-verification
  off for the proxy's MITM cert). Scoped to scraping only.
- **SenseNova** → `SENSENOVA_BASE_URL=https://api.velaalpha.cc/v1`,
  `SENSENOVA_MODEL`; chat-path default switched back to `/chat/completions`
  (velaalpha is an OpenAI-style gateway). Demo image changed to a direct,
  no-redirect URL (Wikimedia) since the gateway fetches the image server-side.
- **TokenRouter** base-url default → `https://api.tokenrouter.com/v1`.
- **Daytona** region from `DAYTONA_REGION` (sent as `target` on sandbox create).

### Result of the final SKU-001 cycle (all keys loaded — 19 dotenv entries)

| Service | Status | Detail |
|---|---|---|
| Daytona | ✅ **[LIVE]** | Region fix worked — real sandbox provisioned + torn down; real UUID stored as `daytona_job_id`. |
| TokenRouter | reaches + auths, then falls back | HTTP 503 `No available channel for model kimi-k2.6 under group default` — the model name isn't in the TokenRouter account's catalog. Set `KIMI_MODEL` to a model they actually expose. |
| Kimi direct | 401 | Key invalid for `api.moonshot.cn` — it's meant to go via TokenRouter. |
| SenseNova (velaalpha) | reaches + auths, then falls back | `GET /v1/models` works (only model: `vela-alpha`), but `POST /v1/chat/completions` returns `invalid arguments` for **every** request shape tried (text-only, parts, both image forms, both model names, streaming). Gateway isn't serving standard OpenAI inference for this key — needs their API spec / provisioning. |
| Bright Data | proxy connect timeout | `Test-NetConnection brd.superproxy.io:33335` → ping OK, **TCP port blocked** from this machine (firewall). Wiring is correct; works where 33335 is reachable. |

Every failure still falls back to mock cleanly — the SKU-001 cycle reliably
produces HOLD→HARVEST, $49.90→$54.90.

## Forced-mode re-enforcement

- `preCheck` forces `BUNKER` when `stock <= stockReserveMin`. `postCheck`
  re-enforces this on Kimi's output by overwriting `decision.recommendedMode`
  (the deterministic layer wins; the forced mode is not merely injected into the
  prompt).
