# Recovery exercise postmortem

Exercise dates: 2026-09-19–22, America/Sao_Paulo. This was a deliberate failure injection
against disposable `collectors-proof-*` projects. No development data was targeted.
The source baseline was `e77c5c3ef4176a26276c89436181b9c425e2863f` plus issue #44 changes.
Final run timestamps and auction identifiers are retained in the `recovery-postmortem`
attachment in the proof report; the [verification record](verification.md) records its result.

## Impact and detection

Stopping Mailpit prevented new verification email delivery, while registration still
returned its accepted response. The event registry retained incomplete publications.
The test detected the backlog through a read-only count and then killed the backend.
After restoring Mailpit and restarting the process, the original verification token
arrived and could be consumed exactly once; the incomplete-publication count returned
to zero. Domain state survived independently of the failed email side effect.

The closing exercise held the eligible-bid table read in PostgreSQL. The auction
committed its `CLOSING` claim but could not finish selecting the winner. A read-only
state query detected that condition before SIGKILL. Releasing the test lock and
restarting the backend completed the same auction with one sale. Replaying a lost
bid acknowledgement returned the original sequence, and repeated restarts did not
duplicate the seller-decision expiry fact or the sale.

The initial diagnostic runs also exposed two observability defects: Prometheus lacked
permission to scrape its endpoint, and Tempo's HTTP receiver listened on container
loopback. The latter produced backend `Failed to export spans` errors and an empty
Tempo trace search. The receiver log showed `endpoint=127.0.0.1:4318`. The fixes permit
private-network scraping (blocked at the public proxy) and bind Tempo to its container
network. The proof now checks actual scrape success and retrieval of a request's exact
`X-Trace-Id`, so configuration files alone cannot satisfy this gate.

## Bid contention diagnosis

The initial isolated burst recorded p95 of 722.80 ms, exceeding the unchanged 500 ms
limit. The original command path performed a new-entity lookup before storing each
attempt, reread eligible history to calculate an already-maintained minimum, and eagerly
loaded auction image bytes and timeline entries while holding the auction lock.

The final path generates attempt identifiers on persistence, obtains the minimum from
the locked auction projection, and uses a fetch graph that omits display collections.
A PostgreSQL regression test first failed with one media collection loaded for a rejected
bid, then passed with zero media and timeline collections loaded. A separate regression
checks that disqualifying all earlier bids allows the opening amount again without
resetting the durable sequence. These changes preserve locking and atomic persistence.
The final measured result is in the [verification record](verification.md).

The first complete CI measurement subsequently reached 508.85 ms p95 and failed
the unchanged 500 ms gate. The per-bidder rate-history query still ran while holding
the shared auction lock. A deterministic PostgreSQL test paused that query and showed
another bidder unable to proceed. The query now runs under the account lock before
acquiring the auction lock; validation precedence and atomic persistence remain intact.
All attempts, including replays, acquire the account lock before reading idempotency
history. A second concurrency test demonstrated why this matters: simultaneous retries
previously returned a rejection instead of the original accepted result.

An earlier CI startup failure had hidden its error in a local-only build log. Build
output is now streamed and retained, with a shell regression check proving that the
original failure status and project cleanup are preserved. That startup failure did
not reproduce on the next runner; its underlying cause cannot be established from
the incomplete original evidence.

## Recovery and learning

The recovery mechanism is persisted state plus application-start reconciliation and
durable event republication. No operator edited accepted bids, outcomes, sales or event
completion flags. SMTP delivery itself remains at-least-once; this exercise does not
promise exactly-once delivery to an external mail system.

The cold backup exercise stopped writers, copied PostgreSQL and private object storage,
restored into a separate project, and compared domain counts and every private file's
content hash before checking application readiness. Its measured recovery time and
counts are recorded in `backup.json`. This establishes a cold-snapshot recovery point,
not continuous point-in-time recovery or a public-service availability SLO.

Follow-up owner: repository maintainer. Keep the operational proof as a required CI
check for changes to lifecycle commands, persistence, security and observability.
Investigate any failed latency measurement using the persisted environment and raw
assertion results; do not change correctness or latency thresholds to obtain a green run.
