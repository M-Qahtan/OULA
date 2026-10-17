import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "./e2e",
  testMatch: "**/*.e2e.ts",
  timeout: 45_000,
  expect: { timeout: 7_500 },
  fullyParallel: false,
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [["line"]] : [["list"]],
  use: {
    baseURL: "http://127.0.0.1:3000",
    browserName: "chromium",
    launchOptions: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH
      ? { executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH }
      : undefined,
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
  },
  webServer: [
    {
      command: "node e2e/fixtures/contract-server.mjs",
      url: "http://127.0.0.1:8099/health",
      reuseExistingServer: false,
    },
    {
      command: "npm run start -- -H 127.0.0.1 -p 3000",
      url: "http://127.0.0.1:3000",
      timeout: 120_000,
      reuseExistingServer: !process.env.CI,
      env: {
        NEXT_TELEMETRY_DISABLED: "1",
        OULA_BACKEND_URL: "http://127.0.0.1:8099",
        OULA_APP_ORIGIN: "http://127.0.0.1:3000",
        OULA_OIDC_ISSUER: "http://127.0.0.1:8099",
        OULA_OIDC_CLIENT_ID: "oula-web",
        OULA_SESSION_SECRET:
          "synthetic-contract-test-secret-at-least-32-characters",
        OULA_ALLOW_HTTP_LOCALHOST: "true",
      },
    },
  ],
});
