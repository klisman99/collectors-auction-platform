# Reproducible MVP proof

Issue [#44](https://github.com/klisman99/collectors-auction-platform/issues/44) is the
acceptance gate for M6. A configured test is not a successful measurement: the gate
requires all correctness assertions and both latency thresholds to pass in the same run.

## Run from a clean checkout

For ordinary development, copy `.env.example` to `.env`, supply the bootstrap
administrator credentials, and run `docker compose up --build`. The application is
at <http://localhost:8080>, with private object storage in MinIO and email in Mailpit.
Add `OTEL_TRACING_ENABLED=true docker compose --profile observability up --build`
for Prometheus, Grafana and Tempo.

For the complete automated acceptance sequence, use the toolchain already pinned
in the repository, Docker Compose, and Python for backup manifest verification:

```bash
node scripts/check-docs.mjs
./mvnw --batch-mode --no-transfer-progress spotless:check
./mvnw --batch-mode --no-transfer-progress verify
corepack pnpm --dir web install --frozen-lockfile
corepack pnpm --dir web run ci
corepack pnpm --dir web exec playwright install --with-deps chromium
bash scripts/prove-mvp.sh
```

The script builds both images and launches a fresh `collectors-proof-*` Compose
project, using ports 18080 (web), 18025 (Mailpit), 15432 (PostgreSQL), 19000/19001
(MinIO), 19090 (Prometheus), 13000 (Grafana), and 13200/14318 (Tempo/OTLP).
These ports must be free. It supplies disposable credentials independently of `.env`,
regenerates and compares the OpenAPI client, runs browser journeys and operational
experiments, verifies Prometheus scraping, and removes only that project's volumes.
It never resets the ordinary development database. Set `KEEP_PROOF_STACK=1` to retain
an experiment for investigation; the command prints its project name. Remove that
specific project with `docker compose -p <printed-project> --profile observability down --volumes`.

## Evidence and acceptance matrix

| Acceptance area | Executable evidence | Required result |
| --- | --- | --- |
| Registration, verification, safe upload, moderation, scheduling | `web/e2e/identity.spec.ts`, `auction-scheduling.spec.ts`, `web/proof/journey.spec.ts` | Real Mailpit token consumed and real image normalized; approved item scheduled through HTTP |
| Concurrent bidding and late extension | `BiddingConcurrencyIntegrationTests`, `BiddingLateBidExtensionHttpIntegrationTests`, proof burst | PostgreSQL order is contiguous; one accepted equal-price contender; immutable bids; two-minute extension persists after disqualification |
| Suspension and authorization | `AccountSuspensionHttpIntegrationTests`, `OperationalAccountHttpIntegrationTests`, proof journey | Sessions revoked; operational roles cannot trade; public reasons survive; private notes remain private |
| Closing and reserve | `AuctionLifecycleIntegrationTests`, `AuctionHttpIntegrationTests`, proof recovery | Interrupted closing resumes; one outcome and one sale; seller acceptance works; expired decision becomes unsold |
| Completed and failed settlement | `SettlementHttpIntegrationTests`, `SettlementConcurrencyIntegrationTests`, `web/e2e/settlement.spec.ts`, proof recovery | Completed item archived; expired payment releases item; no duplicate sale after restart |
| Lost acknowledgement | proof recovery | Discard initial HTTP result, kill process, replay the same command key; original sequence returned and one durable bid remains |
| Scheduling and deadline downtime | proof recovery | Kill before a persisted deadline becomes due, advance fixture deadline, restart; production reconciliation performs the transition exactly once |
| Kill during closing | proof recovery | Hold the eligible-bid read with a PostgreSQL table lock; observe committed `CLOSING`; SIGKILL; release lock; restart to one sold outcome |
| Durable listener recovery | proof email outage | Stop SMTP; registration remains committed with incomplete publication; SIGKILL; restore SMTP and restart; publication completes and token works once |
| Security and privacy | `SessionCookieIntegrationTests`, `IdentityHttpIntegrationTests`, `AuditHistoryHttpIntegrationTests`, `ReadOnlyStompChannelInterceptorTests`, proof negatives | Session/CSRF, role matrix, rate limits, private media, pseudonyms and reserve privacy hold; proof passwords and token absent from backend logs |
| Module boundaries and migrations | `ArchitectureTests`, `PostgreSqlMigrationIntegrationTests` | Modulith verification and PostgreSQL migrations pass |
| Operations | provisioned dashboard, proof Prometheus query, [runbook](runbook.md) | Scrape target up, useful HTTP/JVM/connection signals, trace correlation, recovery and postmortem procedure |
| Reproducibility | `.github/workflows/ci.yml` | All layers, generated contract comparison and image builds pass; tracked checkout remains unchanged |

Test class names refer to `src/test/java/io/github/klisman99/collectorsauctionplatform/`.
The browser and operational suites complement each other: the existing settlement
browser test seeds a sale; the operational journey creates that sale from a real auction.

## Measurement contract

The burst authenticates 100 distinct regular accounts before measurement and submits
one simultaneous HTTP command per account through the same-origin NGINX shell to a
single auction. All commands offer the same next amount. Exactly one must return 201;
the other 99 must return the expected 422 below-minimum rejection. Rejections are part of the
100-command response population, never silently discarded. An initial accepted bid
proves that the live subscription works. A fixed 100-command warmup then exercises
the contention path; a second independent 100-command burst supplies the latency
gate. Both bursts require one acceptance, 99 expected rejections, contiguous durable
order and live delivery. The report retains both populations and every response time.
The warmup is never repeated until it passes. This measures a warmed service;
cold-start latency is recorded separately and is an explicit limitation if it exceeds
the threshold.

`p95ResponseMs` is the nearest-rank 95th percentile of the 100 complete response times
measured using the client's monotonic clock. It must be **below 500 ms**.
`requestToLiveEventMs` measures burst release to browser receipt of the committed
winning STOMP event. It includes network and transaction time and is a conservative
upper bound on commit-to-receipt latency; it must be **below 1000 ms**. The host and
browser share a clock. Missing events, unexpected responses, or zero accepted bids
fail the run. There are no performance retries and no threshold overrides.

This is a hot-auction contention burst, not a sustained throughput claim. Registration
and login are excluded from bidding latency. Load identities are seeded from a verified
fixture account; login still uses real sessions. Time compression updates only durable
deadlines on test fixtures. No endpoint bypasses production policy and no test writes
synthetic bids, final outcomes, or sales. A client-discard experiment proves replay after
an ambiguous outcome; it does not claim to intercept a TCP packet at the commit boundary.
External SMTP is at-least-once: a crash after sending but before completion recording can
repeat an email. Domain outcomes and audit facts must remain unique.

The automated cold backup/restore check also preserves all private object bytes and
compares bid, sale, audit and media counts in a second disposable project. See the
[completed recovery exercise postmortem](postmortem.md).

## Retained artifacts and status

`artifacts/proof/environment.txt` identifies source revision, working-tree differences,
host kernel/architecture, Docker capacity, and Node runtime. `results.json` contains
individual experiment results and the `100-bidder-cold-warmup` and
`100-bidder-measurement` attachments, including failed thresholds.
`build.log` preserves build/startup diagnostics without backend request logs.
`readiness.json`, `prometheus.json`, and container status support
operational diagnosis. Raw database contents, credentials, auth state, and proof HTTP
traces are not published. Browser traces are for local diagnosis only.

See [verification record](verification.md) for the measured status and limitations.
Do not mark M6 complete when a test is skipped, infrastructure prevents an experiment,
or a latency threshold fails. A local result is not evidence that GitHub Actions ran.

## Verified additions

On 2026-09-19, the Node 24 compatible type definitions were verified as `@types/node`
24.13.6 using the [official npm registry](https://registry.npmjs.org/@types/node).
The artifact upload action was verified as 7.0.1 from its
[official releases](https://github.com/actions/upload-artifact/releases/tag/v7.0.1).
The existing runtime and application dependencies are unchanged.
