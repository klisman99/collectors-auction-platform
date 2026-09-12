import { type APIRequestContext, expect, type Page, test } from '@playwright/test';

const mailpitUrl = process.env.MAILPIT_URL ?? 'http://127.0.0.1:8025';
const administratorEmail = process.env.INITIAL_ADMINISTRATOR_EMAIL ?? 'admin-e2e@example.com';
const administratorPassword =
  process.env.INITIAL_ADMINISTRATOR_PASSWORD ?? 'e2e administrator password';

test('seller publishes, administrator suspends and cancels, and the public timeline explains why', async ({
  browser,
  page,
  request,
}) => {
  test.setTimeout(90_000);
  const unique = Date.now();
  const email = `auction-seller-${unique}@example.com`;
  const password = 'an auction seller password';
  const title = `Rare collector card ${unique}`;

  await page.goto('/');
  await page.getByLabel('Email address').fill(email);
  await page.getByLabel('Public handle').fill(`auction_${unique}`);
  await page.getByLabel('Password').fill(password);
  const messagesBeforeRegistration = await mailpitMessageIds(request);
  await page.getByRole('button', { name: 'Create account' }).click();
  await page.goto(
    await mailpitLink(request, 'verificationToken', email, messagesBeforeRegistration),
  );
  await page.getByRole('button', { name: 'Verify email' }).click();
  await page.getByLabel('Email address').fill(email);
  await page.getByLabel('Password').fill(password);
  await page.getByRole('button', { name: 'Sign in' }).click();

  await page.getByLabel('Draft title').fill(title);
  await page
    .getByLabel('Description')
    .fill('A complete description of this rare physical collector card.');
  await page
    .getByLabel('Condition notes')
    .fill('Excellent condition with careful archival storage.');
  await page.getByLabel('Ownership declaration').check();
  await page.getByRole('button', { name: 'Create draft' }).click();
  await page.getByLabel('Draft image').setInputFiles({
    name: 'collector-card.png',
    mimeType: 'image/png',
    buffer: Buffer.from(
      'iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAIAAAD91JpzAAAAFElEQVR4nGP8z8DAwMDAxMDAwMDAAAANHQEDasKb6QAAAABJRU5ErkJggg==',
      'base64',
    ),
  });
  await expect(page.getByText('1/5 images')).toBeVisible();
  await page.getByRole('button', { name: 'Submit for review' }).click();
  await expect(page.getByText('Submitted for moderation.')).toBeVisible();

  const interventionContext = await browser.newContext();
  try {
    const administratorPage = await interventionContext.newPage();
    await signInAsAdministrator(administratorPage);
    const submission = administratorPage
      .getByRole('listitem')
      .filter({ hasText: title })
      .filter({ has: administratorPage.getByRole('button', { name: 'Approve' }) });
    await expect(submission).toBeVisible();
    await submission.getByRole('button', { name: 'Approve' }).click();
    await expect(administratorPage.getByText('Collectible approved.')).toBeVisible();
  } finally {
    await interventionContext.close();
  }

  await page.reload();
  await page.getByRole('button', { name: `Schedule auction for ${title}` }).click();
  await page.getByLabel('Opening amount').fill('100.00');
  await page.getByLabel('Minimum increment').fill('10.00');
  await page.getByLabel('Optional reserve').fill('150.00');
  const startsAt = new Date(Date.now() + 20 * 60_000);
  const endsAt = new Date(startsAt.getTime() + 2 * 60 * 60_000);
  await page.getByLabel('Start in São Paulo').fill(saoPauloInput(startsAt));
  await page.getByLabel('End in São Paulo').fill(saoPauloInput(endsAt));
  await page.getByRole('button', { name: 'Publish auction' }).click();

  await expect(page.getByRole('heading', { name: 'Published snapshot' })).toBeVisible();
  await expect(page.getByText('Locked after publication')).toBeVisible();
  await expect(page.getByText('Editable before start')).toBeVisible();
  await expect(page.getByText('R$ 100,00', { exact: true })).toBeVisible();

  const messagesBeforeCancellation = await mailpitMessageIds(request);
  const administratorContext = await browser.newContext();
  try {
    const administratorPage = await administratorContext.newPage();
    await signInAsAdministrator(administratorPage);
    const intervention = administratorPage.getByRole('region', { name: 'Auction intervention' });
    await intervention
      .getByLabel('Auction', { exact: true })
      .selectOption({ label: `${title} — SCHEDULED` });
    await intervention.getByLabel('Reason category').selectOption('ITEM_CONCERN');
    await intervention
      .getByLabel('Public reason')
      .fill('The collectible requires an authenticity review.');
    await intervention
      .getByLabel('Internal note (optional)')
      .fill('Escalated by the operations team.');
    await intervention.getByRole('button', { name: 'Suspend auction' }).click();
    await expect(administratorPage.getByRole('status')).toContainText('Auction suspended.');
    const suspendedAuction = intervention.getByRole('listitem').filter({ hasText: title });
    await expect(suspendedAuction.getByText('SCHEDULED · seller')).toBeVisible();
    await expect(suspendedAuction.getByText('Start blocked')).toBeVisible();

    await intervention
      .getByLabel('Public reason')
      .fill('The collectible approval was revoked after review.');
    await intervention
      .getByLabel('Cancellation item disposition')
      .selectOption('REVOKE_APPROVAL_TO_DRAFT');
    await suspendedAuction.getByRole('button', { name: 'Cancel auction' }).click();
    await expect(administratorPage.getByRole('status')).toContainText('Auction cancelled.');
  } finally {
    await administratorContext.close();
  }
  await expect
    .poll(async () => (await mailpitMessageIds(request)).size, { timeout: 30_000 })
    .toBeGreaterThan(messagesBeforeCancellation.size);

  const visitorContext = await browser.newContext();
  try {
    const visitorPage = await visitorContext.newPage();
    await visitorPage.goto('/');
    await visitorPage.getByRole('tab', { name: 'Ended auctions' }).click();
    await expect(visitorPage.getByText(title)).toBeVisible();
    await visitorPage.getByRole('button', { name: `View ${title}` }).click();
    await expect(
      visitorPage.getByText('The collectible approval was revoked after review.'),
    ).toBeVisible();
    await expect(visitorPage.getByText('Escalated by the operations team.')).not.toBeVisible();
  } finally {
    await visitorContext.close();
  }
});

async function signInAsAdministrator(page: Page): Promise<void> {
  await page.goto('/');
  await page.getByRole('button', { name: 'Sign in' }).click();
  await page.getByLabel('Email address').fill(administratorEmail);
  await page.getByLabel('Password').fill(administratorPassword);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page.getByRole('heading', { name: 'Manage operational accounts' })).toBeVisible();
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

async function mailpitMessageIds(request: APIRequestContext): Promise<Set<string>> {
  const response = await request.get(`${mailpitUrl}/api/v1/messages`);
  if (!response.ok()) return new Set();
  const body = (await response.json()) as { messages?: Array<{ ID: string }> };
  return new Set((body.messages ?? []).map((message) => message.ID));
}

async function mailpitLink(
  request: APIRequestContext,
  queryParameter: string,
  recipient: string,
  ignoredMessageIds: Set<string>,
): Promise<string> {
  let link = '';
  await expect
    .poll(
      async () => {
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
              new RegExp(`https?:\\/\\/[^\\s"<>]+${queryParameter}=[^\\s"<>]+`),
            )?.[0] ?? '';
          if (link !== '') return link;
        }
        return '';
      },
      { timeout: 30_000 },
    )
    .not.toBe('');
  return link;
}
