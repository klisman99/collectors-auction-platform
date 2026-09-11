import { type APIRequestContext, expect, test } from '@playwright/test';

const mailpitUrl = process.env.MAILPIT_URL ?? 'http://127.0.0.1:8025';

test('recovers a password and rejects every existing session on its next request', async ({
  browser,
  page,
  request,
}) => {
  const email = `collector-${Date.now()}@example.com`;
  const password = 'a secure passphrase';

  await page.goto('/');
  await page.getByLabel('Email address').fill(email);
  await page.getByLabel('Public handle').fill(`collector_${Date.now()}`);
  await page.getByLabel('Password').fill(password);
  await page.getByRole('button', { name: 'Create account' }).click();
  await expect(page.getByRole('heading', { name: 'Verify your email' })).toBeVisible();

  const tokenLink = await mailpitLink(request, 'verificationToken');

  await page.goto(tokenLink);
  await expect(page.getByRole('heading', { name: 'Verify your email' })).toBeVisible();
  await page.getByRole('button', { name: 'Verify email' }).click();
  await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible();
  await page.getByLabel('Email address').fill(email);
  await page.getByLabel('Password').fill(password);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page.getByText('Trading access is active.')).toBeVisible();

  const existingSessionContext = await browser.newContext();
  const recoveryContext = await browser.newContext();
  try {
    const existingSessionPage = await existingSessionContext.newPage();
    await existingSessionPage.goto('/');
    await existingSessionPage.getByRole('button', { name: 'Sign in' }).click();
    await existingSessionPage.getByLabel('Email address').fill(email);
    await existingSessionPage.getByLabel('Password').fill(password);
    await existingSessionPage.getByRole('button', { name: 'Sign in' }).click();
    await expect(existingSessionPage.getByText('Trading access is active.')).toBeVisible();

    const recoveryPage = await recoveryContext.newPage();
    await recoveryPage.goto('/');
    await recoveryPage.getByRole('button', { name: 'Sign in' }).click();
    await recoveryPage.getByRole('button', { name: 'Forgot password?' }).click();
    await recoveryPage.getByLabel('Email address').fill(email);
    await recoveryPage.getByRole('button', { name: 'Send recovery link' }).click();
    await expect(recoveryPage.getByRole('status')).toContainText('If an account exists');

    await recoveryPage.goto(await mailpitLink(request, 'recoveryToken'));
    const newPassword = 'a replacement passphrase';
    await recoveryPage.getByLabel('New password', { exact: true }).fill(newPassword);
    await recoveryPage.getByLabel('Confirm new password').fill(newPassword);
    await recoveryPage.getByRole('button', { name: 'Reset password' }).click();
    await expect(recoveryPage.getByRole('heading', { name: 'Sign in' })).toBeVisible();

    await page.reload();
    await expect(
      page.getByRole('heading', { name: 'Start collecting with confidence.' }),
    ).toBeVisible();
    await existingSessionPage.reload();
    await expect(
      existingSessionPage.getByRole('heading', { name: 'Start collecting with confidence.' }),
    ).toBeVisible();

    await recoveryPage.getByLabel('Email address').fill(email);
    await recoveryPage.getByLabel('Password').fill(newPassword);
    await recoveryPage.getByRole('button', { name: 'Sign in' }).click();
    await expect(recoveryPage.getByText('Trading access is active.')).toBeVisible();
  } finally {
    await recoveryContext.close();
    await existingSessionContext.close();
  }
});

async function mailpitLink(request: APIRequestContext, queryParameter: string): Promise<string> {
  let link = '';
  await expect
    .poll(
      async () => {
        const response = await request.get(`${mailpitUrl}/api/v1/messages`);
        if (!response.ok()) return '';
        const body = (await response.json()) as { messages?: Array<{ ID: string }> };
        for (const message of body.messages ?? []) {
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
