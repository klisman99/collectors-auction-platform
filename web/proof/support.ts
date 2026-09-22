import { execFileSync, spawn } from 'node:child_process';
import { randomUUID } from 'node:crypto';
import { type APIRequestContext, expect, request } from '@playwright/test';

export const baseURL = process.env.BASE_URL ?? 'http://127.0.0.1:18080';
export const password = 'operational proof participant password';
export const runId = randomUUID().replaceAll('-', '').slice(0, 12);
const project = process.env.E2E_COMPOSE_PROJECT;

export function compose(...args: string[]): string {
  if (!project?.startsWith('collectors-proof-')) {
    throw new Error('Recovery experiments require an isolated collectors-proof-* Compose project.');
  }
  return execFileSync(
    'docker',
    [
      'compose',
      '--env-file',
      '/dev/null',
      '-p',
      project,
      '-f',
      `${import.meta.dirname}/../../compose.yaml`,
      ...args,
    ],
    {
      encoding: 'utf8',
      timeout: 120_000,
      stdio: ['pipe', 'pipe', 'pipe'],
    },
  );
}

export function sql(statement: string): string {
  return compose(
    'exec',
    '-T',
    'postgres',
    'psql',
    '-X',
    '-qAt',
    '-v',
    'ON_ERROR_STOP=1',
    '-U',
    'collectors',
    '-d',
    'collectors_auction',
    '-c',
    statement,
  ).trim();
}

export async function client(): Promise<APIRequestContext> {
  return request.newContext({ baseURL });
}

export async function csrf(api: APIRequestContext): Promise<Record<string, string>> {
  const response = await api.get('/api/v1/csrf');
  expect(response.status()).toBe(200);
  const token = await response.json();
  return { [token.headerName]: token.token };
}

export async function post(api: APIRequestContext, path: string, data: unknown = {}) {
  return api.post(path, { data, headers: await csrf(api) });
}

export async function signIn(api: APIRequestContext, email: string, secret = password) {
  const response = await post(api, '/api/v1/auth/sign-in', { email, password: secret });
  expect(response.status(), 'sign-in status').toBe(200);
  return response.json();
}

export async function verificationToken(api: APIRequestContext, email: string): Promise<string> {
  let token = '';
  await expect
    .poll(async () => {
      const response = await api.get(
        `${process.env.MAILPIT_URL ?? 'http://127.0.0.1:18025'}/api/v1/messages?limit=200`,
      );
      if (!response.ok()) return false;
      const body = await response.json();
      for (const message of body.messages ?? []) {
        if (!message.To?.some((to: { Address: string }) => to.Address === email)) continue;
        const detail = await api.get(
          `${process.env.MAILPIT_URL ?? 'http://127.0.0.1:18025'}/api/v1/message/${message.ID}`,
        );
        const content = await detail.json();
        token =
          `${content.Text ?? ''} ${content.HTML ?? ''}`.match(
            /verificationToken=([A-Za-z0-9_-]+)/,
          )?.[1] ?? '';
        if (token) return true;
      }
      return false;
    })
    .toBe(true);
  return token;
}

export async function register(api: APIRequestContext, label: string) {
  const email = `${label}-${runId}@example.com`;
  const response = await post(api, '/api/v1/auth/register', {
    email,
    publicHandle: `${label}_${runId}`,
    password,
  });
  expect(response.status()).toBe(201);
  const token = await verificationToken(api, email);
  expect((await post(api, '/api/v1/auth/verify-email', { token })).status()).toBe(200);
  return { email, token, ...(await signIn(api, email)) };
}

export async function ready(api: APIRequestContext) {
  await expect
    .poll(
      async () => {
        try {
          return (await api.get('/actuator/health/readiness', { timeout: 2000 })).status();
        } catch {
          return 0;
        }
      },
      { timeout: 120_000 },
    )
    .toBe(200);
}

export async function restart(api: APIRequestContext) {
  compose('kill', '-s', 'SIGKILL', 'backend');
  compose('start', 'backend');
  await ready(api);
}

export async function state(api: APIRequestContext, id: string, expected: string) {
  await expect
    .poll(async () => {
      const response = await api.get(`/api/v1/auctions/${id}`);
      return response.ok() ? (await response.json()).state : response.status();
    })
    .toBe(expected);
}

