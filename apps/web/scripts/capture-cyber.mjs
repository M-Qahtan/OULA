/** Production UI captures. Private records use the explicit synthetic OIDC/API harness, NOT Java/PostgreSQL. */
import { execFileSync, spawn } from "node:child_process";
import {
  mkdir,
  mkdtemp,
  readFile,
  rename,
  rm,
  writeFile,
} from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { fileURLToPath } from "node:url";
import { chromium, expect } from "@playwright/test";

const web = fileURLToPath(new URL("../", import.meta.url));
const output = new URL(
  "../../../docs/delivery/screenshots/cyber/",
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
const video = process.argv.includes("--video");
const smoke = process.argv.includes("--smoke") || video;
const videoDirectory = video
  ? await mkdtemp(join(tmpdir(), "oula-cyber-video-"))
  : undefined;
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
  const scene = page.getByTestId("spatial-environment").first();
  await scene.scrollIntoViewIfNeeded();
  await expect(scene).toHaveAttribute("data-renderer", "interactive", {
    timeout: 20000,
  });
  await expect
    .poll(async () => Number(await scene.getAttribute("data-frames")))
    .toBeGreaterThanOrEqual(3);
  await page.evaluate(() => window.scrollTo({ top: 0, behavior: "instant" }));
  await expect.poll(() => page.evaluate(() => scrollY)).toBe(0);
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
    renderer: await page
      .getByTestId("spatial-environment")
      .first()
      .getAttribute("data-renderer"),
    drawCalls: await page
      .getByTestId("spatial-environment")
      .first()
      .getAttribute("data-draw-calls"),
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
    args:
      process.env.OULA_TEST_SOFTWARE_WEBGL === "1"
        ? [
            "--use-gl=angle",
            "--use-angle=swiftshader",
            "--enable-unsafe-swiftshader",
          ]
        : [],
    ...(process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH
      ? { executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH }
      : {}),
  });
  const context = await browser.newContext({
    viewport: { width: 1536, height: 1000 },
    reducedMotion: video ? "no-preference" : "reduce",
    ...(videoDirectory
      ? {
          recordVideo: {
            dir: videoDirectory,
            size: { width: 1536, height: 1000 },
          },
        }
      : {}),
  });
  const videoEpoch = Date.now();
  const page = await context.newPage();
  const errors = [];
  page.on("pageerror", (error) => errors.push(error.message));
  page.on("requestfailed", (request) =>
    errors.push({
      type: "request",
      url: request.url(),
      failure: request.failure(),
    }),
  );
  page.on("console", (message) => {
    if (message.type() === "error")
      errors.push({
        type: "console",
        message: message.text(),
        location: message.location(),
      });
  });
  for (const world of smoke
    ? []
    : ["pulse", "habitat", "nexus", "forge", "civic"]) {
    await page.goto(`http://127.0.0.1:3000/gateway/${world}?lang=ar`);
    await capture(
      page,
      "oula-" + world + "-desktop",
      "PUBLIC_UI_NO_PRIVATE_RECORDS",
    );
  }
  if (!smoke) {
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto(
      "http://127.0.0.1:3000/gateway/habitat?section=obligations&branch=add&role=manager&lang=ar",
    );
    await capture(page, "oula-habitat-mobile", "PUBLIC_UI_NO_PRIVATE_RECORDS");
  }
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
  if (!video)
    await capture(
      page,
      "oula-property-passport",
      "SYNTHETIC_CONTRACT_HARNESS_NOT_JAVA_POSTGRES",
    );
  if (video) {
    const scene = page.locator(".spatial-passport .spatial-scene");
    await scene.scrollIntoViewIfNeeded();
    await expect(
      page.getByTestId("spatial-environment").first(),
    ).toHaveAttribute("data-renderer", "interactive");
    const box = await scene.boundingBox();
    if (!box) throw new Error("Spatial scene not available for recording");
    const recording = page.video();
    if (!recording) throw new Error("Browser recording unavailable");
    const from = (Date.now() - videoEpoch) / 1000;
    const dwell = (milliseconds) =>
      new Promise((resolve) => setTimeout(resolve, milliseconds));
    await dwell(1200);
    await page.mouse.move(box.x + box.width * 0.42, box.y + box.height * 0.44);
    await page.mouse.down();
    await page.mouse.move(box.x + box.width * 0.65, box.y + box.height * 0.54, {
      steps: 45,
    });
    await page.mouse.up();
    await dwell(1200);
    await page
      .getByRole("button", { name: "تقريب المشهد", exact: true })
      .click();
    await dwell(1200);
    await page
      .getByRole("button", { name: "تدوير المشهد يمينًا", exact: true })
      .click();
    await dwell(1200);
    await page
      .getByRole("button", { name: "إعادة ضبط المشهد", exact: true })
      .click();
    await dwell(1800);
    const duration = (Date.now() - videoEpoch) / 1000 - from;
    const recordedFile = await recording.path();
    await context.close();
    const clip = fileURLToPath(
      new URL("oula-property-passport-interactive.mp4", output),
    );
    const encoded = join(videoDirectory, "encoded.mp4");
    const even = (value) => Math.floor(value / 2) * 2;
    execFileSync("ffmpeg", [
      "-y",
      "-loglevel",
      "error",
      "-ss",
      String(from),
      "-i",
      recordedFile,
      "-t",
      String(duration),
      "-vf",
      `crop=${even(box.width)}:${even(box.height)}:${even(box.x)}:${even(box.y)},scale=1280:-2,format=yuv420p`,
      "-c:v",
      "libx264",
      "-preset",
      "fast",
      "-crf",
      "22",
      "-an",
      "-movflags",
      "+faststart",
      encoded,
    ]);
    const probe = JSON.parse(
      execFileSync(
        "ffprobe",
        [
          "-v",
          "error",
          "-select_streams",
          "v:0",
          "-show_entries",
          "stream=width,height,nb_frames:format=duration,size",
          "-of",
          "json",
          encoded,
        ],
        { encoding: "utf8" },
      ),
    );
    if (
      !probe.streams?.[0]?.width ||
      !Number.isFinite(Number(probe.format?.duration)) ||
      Number(probe.format.duration) < 1
    )
      throw new Error("Recorded video failed verification");
    // Publish the finished, verified video atomically so readers never see an open MP4.
    await writeFile(clip + ".pending", await readFile(encoded), {
      flush: true,
    });
    await rename(clip + ".pending", clip);
    await writeFile(
      new URL("../../CYBER_VIDEO_CAPTURE_20261019.json", output),
      JSON.stringify(
        {
          source: "SYNTHETIC_CONTRACT_HARNESS_NOT_JAVA_POSTGRES",
          description:
            "Actual browser WebGL recording with pointer rotation, zoom and reset. Illustrative district geometry.",
          clip: "screenshots/cyber/oula-property-passport-interactive.mp4",
          viewport: { width: 1536, height: 1000 },
          sceneBounds: box,
          seconds: duration,
          softwareWebGL: process.env.OULA_TEST_SOFTWARE_WEBGL === "1",
          encodedVideo: probe,
          pageErrors: errors,
        },
        null,
        2,
      ) + "\n",
    );
  }
  if (!smoke) {
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
  }
  if (!smoke)
    await writeFile(
      new URL("../../CYBER_VISUAL_CHECKS_20261019.json", output),
      JSON.stringify({ checks, pageErrors: errors }, null, 2) + "\n",
    );
  console.log(JSON.stringify({ checks, pageErrors: errors }, null, 2));
  if (errors.length || checks.some((check) => check.horizontalOverflow > 1))
    process.exitCode = 1;
} finally {
  await browser?.close();
  for (const child of children) child.kill("SIGTERM");
  if (videoDirectory)
    await rm(videoDirectory, { recursive: true, force: true });
}
