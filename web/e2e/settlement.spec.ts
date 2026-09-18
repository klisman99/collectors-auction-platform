import { execFileSync } from 'node:child_process';
import { randomUUID } from 'node:crypto';
import { type APIRequestContext, expect, type Page, test } from '@playwright/test';

const mailpitUrl = process.env.MAILPIT_URL ?? 'http://127.0.0.1:8025';
const composeProject = process.env.E2E_COMPOSE_PROJECT;

test('buyer and seller complete the simulated payment and shipment handoff from the UI', async ({
  browser,
  page,
  request,
}) => {
  test.setTimeout(120_000);
  const unique = Date.now();
  const sellerEmail = `settlement-seller-${unique}@example.com`;
  const buyerEmail = `settlement-buyer-${unique}@example.com`;
  const title = `Settlement collectible ${unique}`;

  await registerVerifyAndSignIn(page, request, sellerEmail, `seller_${unique}`);

  const buyerContext = await browser.newContext();
  try {
    const buyerPage = await buyerContext.newPage();
    await registerVerifyAndSignIn(buyerPage, request, buyerEmail, `buyer_${unique}`);
    insertPaymentPendingSale({ buyerEmail, sellerEmail, title });
    await buyerPage.reload();
    await expect(buyerPage.getByRole('heading', { name: 'Settlement desk' })).toBeVisible();
    await expect(buyerPage.getByText(title, { exact: true })).toBeVisible();
    await buyerPage.getByRole('button', { name: 'Simulate payment' }).click();
    await expect(
      buyerPage.getByText('Payment recorded. The seller can now record shipment.'),
    ).toBeVisible();

    await page.reload();
    await expect(page.getByText(title, { exact: true })).toBeVisible();
    await page.getByLabel('Carrier').fill('Simulated Express');
    await page.getByLabel('Tracking reference').fill('SIM-123-TRACK');
    await page.getByRole('button', { name: 'Record shipment' }).click();
    await expect(
      page.getByText(
        'Shipment recorded. The buyer can now see the carrier and tracking reference.',
      ),
    ).toBeVisible();

    await buyerPage.reload();
    await expect(buyerPage.getByText('Simulated Express')).toBeVisible();
    await expect(buyerPage.getByText('SIM-123-TRACK')).toBeVisible();
  } finally {
    await buyerContext.close();
  }
});

async function registerVerifyAndSignIn(
  page: Page,
  request: APIRequestContext,
  email: string,
  handle: string,
): Promise<void> {
  const password = 'a settlement participant password';
  await page.goto('/');
  const messagesBeforeRegistration = await mailpitMessageIds(request);
  await page.getByLabel('Email address').fill(email);
  await page.getByLabel('Public handle').fill(handle);
  await page.getByLabel('Password').fill(password);
  await page.getByRole('button', { name: 'Create account' }).click();
  await page.goto(await mailpitLink(request, email, messagesBeforeRegistration));
  await page.getByRole('button', { name: 'Verify email' }).click();
  await page.getByLabel('Email address').fill(email);
  await page.getByLabel('Password').fill(password);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page.getByText('Trading access is active.')).toBeVisible();
}

function insertPaymentPendingSale({
  buyerEmail,
  sellerEmail,
  title,
}: {
  buyerEmail: string;
  sellerEmail: string;
  title: string;
}): void {
  const saleId = randomUUID();
  const auctionId = randomUUID();
  const itemId = randomUUID();
  execFileSync('docker', [
    'compose',
    ...(composeProject === undefined ? [] : ['-p', composeProject]),
    '-f',
    `${import.meta.dirname}/../../compose.yaml`,
    'exec',
    '-T',
    'postgres',
    'psql',
    '-U',
    'collectors',
    '-d',
    'collectors_auction',
    '-c',
    `INSERT INTO sales (
      id, auction_id, item_id, seller_id, buyer_id, amount_cents, created_at,
      item_title, seller_handle, buyer_handle, state, payment_deadline_at
    )
    SELECT
      '${saleId}'::uuid, '${auctionId}'::uuid, '${itemId}'::uuid, seller.id, buyer.id, 12500,
      CURRENT_TIMESTAMP, '${title}', seller.public_handle, buyer.public_handle,
      'PAYMENT_PENDING', CURRENT_TIMESTAMP + INTERVAL '24 hours'
    FROM regular_accounts seller
    CROSS JOIN regular_accounts buyer
    WHERE seller.normalized_email = '${sellerEmail}'
      AND buyer.normalized_email = '${buyerEmail}'`,
  ]);
}

async function mailpitMessageIds(request: APIRequestContext): Promise<Set<string>> {
  const response = await request.get(`${mailpitUrl}/api/v1/messages`);
  if (!response.ok()) return new Set();
  const body = (await response.json()) as { messages?: Array<{ ID: string }> };
  return new Set((body.messages ?? []).map((message) => message.ID));
}

async function mailpitLink(
  request: APIRequestContext,
  recipient: string,
  ignoredMessageIds: Set<string>,
): Promise<string> {
  let link = '';
  await expect
    .poll(async () => {
      const response = await request.get(`${mailpitUrl}/api/v1/messages`);
      if (!response.ok()) return '';
      const body = (await response.json()) as {
        messages?: Array<{ ID: string; To?: Array<{ Address: string }> }>;
      };
      for (const message of body.messages ?? []) {
        if (ignoredMessageIds.has(message.ID)) continue;
        if (!message.To?.some(({ Address }) => Address === recipient)) continue;
        const detail = await request.get(`${mailpitUrl}/api/v1/message/${message.ID}`);
        if (!detail.ok()) continue;
        const content = (await detail.json()) as { HTML?: string; Text?: string };
        link =
          `${content.HTML ?? ''}\n${content.Text ?? ''}`.match(
            /https?:\/\/[^\s"<>]+verificationToken=[^\s"<>]+/,
          )?.[0] ?? '';
        if (link !== '') return link;
      }
      return '';
    })
    .not.toBe('');
  return link;
}
