import { randomUUID } from 'node:crypto';
import { type APIRequestContext, expect, test } from '@playwright/test';
import type { BidCommandResult, PublicBid, SaleResponse } from '../src/api/generated/types.gen';
import {
  baseURL,
  client,
  closeAuction,
  compose,
  csrf,
  interruptClosing,
  password,
  post,
  ready,
  register,
  restart,
  runId,
  schedule,
  signIn,
  sql,
  startAuction,
  state,
  verificationToken,
} from './support';

async function administrator() {
  const api = await client();
  await signIn(
    api,
    process.env.INITIAL_ADMINISTRATOR_EMAIL ?? 'admin-proof@example.com',
    process.env.INITIAL_ADMINISTRATOR_PASSWORD ?? 'operational proof administrator password',
  );
  return api;
}

async function saleFor(api: APIRequestContext, auctionId: string): Promise<SaleResponse> {
  let sale: SaleResponse | undefined;
  await expect
    .poll(async () => {
      const response = await api.get('/api/v1/sales/mine');
      expect(response.status()).toBe(200);
      sale = ((await response.json()) as SaleResponse[]).find(
        (candidate) => candidate.auctionId === auctionId,
      );
      return Boolean(sale);
    })
    .toBe(true);
  if (!sale) throw new Error('Sale did not appear');
  return sale;
}

