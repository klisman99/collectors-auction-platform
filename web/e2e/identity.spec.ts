import { expect, test } from '@playwright/test';

const mailpitUrl = process.env.MAILPIT_URL ?? 'http://127.0.0.1:8025';

test('registers, verifies through Mailpit, and signs in', async ({ page, request }) => {
  const email = `collector-${Date.now()}@example.com`;
  const password = 'a secure passphrase';

  await page.goto('/');
  await page.getByLabel('Email address').fill(email);
  await page.getByLabel('Public handle').fill(`collector_${Date.now()}`);
  await page.getByLabel('Password').fill(password);
  await page.getByRole('button', { name: 'Create account' }).click();
  await expect(page.getByRole('heading', { name: 'Verify your email' })).toBeVisible();

  const tokenLink = await test.step('read verification link from Mailpit', async () => {
    await expect.poll(async () => {
      const response = await request.get(`${mailpitUrl}/api/v1/messages`);
      if (!response.ok()) return '';
      const body = await response.json() as { messages?: Array<{ ID: string }> };
      const message = body.messages?.[0];
      if (!message) return '';
      const detail = await request.get(`${mailpitUrl}/api/v1/message/${message.ID}`);
      if (!detail.ok()) return '';
      const content = await detail.json() as { HTML?: string; Text?: string };
      return `${content.HTML ?? ''}\n${content.Text ?? ''}`.match(/https?:\/\/[^\s"<>]+verificationToken=[^\s"<>]+/)?.[0] ?? '';
    }, { timeout: 30_000 }).not.toBe('');

    const response = await request.get(`${mailpitUrl}/api/v1/messages`);
    const body = await response.json() as { messages: Array<{ ID: string }> };
    const detail = await request.get(`${mailpitUrl}/api/v1/message/${body.messages[0].ID}`);
    const content = await detail.json() as { HTML?: string; Text?: string };
    return `${content.HTML ?? ''}\n${content.Text ?? ''}`.match(/https?:\/\/[^\s"<>]+verificationToken=[^\s"<>]+/)?.[0] as string;
  });

  await page.goto(tokenLink);
  await expect(page.getByRole('heading', { name: 'Verify your email' })).toBeVisible();
  await page.getByRole('button', { name: 'Verify email' }).click();
  await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible();
  await page.getByLabel('Email address').fill(email);
  await page.getByLabel('Password').fill(password);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page.getByText('Trading access is active.')).toBeVisible();
});
