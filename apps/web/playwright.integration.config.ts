import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "./e2e-integration",
  testMatch: "**/*.integration.ts",
  timeout: 90_000,
  expect: { timeout: 12_000 },
  fullyParallel: false,
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [["line"]] : [["list"]],
  use: {
    baseURL: "http://127.0.0.1:3000",
    browserName: "chromium",
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
  },
  webServer: {
    command: "npm run start -- -p 3000",
    url: "http://127.0.0.1:3000",
    timeout: 120_000,
    reuseExistingServer: !process.env.CI,
    env: {
      NEXT_TELEMETRY_DISABLED: "1",
      NEXT_PUBLIC_OULA_DEV_AUTH: "true",
      NEXT_PUBLIC_OULA_API_BASE_URL: "http://127.0.0.1:8080",
    },
  },
});
