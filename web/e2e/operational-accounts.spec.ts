import { type APIRequestContext, expect, type Page, test } from '@playwright/test';

const mailpitUrl = process.env.MAILPIT_URL ?? 'http://127.0.0.1:8025';
const administratorEmail = process.env.INITIAL_ADMINISTRATOR_EMAIL ?? 'admin-e2e@example.com';
const administratorPassword =
  process.env.INITIAL_ADMINISTRATOR_PASSWORD ?? 'e2e administrator password';

test('administrator can invite, activate, and deactivate a non-trading moderator', async ({
  browser,
  page,
  request,
}) => {
  test.setTimeout(60_000);
  const email = `moderator-${Date.now()}@example.com`;
  const password = 'a moderator e2e password';

  await signInAsAdministrator(page);
  const invitationForm = page
    .locator('form')
    .filter({ has: page.getByRole('heading', { name: 'Invite an operational account' }) });
  await invitationForm.getByLabel('Email address').fill(email);
  await invitationForm.getByLabel('Public reason').fill('Provide weekend moderation coverage');
  const messageIdsBeforeInvitation = await mailpitMessageIds(request);
  await invitationForm.getByRole('button', { name: 'Send invitation' }).click();
  await expect(page.getByRole('cell', { name: email })).toBeVisible();

  const activationLink = await mailpitLink(
    request,
    'operationalActivationToken',
    email,
    messageIdsBeforeInvitation,
  );
  await page.goto(activationLink);
  await page.getByLabel('New password', { exact: true }).fill(password);
  await page.getByLabel('Confirm new password', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Activate account' }).click();
  await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible();

  await page.getByLabel('Email address').fill(email);
  await page.getByLabel('Password').fill(password);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page.getByRole('heading', { name: 'Moderator access is active.' })).toBeVisible();

  const administratorContext = await browser.newContext();
  try {
    const administratorPage = await administratorContext.newPage();
    await signInAsAdministrator(administratorPage);
    const deactivationForm = administratorPage
      .locator('form')
      .filter({ has: administratorPage.getByRole('heading', { name: 'Deactivate an account' }) });
    await deactivationForm
      .getByLabel('Account', { exact: true })
      .selectOption({ label: `${email} · MODERATOR · ACTIVE` });
    await deactivationForm.getByLabel('Public reason').fill('End of operational assignment');
    await deactivationForm.getByRole('button', { name: 'Deactivate account' }).click();
    await expect(
      administratorPage.getByText('OPERATIONAL_ACCOUNT_DEACTIVATED', { exact: true }),
    ).toBeVisible();
  } finally {
    await administratorContext.close();
  }

  await page.reload();
  await expect(
    page.getByRole('heading', { name: 'Start collecting with confidence.' }),
  ).toBeVisible();
});

async function signInAsAdministrator(page: Page): Promise<void> {
  await page.goto('/');
  await page.getByRole('button', { name: 'Sign in' }).click();
  await page.getByLabel('Email address').fill(administratorEmail);
  await page.getByLabel('Password').fill(administratorPassword);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page.getByRole('heading', { name: 'Manage operational accounts' })).toBeVisible();
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
