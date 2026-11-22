import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  forbidOnly: !!process.env.CI,
  workers: 1,
  reporter: [['list'], ['html', {outputFolder: 'playwright-report', open: 'never'}]],
  outputDir: 'test-results',
  use: {baseURL: 'http://127.0.0.1:5173', timezoneId: 'America/Sao_Paulo', trace: 'retain-on-failure'},
  projects: [
    {name: 'chromium-desktop', use: {...devices['Desktop Chrome'], browserName: 'chromium'}},
    {name: 'webkit-desktop', use: {...devices['Desktop Safari'], browserName: 'webkit'}},
    {name: 'webkit-iphone', use: {...devices['iPhone 13'], browserName: 'webkit'}},
    {name: 'webkit-mobile-compact', use: {browserName: 'webkit', viewport: {width: 320, height: 568}, deviceScaleFactor: 2, isMobile: true, hasTouch: true}},
    {name: 'webkit-mobile-large', use: {browserName: 'webkit', viewport: {width: 430, height: 932}, deviceScaleFactor: 3, isMobile: true, hasTouch: true}},
  ],
  webServer: {command: 'npm run serve', url: 'http://127.0.0.1:5173', reuseExistingServer: false},
});
