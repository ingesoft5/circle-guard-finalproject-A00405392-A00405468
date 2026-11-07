import { defineConfig, devices } from '@playwright/test';

/**
 * E2E contra el cliente web desplegado en un ambiente (por defecto el compose local de dev).
 *   BASE_URL=http://<ip-dev> npx playwright test
 * Reportes: HTML en reports/html y JUnit en reports/junit.xml (los publica Jenkins).
 */
export default defineConfig({
  testDir: './tests',
  globalSetup: './global-setup.ts',
  timeout: 60_000,
  expect: { timeout: 15_000 },
  fullyParallel: false,
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  reporter: [
    ['list'],
    ['html', { outputFolder: 'reports/html', open: 'never' }],
    ['junit', { outputFile: 'reports/junit.xml' }],
  ],
  use: {
    baseURL: process.env.BASE_URL ?? 'http://localhost:8090',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