test('BR-BID-008..011: 100 contenders, durable order, live latency and full settlement journey', async ({
  page,
}, testInfo) => {
  const seller = await client();
  const admin = await administrator();
  const visitor = await client();
  const bidders: APIRequestContext[] = [];
  try {
    const owner = await register(seller, 'seller');
    const auction = await schedule(seller, admin, 500_000);
    // Load identities are fixtures; the seller above exercises real email verification.
    sql(`INSERT INTO regular_accounts (id, normalized_email, public_handle, password_hash, status, registered_at, verified_at)
      SELECT gen_random_uuid(), 'load-' || n || '-${runId}@example.com', 'load_' || n || '_${runId}', password_hash, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
      FROM regular_accounts CROSS JOIN generate_series(0,99) n WHERE id = '${owner.accountId}'`);
    for (let index = 0; index < 100; index++) {
      const api = await client();
      bidders.push(api);
      await signIn(api, `load-${index}-${runId}@example.com`);
    }
    await startAuction(visitor, auction.id);
    await page.goto(baseURL);
    await page.evaluate((id) => {
      const events: Array<{ bidSequence: number; receivedAt: number; occurredAt: string }> = [];
      Object.assign(window, { proofEvents: events });
      const socket = new WebSocket(`${location.origin.replace('http', 'ws')}/ws`);
      socket.onopen = () =>
        socket.send(`CONNECT\naccept-version:1.2\nhost:${location.host}\nheart-beat:0,0\n\n\0`);
      socket.onmessage = ({ data }) => {
        for (const frame of String(data).split('\0')) {
          if (frame.startsWith('CONNECTED'))
            socket.send(`SUBSCRIBE\nid:proof\ndestination:/topic/auctions/${id}\nack:auto\n\n\0`);
          if (frame.startsWith('MESSAGE')) {
            const event = JSON.parse(frame.slice(frame.indexOf('\n\n') + 2));
            if (event.type === 'BID_ACCEPTED') events.push({ ...event, receivedAt: Date.now() });
          }
        }
      };
    }, auction.id);
    const headers = await Promise.all(bidders.map(csrf));
    // Warmup is outside the measured burst and proves subscription delivery.
    await expect
      .poll(async () => {
        const response = await post(bidders[0], `/api/v1/auctions/${auction.id}/bids`, {
          amountCents: 1000,
          idempotencyKey: warmupKey,
        });
        expect([200, 201]).toContain(response.status());
        return page.evaluate(
          () => (window as unknown as { proofEvents: unknown[] }).proofEvents.length,
        );
      })
      .toBe(1);
    async function measureBurst(amountCents: number, sequence: number, attachmentName: string) {
      const burstStartedAt = Date.now();
      const results = await Promise.all(
        bidders.map(async (api, index) => {
          const started = performance.now();
          const response = await api.post(`/api/v1/auctions/${auction.id}/bids`, {
            headers: headers[index],
            data: { amountCents, idempotencyKey: randomUUID() },
          });
          return {
            status: response.status(),
            durationMs: performance.now() - started,
            result: (await response.json()) as BidCommandResult,
          };
        }),
      );
      expect(results.filter((result) => result.status === 201)).toHaveLength(1);
      expect(
        results.filter(
          (result) => result.status === 422 && result.result.code === 'BID_AMOUNT_TOO_LOW',
        ),
      ).toHaveLength(99);
      await expect
        .poll(() =>
          page.evaluate(() => (window as unknown as { proofEvents: unknown[] }).proofEvents.length),
        )
        .toBe(sequence);
      const live = await page.evaluate(
        (targetSequence) =>
          (
            window as unknown as { proofEvents: Array<{ bidSequence: number; receivedAt: number }> }
          ).proofEvents.find((event) => event.bidSequence === targetSequence),
        sequence,
      );
      const durations = results
        .map((result) => result.durationMs)
        .sort((left, right) => left - right);
      const measurement = {
        bidders: 100,
        requests: 100,
        accepted: 1,
        belowMinimumRejections: 99,
        responseTimesMs: durations,
        p95ResponseMs: durations[94],
        maximumResponseMs: durations[99],
        requestToLiveEventMs: (live?.receivedAt ?? Infinity) - burstStartedAt,
      };
      await testInfo.attach(attachmentName, {
        body: JSON.stringify(measurement, null, 2),
        contentType: 'application/json',
      });
      return measurement;
    }
    // A fixed warmup is recorded even when it exceeds the latency gate. It is
    // never retried or used as a substitute for the independently measured burst.
    await measureBurst(2000, 2, '100-bidder-cold-warmup');
    const measurement = await measureBurst(3000, 3, '100-bidder-measurement');
    // Soft gates preserve the recovery evidence while still failing the suite.
    expect
      .soft(measurement.p95ResponseMs, 'p95 bid response must remain below 500 ms')
      .toBeLessThan(500);
    expect
      .soft(
        measurement.requestToLiveEventMs,
        'request-to-live includes commit and must remain below one second',
      )
      .toBeLessThan(1000);
    expect(
      sql(
        `SELECT string_agg(sequence_number::text, ',' ORDER BY sequence_number) FROM accepted_bids WHERE auction_id = '${auction.id}'`,
      ),
    ).toBe('1,2,3');
    const history = (await (
      await visitor.get(`/api/v1/auctions/${auction.id}/bids`)
    ).json()) as PublicBid[];
    expect(history).toHaveLength(3);
    expect(JSON.stringify(history)).not.toContain(runId);
    expect(
      (await (await visitor.get(`/api/v1/auctions/${auction.id}`)).json()).reserveAmountCents,
    ).toBeNull();
    expect([401, 403]).toContain((await visitor.get(auction.privateMediaUrl)).status());
    expect((await bidders[0].get(auction.privateMediaUrl)).status()).toBe(404);
    const privateObjectKey = sql(
      `SELECT display_storage_key FROM collectible_item_media WHERE item_id = '${auction.itemId}' LIMIT 1`,
    );
    expect(privateObjectKey).not.toBe('');
    expect(
      (await visitor.get(`http://127.0.0.1:18333/collectors-images/${privateObjectKey}`)).status(),
    ).toBe(403);

    expect(
      (
        await bidders[0].post(`/api/v1/auctions/${auction.id}/bids`, {
          data: { amountCents: 3000, idempotencyKey: randomUUID() },
        })
      ).status(),
    ).toBe(403);
    expect((await visitor.get('/actuator/prometheus')).status()).toBe(404);

    // Late bid then permanent disqualification; the earlier extension survives.
    sql(
      `UPDATE auctions SET ends_at = CURRENT_TIMESTAMP + INTERVAL '30 seconds' WHERE id = '${auction.id}'`,
    );
    const late = await post(bidders[99], `/api/v1/auctions/${auction.id}/bids`, {
      amountCents: 4000,
      idempotencyKey: randomUUID(),
    });
    expect(late.status()).toBe(201);
    const accepted = await late.json();
    const extended = await (await visitor.get(`/api/v1/auctions/${auction.id}`)).json();
    expect(Date.parse(extended.effectiveEndAt) - Date.parse(accepted.acceptedAt)).toBe(120_000);
    const suspendedId = (await (await bidders[99].get('/api/v1/auth/session')).json()).accountId;
    expect(
      (
        await post(admin, `/api/v1/admin/regular-accounts/${suspendedId}/suspension`, {
          reasonCategory: 'SECURITY',
          publicReason: 'Operational proof suspension',
          internalNote: 'proof-private-note',
        })
      ).status(),
    ).toBe(200);
    const after = await (await visitor.get(`/api/v1/auctions/${auction.id}`)).json();
    expect(after.effectiveEndAt).toBe(extended.effectiveEndAt);
    const bids = (await (
      await visitor.get(`/api/v1/auctions/${auction.id}/bids`)
    ).json()) as PublicBid[];
    expect(bids.find((bid) => bid.sequence === 4)?.disqualified).toBe(true);
    expect(JSON.stringify(after)).not.toContain('proof-private-note');
    expect((await bidders[99].get('/api/v1/auth/session')).status()).toBe(401);
    expect(
      (
        await post(admin, `/api/v1/operations/auctions/${auction.id}/suspension`, {
          reasonCategory: 'OTHER',
          publicReason: 'Operational recovery exercise',
          internalNote: 'proof-private-note',
        })
      ).status(),
    ).toBe(200);
    await restart(visitor);
    await state(visitor, auction.id, 'SUSPENDED');
    expect((await post(admin, `/api/v1/operations/auctions/${auction.id}/resume`)).status()).toBe(
      200,
    );
    await closeAuction(visitor, auction.id, 'AWAITING_SELLER_DECISION');
    expect(
      (
        await post(seller, `/api/v1/auctions/${auction.id}/seller-decision`, { decision: 'ACCEPT' })
      ).status(),
    ).toBe(200);
    const sale = await saleFor(seller, auction.id);
    const buyerIndex = Number(sale.buyer?.handle?.split('_')[1]);
    const buyer = bidders[buyerIndex];
    expect((await post(buyer, `/api/v1/sales/${sale.id}/payment`)).status()).toBe(200);
    expect(
      (
        await post(seller, `/api/v1/sales/${sale.id}/shipment`, {
          carrier: 'Simulated Express',
          trackingReference: 'PROOF-001',
        })
      ).status(),
    ).toBe(200);
    expect((await post(buyer, `/api/v1/sales/${sale.id}/delivery-confirmation`)).status()).toBe(
      200,
    );
    await restart(visitor);
    const completed = await saleFor(buyer, auction.id);
    expect(completed.state).toBe('COMPLETED');
    expect(completed.itemDisposition).toBe('ARCHIVED');
    expect(sql(`SELECT count(*) FROM sales WHERE auction_id = '${auction.id}'`)).toBe('1');
    expect(sql(`SELECT status FROM collectible_items WHERE id = '${auction.itemId}'`)).toBe(
      'ARCHIVED',
    );
  } finally {
    await Promise.all([seller, admin, visitor, ...bidders].map((api) => api.dispose()));
  }
});
const warmupKey = randomUUID();

