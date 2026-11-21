import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  forbidOnly: !!process.env.CI,
  workers: 1,
  use: {baseURL: 'http://127.0.0.1:5173', timezoneId: 'America/Sao_Paulo', trace: 'retain-on-failure'},
  webServer: {command: 'npm run serve', url: 'http://127.0.0.1:5173', reuseExistingServer: false},
});
