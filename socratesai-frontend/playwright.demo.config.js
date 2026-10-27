import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: './demo-e2e',
  workers: 1,
  timeout: 60000,
  use: { baseURL: 'http://localhost:8088', browserName: 'chromium', channel: process.env.PLAYWRIGHT_CHANNEL || 'chrome',
    viewport: { width: 1440, height: 1050 }, trace: 'off', screenshot: 'off', video: 'off' },
  // Runs ONLY against the explicitly started loopback demo. No public target or mocked page routes.
})
