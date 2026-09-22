# MVP verification record

Implementation and validation for issue #44 on 2026-09-19–22.

The complete command and acceptance matrix are in [MVP proof](mvp-proof.md).
Results below are updated from actual execution; unexecuted experiments do not count
as passes. The source checkout is a working tree until these changes are committed.

| Check | Result |
| --- | --- |
| Existing backend full suite before operational changes | Passed |
| Formatting and backend suite | Passed: 121 tests, zero failures/errors/skips, including module verification and PostgreSQL tests |
| Frontend formatting, types, unit tests and build | Passed: 41 tests across 8 files |
| Clean Compose build, generated contract and browser journeys | Passed: both images build, client matches live API, 5 browser journeys |
| 100-bidder latency, correctness and live delivery | Passed: measured p95 420.26 ms, maximum 425.84 ms, request-to-live 175 ms; one accepted command and 99 expected rejections |
| Process restart and durable delivery experiments | Passed: four operational tests, zero skips/retries/failures; one durable order, outcome and sale across injected failures |
| Prometheus scrape and trace availability | Passed: target up; exact response trace retrieved from Tempo; structured logs and proof-secret checks passed |
| Backup restore / postmortem exercise | Passed: restored application ready in 17 seconds; 7 bids, 3 sales, 70 audit records, 5 media records and all private file bytes preserved |
| GitHub Actions | Workflow configured; remote execution not yet observed |

The final operational suite started at `2026-09-22T18:32:12.189Z` and completed in
155.84 seconds. The restart scenario ran from `18:32:57.787Z` to `18:34:25.385Z`;
backup recovery started at `18:34:48Z`. Its isolated project was
`collectors-proof-1790101890`. Source baseline:
`e77c5c3ef4176a26276c89436181b9c425e2863f` plus this working-tree implementation.
Raw sanitized measurements and recovery facts remain in `artifacts/proof/results.json`
and `backup.json`; the workflow retains equivalent evidence for each CI run.

## Measurement limits

The host was Linux x86_64 with 20 Docker-visible CPUs and 16,614,326,272 bytes of
memory. The local browser runner used Node 24.18.0 and reported the repository's
24.20.0 engine mismatch; application images and CI use the existing pinned toolchain.
These are observed environment versions, not new version selections.

The fixed 100-command warmup also passed in this run: p95 485.20 ms, maximum
493.97 ms, live event 335 ms. Earlier isolated cold bursts exceeded 500 ms
(623–748 ms during diagnosis). Cold-start performance is therefore variable; this
single passing warmup does not establish a cold-start latency guarantee. The gated
measurement follows the documented fixed warmup, with no retries or threshold
overrides. Both populations retain all responses and all correctness assertions.

This proves the specified simultaneous contention scenario on this host, not sustained
throughput or production capacity. Recovery uses compressed fixture deadlines and
real process kills. The lost-acknowledgement case discards the client response; it does
not interrupt a network packet at the commit boundary. Backup timing covers a cold
snapshot restore into a second local project, not continuous recovery.

Local acceptance passed. M6's remote reproducibility gate remains unverified until
GitHub Actions executes this change successfully from a clean checkout. No remote
workflow execution or public deployment is claimed.
