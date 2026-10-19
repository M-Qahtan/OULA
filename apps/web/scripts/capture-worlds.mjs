/** Public UI evidence from a production build. No authentication or simulated records. */
import { spawn } from "node:child_process";
import { mkdir, writeFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { chromium } from "@playwright/test";

const output = new URL("../../../docs/delivery/screenshots/", import.meta.url);
await mkdir(output, { recursive: true });
const server = spawn(
  process.execPath,
  [
    fileURLToPath(
      new URL("../node_modules/next/dist/bin/next", import.meta.url),
    ),
    "start",
    "-H",
    "127.0.0.1",
    "-p",
    "3001",
  ],
  {
    cwd: fileURLToPath(new URL("../", import.meta.url)),
    stdio: ["ignore", "pipe", "pipe"],
    env: { ...process.env, NEXT_TELEMETRY_DISABLED: "1" },
  },
);
let browser;
try {
  await new Promise((resolve, reject) => {
    let log = "";
    const timeout = setTimeout(
      () => reject(new Error("Next startup timed out: " + log)),
      10000,
    );
    function receive(chunk) {
      log += chunk;
      if (log.includes("Ready in")) {
        clearTimeout(timeout);
        resolve();
      }
    }
    server.stdout.on("data", receive);
    server.stderr.on("data", receive);
    server.once("error", (error) => {
      clearTimeout(timeout);
      reject(error);
    });
    server.once("exit", (code) => {
      clearTimeout(timeout);
      reject(new Error("Next exited " + code + ": " + log));
    });
  });
  browser = await chromium.launch({
    headless: true,
    ...(process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH
      ? { executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH }
      : {}),
  });
  const cases = [
    ["oula-gateway-desktop-20261019", "/gateway", 1536, 864],
    ["oula-gateway-mobile-20261019", "/gateway", 390, 844],
    [
      "oula-pulse-passport-20261019",
      "/gateway/pulse?section=passport&branch=evidence&lang=ar",
      1280,
      900,
    ],
    [
      "oula-habitat-care-20261019",
      "/gateway/habitat?section=obligations&branch=add&role=manager&lang=ar",
      1280,
      900,
    ],
    [
      "oula-nexus-deals-20261019",
      "/gateway/nexus?section=deals&branch=open&role=broker&lang=ar",
      1280,
      900,
    ],
    [
      "oula-forge-scope-20261019",
      "/gateway/forge?section=simulation&branch=wind&role=engineer&lang=en",
      1280,
      900,
    ],
    [
      "oula-civic-scope-20261019",
      "/gateway/civic?section=quality&branch=indicators&role=government&lang=ar",
      1280,
      900,
    ],
  ];
  const checks = [];
  for (const [name, path, width, height] of cases) {
    const page = await browser.newPage({
      viewport: { width, height },
      reducedMotion: "reduce",
    });
    const errors = [];
    page.on("pageerror", (error) => errors.push(error.message));
    await page.goto("http://127.0.0.1:3001" + path);
    await page.locator(".og-portal, .ow-task-detail h3").first().waitFor();
    await page.evaluate(async () => {
      await document.fonts.ready;
      await Promise.all(
        Array.from(document.images).map((image) => image.decode()),
      );
    });
    await page.screenshot({
      path: fileURLToPath(new URL(name + ".jpg", output)),
      type: "jpeg",
      quality: 88,
      fullPage: true,
    });
    checks.push({
      name,
      path,
      width,
      height,
      horizontalOverflow: await page.evaluate(
        () => document.documentElement.scrollWidth - innerWidth,
      ),
      pageErrors: errors,
    });
    await page.close();
  }
  await writeFile(
    new URL("../WORLDS_VISUAL_CHECKS_20261019.json", output),
    JSON.stringify(checks, null, 2) + "\n",
  );
  console.log(JSON.stringify(checks, null, 2));
  if (
    checks.some(
      (check) => check.horizontalOverflow > 1 || check.pageErrors.length,
    )
  )
    process.exitCode = 1;
} finally {
  await browser?.close();
  server.kill("SIGTERM");
}