test('BR-AUC-014 / BR-OUT-009 / BR-SALE-007: restart deadlines and lost acknowledgement', async ({
  request,
}, testInfo) => {
  await ready(request);
  const startedAt = new Date().toISOString();
  const seller = await client();
  const buyer = await client();
  const admin = await administrator();
  try {
    await register(seller, 'recovery_s');
    await register(buyer, 'recovery_b');
    const auction = await schedule(seller, admin);
    compose('kill', '-s', 'SIGKILL', 'backend');
    sql(
      `UPDATE auctions SET starts_at = CURRENT_TIMESTAMP - INTERVAL '1 minute', ends_at = CURRENT_TIMESTAMP + INTERVAL '1 hour' WHERE id = '${auction.id}'`,
    );
    compose('start', 'backend');
    await ready(buyer);
    await state(buyer, auction.id, 'LIVE');
    const key = randomUUID();
    // Discard the first response at the client delivery boundary, then kill the
    // process. The caller resolves the ambiguous result only by replaying its key.
    await post(buyer, `/api/v1/auctions/${auction.id}/bids`, {
      amountCents: 1000,
      idempotencyKey: key,
    });
    expect(sql(`SELECT count(*) FROM accepted_bids WHERE auction_id = '${auction.id}'`)).toBe('1');
    await restart(buyer);
    const replay = await post(buyer, `/api/v1/auctions/${auction.id}/bids`, {
      amountCents: 1000,
      idempotencyKey: key,
    });
    expect(replay.status()).toBe(200);
    expect((await replay.json()).sequence).toBe(1);
    await interruptClosing(buyer, auction.id);
    const sale = await saleFor(buyer, auction.id);
    compose('kill', '-s', 'SIGKILL', 'backend');
    sql(
      `UPDATE sales SET payment_deadline_at = CURRENT_TIMESTAMP - INTERVAL '1 second' WHERE id = '${sale.id}'`,
    );
    compose('start', 'backend');
    await ready(buyer);
    await expect.poll(async () => (await saleFor(buyer, auction.id)).state).toBe('FAILED');
    expect((await saleFor(buyer, auction.id)).itemDisposition).toBe('RELISTING_ELIGIBLE');
    expect(
      sql(`SELECT auction_locked_at IS NULL FROM collectible_items WHERE id = '${auction.itemId}'`),
    ).toBe('t');
    const reserve = await schedule(seller, admin, 100_000);
    await startAuction(buyer, reserve.id);
    expect(
      (
        await post(buyer, `/api/v1/auctions/${reserve.id}/bids`, {
          amountCents: 1000,
          idempotencyKey: randomUUID(),
        })
      ).status(),
    ).toBe(201);
    await closeAuction(buyer, reserve.id, 'AWAITING_SELLER_DECISION');
    compose('kill', '-s', 'SIGKILL', 'backend');
    sql(
      `UPDATE auctions SET seller_decision_deadline_at = CURRENT_TIMESTAMP - INTERVAL '1 second' WHERE id = '${reserve.id}'`,
    );
    compose('start', 'backend');
    await ready(buyer);
    await state(buyer, reserve.id, 'UNSOLD');
    await restart(buyer);
    expect(sql(`SELECT count(*) FROM sales WHERE auction_id = '${reserve.id}'`)).toBe('0');
    expect(sql(`SELECT count(*) FROM sales WHERE auction_id = '${auction.id}'`)).toBe('1');
    expect(
      sql(
        `SELECT count(*) FROM auction_timeline_events WHERE auction_id = '${reserve.id}' AND event_type = 'SELLER_DECISION_EXPIRED'`,
      ),
    ).toBe('1');
    await testInfo.attach('recovery-postmortem', {
      contentType: 'application/json',
      body: JSON.stringify(
        {
          startedAt,
          recoveredAt: new Date().toISOString(),
          fault:
            'SIGKILL during committed closing claim; deadline downtime; discarded acknowledgement',
          detection: 'persisted CLOSING state while eligible-bid query held by PostgreSQL lock',
          mitigation: 'restart process and replay original bid key',
          auctionId: auction.id,
          reserveAuctionId: reserve.id,
          durableBidCount: 1,
          soldAuctionSaleCount: 1,
          expiredReserveSaleCount: 0,
          sellerDecisionExpiryFactCount: 1,
          userImpact: 'temporary API unavailability; no accepted bid or final outcome lost',
          followUp: 'maintainer reruns this gate for lifecycle, persistence or runtime changes',
        },
        null,
        2,
      ),
    });
  } finally {
    await Promise.all([seller, buyer, admin].map((api) => api.dispose()));
  }
});

