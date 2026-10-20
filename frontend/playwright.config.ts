import { defineConfig, devices } from '@playwright/test'

/**
 * E2E chạy trên stack thật: compose profile `core`, `app` :8081, `gateway` :8080, `npm run dev` :5173
 * (CI dựng stack này trước khi chạy). Hai khổ màn hình theo yêu cầu đối chiếu prototype: 1280px và 390px.
 */
export default defineConfig({
  testDir: './e2e',
  outputDir: './test-results',
  reporter: [['list'], ['html', { open: 'never', outputFolder: 'playwright-report' }]],
  // Lần đầu sau khi dựng stack, Keycloak và Gateway chậm: 5 giây mặc định không đủ.
  expect: { timeout: 15_000 },
  fullyParallel: false,
  workers: 1,
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:5173',
    trace: 'retain-on-failure',
    locale: 'vi-VN',
  },
  projects: [
    { name: 'desktop-1280', use: { ...devices['Desktop Chrome'], viewport: { width: 1280, height: 800 } } },
    {
      name: 'mobile-390',
      use: { ...devices['Desktop Chrome'], viewport: { width: 390, height: 844 }, hasTouch: true },
    },
  ],
})
