import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './tests',
  timeout: 30 * 1000,
  expect: {
    timeout: 5000
  },
  fullyParallel: true,
  retries: 1,
  workers: 1, // Individual container workers handle concurrency via our orchestrator
  reporter: [
    ['list'],
    ['json', { outputFile: 'test-results/summary.json' }],
    ['html', { outputFolder: 'test-results/html', open: 'always' }]
  ],
  use: {
    actionTimeout: 0,
    trace: 'retain-on-failure',
    video: 'retain-on-failure',
    screenshot: 'only-on-failure',
    headless: true,
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
  outputDir: 'test-results/',
});