test('BR-NOTIFY-005: failed email delivery survives a killed process and logs contain no secrets', async () => {
  const api = await client();
  const email = `outage-${runId}@example.com`;
  try {
    compose('stop', 'mailpit');
    expect(
      (
        await post(api, '/api/v1/auth/register', {
          email,
          publicHandle: `outage_${runId}`,
          password,
        })
      ).status(),
    ).toBe(201);
    await expect
      .poll(() =>
        Number(
          sql(
            "SELECT count(*) FROM event_publication WHERE completion_date IS NULL AND listener_id LIKE '%Verification%'",
          ),
        ),
      )
      .toBeGreaterThan(0);
    compose('kill', '-s', 'SIGKILL', 'backend');
    compose('start', 'mailpit');
    compose('start', 'backend');
    await ready(api);
    const token = await verificationToken(api, email);
    expect((await post(api, '/api/v1/auth/verify-email', { token })).status()).toBe(200);
    expect((await post(api, '/api/v1/auth/verify-email', { token })).status()).toBe(400);
    await expect
      .poll(() =>
        Number(sql('SELECT count(*) FROM event_publication WHERE completion_date IS NULL')),
      )
      .toBe(0);
    const logs = compose('logs', '--no-color', 'backend');
    const structuredLogs = logs.split('\n').flatMap((line) => {
      const start = line.indexOf('{');
      if (start < 0) return [];
      try {
        return [JSON.parse(line.slice(start))];
      } catch {
        return [];
      }
    });
    expect(
      structuredLogs.some((entry) => entry['@timestamp'] && entry.level && entry.message),
    ).toBe(true);

    for (const secret of [
      password,
      token,
      process.env.INITIAL_ADMINISTRATOR_PASSWORD ?? 'operational proof administrator password',
    ]) {
      // Never print either the secret or complete logs in assertion failure output.
      expect(
        logs.includes(secret),
        'backend logs must not contain proof credentials or tokens',
      ).toBe(false);
    }
  } finally {
    compose('start', 'mailpit', 'backend');
    await api.dispose();
  }
});

test('operations: Prometheus scrapes and Tempo retrieves a correlated request trace', async ({
  request,
}, testInfo) => {
  await expect
    .poll(async () => {
      const response = await request.get('http://127.0.0.1:19090/api/v1/query?query=up');
      const body = await response.json();
      return body.data.result.some(
        (series: { metric: { job: string }; value: string[] }) =>
          series.metric.job === 'collectors-auction-platform' && series.value[1] === '1',
      );
    })
    .toBe(true);
  const response = await request.get('/api/v1/status');
  expect(response.status()).toBe(200);
  const traceId = response.headers()['x-trace-id'];
  expect(traceId).toMatch(/^[a-f0-9]{32}$/);
  await expect
    .poll(async () => (await request.get(`http://127.0.0.1:13200/api/traces/${traceId}`)).status())
    .toBe(200);
  await testInfo.attach('trace-correlation', {
    body: JSON.stringify({ traceId }),
    contentType: 'application/json',
  });
});
