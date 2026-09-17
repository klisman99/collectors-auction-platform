import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  // The scenarios share the single bootstrapped administrator account, whose
  // production sign-in rate limit intentionally rejects concurrent attempts.
  workers: 1,
  forbidOnly: Boolean(process.env.CI),
  reporter: process.env.CI ? 'github' : 'list',
  retries: process.env.CI ? 2 : 0,
  use: {
    baseURL: process.env.BASE_URL ?? 'http://127.0.0.1:8080',
    trace: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