export async function schedule(
  seller: APIRequestContext,
  admin: APIRequestContext,
  reserveAmountCents: number | null = null,
) {
  const draftResponse = await post(seller, '/api/v1/catalog/drafts', {
    category: 'CARDS',
    title: `Proof collectible ${randomUUID().slice(0, 8)}`,
    description: 'One physical collectible for the reproducible operational journey.',
    condition: 'EXCELLENT',
    conditionNotes: 'No visible damage or wear.',
    ownershipDeclared: true,
  });
  expect(draftResponse.status()).toBe(201);
  const draft = await draftResponse.json();
  const upload = await seller.post(`/api/v1/catalog/drafts/${draft.id}/images`, {
    headers: await csrf(seller),
    multipart: {
      file: {
        name: 'proof.png',
        mimeType: 'image/png',
        buffer: Buffer.from(
          'iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAIAAAD91JpzAAAAFElEQVR4nGP8z8DAwMDAxMDAwMDAAAANHQEDasKb6QAAAABJRU5ErkJggg==',
          'base64',
        ),
      },
    },
  });
  expect(upload.status()).toBe(200);
  const media = await upload.json();
  expect((await post(seller, `/api/v1/catalog/drafts/${draft.id}/submit`)).status()).toBe(200);
  expect((await post(admin, `/api/v1/moderation/submissions/${draft.id}/approve`)).status()).toBe(
    200,
  );
  const response = await post(seller, '/api/v1/auctions', {
    itemId: draft.id,
    openingAmountCents: 1000,
    minimumIncrementCents: 1000,
    reserveAmountCents,
    startsAt: new Date(Date.now() + 360_000).toISOString(),
    endsAt: new Date(Date.now() + 1200_000).toISOString(),
  });
  expect(response.status()).toBe(201);
  return { ...(await response.json()), privateMediaUrl: media.url };
}

export async function startAuction(api: APIRequestContext, id: string) {
  // Time compression only: the application's reconciler performs the transition.
  sql(
    `UPDATE auctions SET starts_at = CURRENT_TIMESTAMP - INTERVAL '1 minute', ends_at = CURRENT_TIMESTAMP + INTERVAL '1 hour' WHERE id = '${id}'`,
  );
  await state(api, id, 'LIVE');
}

export async function closeAuction(api: APIRequestContext, id: string, expected: string) {
  compose('kill', '-s', 'SIGKILL', 'backend');
  sql(`UPDATE auctions SET ends_at = CURRENT_TIMESTAMP - INTERVAL '1 second' WHERE id = '${id}'`);
  compose('start', 'backend');
  await ready(api);
  await state(api, id, expected);
}

export async function interruptClosing(api: APIRequestContext, id: string) {
  // Block the eligible-bid read after the CLOSING claim has committed. This
  // makes a process kill in the middle of closing deterministic without hooks
  // or synthetic production events.
  const applicationName = `proof_closing_${runId}`;
  const lock = spawn(
    'docker',
    [
      'compose',
      '--env-file',
      '/dev/null',
      '-p',
      project ?? '',
      '-f',
      `${import.meta.dirname}/../../compose.yaml`,
      'exec',
      '-T',
      'postgres',
      'psql',
      '-X',
      '-qAt',
      '-v',
      'ON_ERROR_STOP=1',
      '-U',
      'collectors',
      '-d',
      'collectors_auction',
      '-c',
      `SET application_name = '${applicationName}'; BEGIN; LOCK TABLE accepted_bids IN ACCESS EXCLUSIVE MODE; SELECT pg_sleep(120); ROLLBACK;`,
    ],
    { stdio: 'ignore' },
  );
  try {
    await expect
      .poll(() =>
        sql(
          `SELECT count(*) FROM pg_locks l JOIN pg_stat_activity a ON a.pid = l.pid WHERE a.application_name = '${applicationName}' AND l.relation = 'accepted_bids'::regclass AND l.granted`,
        ),
      )
      .toBe('1');
    sql(`UPDATE auctions SET ends_at = CURRENT_TIMESTAMP - INTERVAL '1 second' WHERE id = '${id}'`);
    await expect.poll(() => sql(`SELECT state FROM auctions WHERE id = '${id}'`)).toBe('CLOSING');
    compose('kill', '-s', 'SIGKILL', 'backend');
  } finally {
    sql(
      `SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE application_name = '${applicationName}'`,
    );
    lock.kill();
    compose('start', 'backend');
  }
  await ready(api);
  await state(api, id, 'SOLD');
}
