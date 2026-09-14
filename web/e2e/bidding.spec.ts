import { execFileSync } from 'node:child_process';
import { type APIRequestContext, expect, type Page, test } from '@playwright/test';

const mailpitUrl = process.env.MAILPIT_URL ?? 'http://127.0.0.1:8025';
const administratorEmail = process.env.INITIAL_ADMINISTRATOR_EMAIL ?? 'admin-e2e@example.com';
const administratorPassword =
  process.env.INITIAL_ADMINISTRATOR_PASSWORD ?? 'e2e administrator password';

test('a verified non-seller places a durable pseudonymous bid from the live auction UI', async ({
  browser,
  page,
  request,
}) => {
  test.setTimeout(180_000);
  const unique = Date.now();
  const sellerEmail = `bid-seller-${unique}@example.com`;
  const sellerPassword = 'a bidding seller password';
  const bidderEmail = `bidder-${unique}@example.com`;
  const bidderPassword = 'a verified bidder password';
  const bidderHandle = `bidder_${unique}`;
  const title = `Live bidding card ${unique}`;

  await registerVerifyAndSignIn(page, request, sellerEmail, `seller_${unique}`, sellerPassword);
  await page.getByLabel('Draft title').fill(title);
  await page.getByLabel('Description').fill('A complete collectible prepared for live bidding.');
  await page.getByLabel('Condition notes').fill('Excellent condition with no visible wear.');
  await page.getByLabel('Ownership declaration').check();
  await page.getByRole('button', { name: 'Create draft' }).click();
  await page.getByLabel('Draft image').setInputFiles({
    name: 'bidding-card.png',
    mimeType: 'image/png',
    buffer: Buffer.from(
      'iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAIAAAD91JpzAAAAFElEQVR4nGP8z8DAwMDAxMDAwMDAAAANHQEDasKb6QAAAABJRU5ErkJggg==',
      'base64',
    ),
  });
  await page.getByRole('button', { name: 'Submit for review' }).click();

  const administratorContext = await browser.newContext();
  try {
    const administratorPage = await administratorContext.newPage();
    await signIn(administratorPage, administratorEmail, administratorPassword);
    const submission = administratorPage
      .getByRole('listitem')
      .filter({ hasText: title })
      .filter({ has: administratorPage.getByRole('button', { name: 'Approve' }) });
    await submission.getByRole('button', { name: 'Approve' }).click();
    await expect(administratorPage.getByText('Collectible approved.')).toBeVisible();
  } finally {
    await administratorContext.close();
  }

  await page.reload();
  await page.getByRole('button', { name: `Schedule auction for ${title}` }).click();
  await page.getByLabel('Opening amount').fill('100.00');
  await page.getByLabel('Minimum increment').fill('10.00');
  const startsAt = new Date(Date.now() + 6 * 60_000);
  await page.getByLabel('Start in São Paulo').fill(saoPauloInput(startsAt));
  await page
    .getByLabel('End in São Paulo')
    .fill(saoPauloInput(new Date(startsAt.getTime() + 10 * 60_000)));
  await page.getByRole('button', { name: 'Publish auction' }).click();
  await expect(page.getByRole('heading', { name: 'Published snapshot' })).toBeVisible();
  const scheduledResponse = await request.get('/api/v1/auctions?state=SCHEDULED');
  expect(scheduledResponse.ok()).toBeTruthy();
  const scheduledAuctions = (await scheduledResponse.json()) as {
    content: Array<{ id: string; item: { title: string } }>;
  };
  const scheduledAuction = scheduledAuctions.content.find(
    (candidate) => candidate.item.title === title,
  );
  expect(scheduledAuction).toBeDefined();
  promoteScheduledAuction(scheduledAuction?.id ?? '');

  const bidderContext = await browser.newContext();
  try {
    const bidderPage = await bidderContext.newPage();
    await registerVerifyAndSignIn(bidderPage, request, bidderEmail, bidderHandle, bidderPassword);
    await bidderPage.getByRole('tab', { name: 'Live auctions' }).click();
    await expect(bidderPage.getByText(title, { exact: true })).toBeVisible();
    await bidderPage.getByRole('button', { name: `View ${title}` }).click();
    await expect(bidderPage.getByText(/Required bid/)).toContainText('R$ 100,00');
    await bidderPage.getByRole('button', { name: 'Place bid' }).click();
    await expect(bidderPage.getByRole('status')).toContainText('Bid accepted as #1');

    const auctionsResponse = await request.get('/api/v1/auctions?state=LIVE');
    expect(auctionsResponse.ok()).toBeTruthy();
    const auctions = (await auctionsResponse.json()) as {
      content: Array<{ id: string; item: { title: string } }>;
    };
    const auction = auctions.content.find((candidate) => candidate.item.title === title);
    expect(auction).toBeDefined();
    const historyResponse = await request.get(`/api/v1/auctions/${auction?.id}/bids`);
    expect(historyResponse.ok()).toBeTruthy();
    const history = (await historyResponse.json()) as Array<Record<string, unknown>>;
    expect(history).toHaveLength(1);
    expect(history[0]).toMatchObject({ amountCents: 10_000, sequence: 1 });
    expect(history[0].bidderPseudonym).toMatch(/^Bidder-/);
    expect(JSON.stringify(history)).not.toContain(bidderHandle);
    expect(JSON.stringify(history)).not.toContain(bidderEmail);
  } finally {
    await bidderContext.close();
  }
});

async function registerVerifyAndSignIn(
  page: Page,
  request: APIRequestContext,
  email: string,
  handle: string,
  password: string,
): Promise<void> {
  await page.goto('/');
  const messagesBeforeRegistration = await mailpitMessageIds(request);
  await page.getByLabel('Email address').fill(email);
  await page.getByLabel('Public handle').fill(handle);
  await page.getByLabel('Password').fill(password);
  await page.getByRole('button', { name: 'Create account' }).click();
  await page.goto(await mailpitLink(request, email, messagesBeforeRegistration));
  await page.getByRole('button', { name: 'Verify email' }).click();
  await signIn(page, email, password);
  await expect(page.getByText('Trading access is active.')).toBeVisible();
}

async function signIn(page: Page, email: string, password: string): Promise<void> {
  if (page.url() === 'about:blank') await page.goto('/');
  const signInHeading = page.getByRole('heading', { name: 'Sign in', exact: true });
  if (!(await signInHeading.isVisible())) {
    await page.getByRole('button', { name: 'Sign in' }).click();
  }
  await expect(signInHeading).toBeVisible();
  await page.getByLabel('Email address').fill(email);
  await page.getByLabel('Password').fill(password);
  await page.getByRole('button', { name: 'Sign in' }).click();
}

function saoPauloInput(value: Date): string {
  return new Intl.DateTimeFormat('sv-SE', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
    timeZone: 'America/Sao_Paulo',
  })
    .format(value)
    .replace(' ', 'T');
}

function promoteScheduledAuction(auctionId: string): void {
  execFileSync('docker', [
    'compose',
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
    `UPDATE auctions SET state = 'LIVE', starts_at = CURRENT_TIMESTAMP - INTERVAL '1 second' WHERE id = '${auctionId}'::uuid`,
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
