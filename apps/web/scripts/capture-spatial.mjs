/** Production UI captures. Private records use the explicit synthetic OIDC/API harness, NOT Java/PostgreSQL. */
import { spawn } from "node:child_process";
import { mkdir, writeFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { chromium, expect } from "@playwright/test";

const web = fileURLToPath(new URL("../", import.meta.url));
const output = new URL(
  "../../../docs/delivery/screenshots/spatial/",
  import.meta.url,
);
await mkdir(output, { recursive: true });
const env = {
  ...process.env,
  NEXT_TELEMETRY_DISABLED: "1",
  OULA_BACKEND_URL: "http://127.0.0.1:8099",
  OULA_APP_ORIGIN: "http://127.0.0.1:3000",
  OULA_OIDC_ISSUER: "http://127.0.0.1:8099",
  OULA_OIDC_CLIENT_ID: "oula-web",
  OULA_SESSION_SECRET:
    "synthetic-spatial-capture-only-secret-at-least-32-characters",
  OULA_ALLOW_HTTP_LOCALHOST: "true",
};
const children = [
  spawn(process.execPath, ["e2e/fixtures/contract-server.mjs"], {
    cwd: web,
    env,
    stdio: "ignore",
  }),
  spawn(
    process.execPath,
    [
      "node_modules/next/dist/bin/next",
      "start",
      "-H",
      "127.0.0.1",
      "-p",
      "3000",
    ],
    { cwd: web, env, stdio: "ignore" },
  ),
];
let browser;
const checks = [];
async function ready(url) {
  const until = Date.now() + 15000;
  while (Date.now() < until) {
    try {
      if ((await fetch(url)).ok) return;
    } catch {}
    await new Promise((resolve) => setTimeout(resolve, 150));
  }
  throw new Error("Capture service did not become ready");
}
async function capture(page, name, source) {
  await page.evaluate(async () => {
    await document.fonts.ready;
    const image = new Image();
    image.src = "/visuals/spatial/riyadh-canvas.webp";
    await image.decode();
  });
  await expect(page.locator(".spatial-main")).toHaveCSS("opacity", "1");
  await page.screenshot({
    path: fileURLToPath(new URL(name + ".jpg", output)),
    type: "jpeg",
    quality: 86,
    fullPage: true,
  });
  checks.push({
    name,
    path: new URL(page.url()).pathname + new URL(page.url()).search,
    source,
    viewport: page.viewportSize(),
    horizontalOverflow: await page.evaluate(
      () => document.documentElement.scrollWidth - innerWidth,
    ),
  });
}
try {
  await ready("http://127.0.0.1:8099/health");
  await ready("http://127.0.0.1:3000/gateway");
  browser = await chromium.launch({
    headless: true,
    ...(process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH
      ? { executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH }
      : {}),
  });
  const context = await browser.newContext({
    viewport: { width: 1536, height: 1000 },
    reducedMotion: "reduce",
  });
  const page = await context.newPage();
  const errors = [];
  page.on("pageerror", (error) => errors.push(error.message));
  for (const world of ["pulse", "habitat", "nexus", "forge", "civic"]) {
    await page.goto(`http://127.0.0.1:3000/gateway/${world}?lang=ar`);
    await capture(
      page,
      "oula-" + world + "-desktop",
      "PUBLIC_UI_NO_PRIVATE_RECORDS",
    );
  }
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto(
    "http://127.0.0.1:3000/gateway/habitat?section=obligations&branch=add&role=manager&lang=ar",
  );
  await capture(page, "oula-habitat-mobile", "PUBLIC_UI_NO_PRIVATE_RECORDS");
  await page.setViewportSize({ width: 1536, height: 1000 });
  await page.goto(
    "http://127.0.0.1:3000/api/auth/login?returnTo=" +
      encodeURIComponent(
        "/workspace?world=PULSE&view=passport&lang=ar&connection=server",
      ),
  );
  await page
    .getByLabel("معرّف العقار", { exact: true })
    .fill("123e4567-e89b-42d3-a456-426614174003");
  await page.getByRole("button", { name: "قراءة السجل", exact: true }).click();
  await expect(page.getByTestId("spatial-passport")).toBeVisible();
  await capture(
    page,
    "oula-property-passport",
    "SYNTHETIC_CONTRACT_HARNESS_NOT_JAVA_POSTGRES",
  );
  await page.setViewportSize({ width: 390, height: 844 });
  await capture(
    page,
    "oula-property-passport-mobile",
    "SYNTHETIC_CONTRACT_HARNESS_NOT_JAVA_POSTGRES",
  );
  await page
    .getByRole("button", { name: "افتح مساعد عُلا", exact: true })
    .click();
  await capture(
    page,
    "oula-assistant-mobile",
    "SYNTHETIC_CONTRACT_HARNESS_NOT_JAVA_POSTGRES",
  );
  await page.keyboard.press("Escape");
  await page.setViewportSize({ width: 1536, height: 1000 });
  await page.goto(
    "http://127.0.0.1:3000/workspace?world=NEXUS&view=deal&lang=ar&connection=server",
  );
  await page
    .getByLabel("افتح صفقة محفوظة", { exact: true })
    .fill("123e4567-e89b-42d3-a456-426614174008");
  await page
    .getByRole("button", { name: "أعد قراءة الصفقة", exact: true })
    .click();
  await expect(page.getByTestId("spatial-deal-room")).toBeVisible();
  await capture(
    page,
    "oula-deal-room",
    "SYNTHETIC_CONTRACT_HARNESS_NOT_JAVA_POSTGRES",
  );
  await page.goto(
    "http://127.0.0.1:3000/workspace?world=PULSE&view=decision&lang=ar&connection=server",
  );
  await capture(
    page,
    "oula-decision-space",
    "SYNTHETIC_CONTRACT_HARNESS_NOT_JAVA_POSTGRES",
  );
  await writeFile(
    new URL("../../SPATIAL_VISUAL_CHECKS_20261019.json", output),
    JSON.stringify({ checks, pageErrors: errors }, null, 2) + "\n",
  );
  console.log(JSON.stringify({ checks, pageErrors: errors }, null, 2));
  if (errors.length || checks.some((check) => check.horizontalOverflow > 1))
    process.exitCode = 1;
} finally {
  await browser?.close();
  for (const child of children) child.kill("SIGTERM");
}
