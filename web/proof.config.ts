import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './proof',
  workers: 1,
  retries: 0,
  timeout: 600_000,
  expect: { timeout: 60_000 },
  outputDir: '../artifacts/proof/results',
  reporter: [['list'], ['json', { outputFile: '../artifacts/proof/results.json' }]],
  // Requests contain credentials and single-use tokens. Persist assertions and
  // sanitized measurements, never request traces or authentication state.
  use: { baseURL: process.env.BASE_URL ?? 'http://127.0.0.1:18080', trace: 'off' },
});
