import { expect, test, type APIRequestContext, type Browser, type Page } from '@playwright/test';

const mailpitUrl = process.env.MAILPIT_URL ?? 'http://127.0.0.1:8025';
const administratorEmail = process.env.INITIAL_ADMINISTRATOR_EMAIL ?? 'admin-e2e@example.com';
const administratorPassword = process.env.INITIAL_ADMINISTRATOR_PASSWORD ?? 'e2e administrator password';

test('administrator can invite, activate, and deactivate a non-trading moderator', async ({ browser, page, request }) => {
  const email = `moderator-${Date.now()}@example.com`;
  const password = 'a moderator e2e password';

  await signInAsAdministrator(page);
  await page.getByLabel('Email address').fill(email);
  await page.getByLabel('Public reason').first().fill('Provide weekend moderation coverage');
  await page.getByRole('button', { name: 'Send invitation' }).click();
  await expect(page.getByText(email)).toBeVisible();

  const activationLink = await mailpitLink(request, 'operationalActivationToken');
  await page.goto(activationLink);
  await page.getByLabel('New password').fill(password);
  await page.getByLabel('Confirm new password').fill(password);
  await page.getByRole('button', { name: 'Activate account' }).click();
  await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible();

  await page.getByLabel('Email address').fill(email);
  await page.getByLabel('Password').fill(password);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page.getByText('dedicated operations identity')).toBeVisible();

  const administratorContext = await browser.newContext();
  try {
    const administratorPage = await administratorContext.newPage();
    await signInAsAdministrator(administratorPage);
    await administratorPage.getByLabel('Account').selectOption({ label: `${email} · MODERATOR` });
    await administratorPage.getByLabel('Public reason', { exact: true }).last().fill('End of operational assignment');
    await administratorPage.getByRole('button', { name: 'Deactivate account' }).click();
    await expect(administratorPage.getByText('DEACTIVATED')).toBeVisible();
  } finally {
    await administratorContext.close();
  }

  await page.reload();
  await expect(page.getByRole('heading', { name: 'Start collecting with confidence.' })).toBeVisible();
});

async function signInAsAdministrator(page: Page): Promise<void> {
  await page.goto('/');
  await page.getByRole('button', { name: 'Sign in' }).click();
  await page.getByLabel('Email address').fill(administratorEmail);
  await page.getByLabel('Password').fill(administratorPassword);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page.getByRole('heading', { name: 'Manage operational accounts' })).toBeVisible();
}

async function mailpitLink(request: APIRequestContext, queryParameter: string): Promise<string> {
  let link = '';
  await expect.poll(async () => {
    const response = await request.get(`${mailpitUrl}/api/v1/messages`);
    if (!response.ok()) return '';
    const body = await response.json() as { messages?: Array<{ ID: string }> };
    for (const message of body.messages ?? []) {
      const detail = await request.get(`${mailpitUrl}/api/v1/message/${message.ID}`);
      if (!detail.ok()) continue;
      const content = await detail.json() as { HTML?: string; Text?: string };
      link = `${content.HTML ?? ''}\n${content.Text ?? ''}`.match(
        new RegExp(`https?:\\/\\/[^\\s"<>]+${queryParameter}=[^\\s"<>]+`),
      )?.[0] ?? '';
      if (link !== '') return link;
    }
    return '';
  }, { timeout: 30_000 }).not.toBe('');
  return link;
}
