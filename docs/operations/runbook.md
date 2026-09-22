# Local operations and recovery

Use the [proof sequence](mvp-proof.md) for an isolated exercise. For a retained proof
stack, prefix every command with `docker compose -p <printed-project>` and use its
ports. The commands below target the ordinary development stack.

## Signals and diagnosis

Open Grafana at <http://localhost:3000> and select **Collectors operational proof**.
Its provisioned panels show availability, bid p95, bid status rates, HTTP failures,
JDBC usage and JVM threads. Prometheus is at <http://localhost:9090>. Metrics are
scraped directly from the backend on the Compose network; `/actuator/prometheus`
returns 404 through NGINX. The backend must remain unexposed to host traffic.

| SLI | Acceptance or investigation trigger | First action |
| --- | --- | --- |
| Readiness and Prometheus `up` | Target remains healthy outside injected outage | Check backend, PostgreSQL and MinIO container status |
| Bid p95 | Below 500 ms for 100-command proof | Compare 422 below-minimum rejections with 429 limits and 5xx errors; inspect JDBC saturation and database locks |
| Committed live delivery | Below one second in browser proof | Compare persisted REST snapshot with STOMP projection; inspect listener errors and event backlog |
| Pending durable events | Returns to zero after recovery | Restore failed dependency and restart backend to republish outstanding events |
| Overdue non-terminal auctions/sales | Reconciled after restart | Inspect lifecycle logs, blocked transactions and durable deadlines before retrying |
| Domain uniqueness | No duplicate bid sequence, sale or terminal transition | Stop the exercise and retain evidence; never delete duplicate-looking facts to pass it |

Backend output is structured JSON. Obtain an `X-Trace-Id` from an API response and
search that ID in `docker compose logs backend`; use Grafana Explore's Tempo source
to inspect the same trace. A request may have a trace without an application log entry.
Do not log request bodies, cookies, tokens, passwords, exact reserves or private notes.
Keep diagnostic logs local until reviewed and sanitized. Traces expire with Tempo's
configured retention and require tracing enabled before the incident.

Read-only incident queries (operator access, never product-module cross-table code):

```bash
docker compose exec -T postgres psql -U collectors -d collectors_auction <<'SQL'
SELECT count(*) AS pending, min(publication_date) AS oldest
FROM event_publication WHERE completion_date IS NULL;
SELECT state, count(*) FROM auctions GROUP BY state;
SELECT state, count(*) FROM sales GROUP BY state;
SELECT pid, wait_event_type, wait_event, pg_blocking_pids(pid)
FROM pg_stat_activity WHERE datname = current_database() AND state <> 'idle';
SELECT auction_id, sequence_number, count(*) FROM accepted_bids
GROUP BY auction_id, sequence_number HAVING count(*) > 1;
SELECT auction_id, count(*) FROM sales GROUP BY auction_id HAVING count(*) > 1;
SQL
```

For delayed starts, closing, seller decisions or settlement: verify database connectivity
and server clock; restore dependencies; restart the backend and let its application-ready
reconciler process durable deadlines. For SMTP failure: restore Mailpit and restart the
backend to republish outstanding events. Recheck backlog and the resulting domain facts.
Do not mutate event publication status or manually manufacture sales. If a bid response
was lost, retry the original account/auction/idempotency key and amount. A new key is a
new command. Reconnect live clients through their normal REST snapshot recovery.

## Backup and restore exercise

PostgreSQL and private MinIO objects form one recoverable data set. Pause writers for
a consistent local backup; a database dump alone does not preserve draft media. Protect
the backup directory because it includes credentials, tokens, identity and private media.
Store deployment secrets separately. This cold-backup procedure makes no zero-downtime
claim. Recovery point is the stopped-writer snapshot; record actual downtime as the RTO.

```bash
umask 077
mkdir -p backups/exercise
docker compose stop web backend minio-init minio
docker compose exec -T postgres pg_dump -U collectors -d collectors_auction -Fc > backups/exercise/database.dump
minio_container=$(docker compose ps --all --quiet minio)
docker cp "$minio_container:/data/." - > backups/exercise/minio.tar
# Preserve the exact private object volume bytes while the MinIO server is stopped.
sha256sum backups/exercise/database.dump backups/exercise/minio.tar > backups/exercise/SHA256SUMS
docker compose start minio backend web
```

Restore into a **new disposable Compose project**, with different host ports and a
fresh database, never over the source deployment. First create its volumes and start
PostgreSQL; keep backend/web stopped. Use the same environment and image definitions
as the backup. Check checksums, then restore before starting writers:

```bash
# Set target to the new recovery project, not the source project.
target=collectors-recovery-exercise
sha256sum --check backups/exercise/SHA256SUMS
docker compose -p "$target" up -d postgres
docker compose -p "$target" exec -T postgres pg_restore \
  -U collectors -d collectors_auction --exit-on-error < backups/exercise/database.dump
docker compose -p "$target" create minio
restored_minio=$(docker compose -p "$target" ps --all --quiet minio)
docker cp - "$restored_minio:/data" < backups/exercise/minio.tar
docker compose -p "$target" up -d
```

Set port overrides before these commands to avoid the source stack's ports. Wait for
PostgreSQL readiness before `pg_restore`. Verify migration validation, account access,
private and public media, bid order, sale counts, deadline reconciliation and event backlog.
Record backup and restored counts, checksums, recovery duration and any lost interval.
Only then remove the disposable recovery project. Do not commit backups.

## Postmortem exercise

Run the SMTP outage and interrupted-closing proof cases with `KEEP_PROOF_STACK=1`.
Write an incident record with UTC timestamps for injection, detection, mitigation and
recovery; source revision; affected auction IDs (no credentials); client-visible impact;
trace IDs; dashboard observations; durable backlog before/after; and assertions showing
one bid sequence/outcome/sale. Explain why accepted domain commands survived the outage,
which dependency failed, and whether any email repeated. Include the measured detection
and recovery intervals, one preventive action with an owner, and a rerun proving it.

A completed exercise must include actual observations. The blank template is not evidence:

```text
Revision / environment:
Injected failure and UTC time:
Detection signal and UTC time:
Affected request / trace IDs:
Durable facts before restart:
Recovery action and UTC time:
Durable facts after restart:
User impact and measured downtime:
Root cause / contributing conditions:
Follow-up owner / regression proof:
```
