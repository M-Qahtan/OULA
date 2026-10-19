import { expect, test, type Page } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";

const fixture = "http://127.0.0.1:8099";
const property = "123e4567-e89b-42d3-a456-426614174003";

async function passport(page: Page) {
  await page.goto(
    "/api/auth/login?returnTo=" +
      encodeURIComponent(
        "/workspace?world=PULSE&view=passport&lang=en&connection=server",
      ),
  );
  await page.getByLabel("Property ID", { exact: true }).fill(property);
  await page.getByRole("button", { name: "Read record", exact: true }).click();
  await expect(page.getByTestId("spatial-passport")).toBeVisible();
}
async function interactive(page: Page) {
  const scene = page.getByTestId("spatial-environment").first();
  await scene.scrollIntoViewIfNeeded();
  await expect(scene).toHaveAttribute("data-renderer", "interactive", {
    timeout: 20000,
  });
  await expect
    .poll(async () => Number(await scene.getAttribute("data-frames")))
    .toBeGreaterThanOrEqual(3);
  return scene;
}
async function stableFrames(page: Page) {
  return page
    .getByTestId("spatial-environment")
    .first()
    .evaluate(async (node) => {
      await new Promise((resolve) => setTimeout(resolve, 180));
      const before = node.getAttribute("data-frames");
      await new Promise((resolve) => setTimeout(resolve, 220));
      return before === node.getAttribute("data-frames");
    });
}
test.beforeEach(async ({ request }) => {
  await request.post(fixture + "/_test/reset");
});

test("GPU district renders with bounded draw calls and keyboard-accessible rotate, zoom, reset and pause", async ({
  page,
}) => {
  const errors: string[] = [];
  page.on("pageerror", (error) => errors.push(error.message));
  page.on("console", (message) => {
    if (message.type() === "error") errors.push(message.text());
  });
  await page.setViewportSize({ width: 1536, height: 1000 });
  await page.goto("/gateway/pulse?lang=en");
  const scene = await interactive(page);
  const calls = Number(await scene.getAttribute("data-draw-calls"));
  expect(calls).toBeGreaterThan(0);
  expect(calls).toBeLessThanOrEqual(20);
  const angle = await scene.getAttribute("data-azimuth");
  const rotate = page.getByRole("button", {
    name: "Rotate view right",
    exact: true,
  });
  await rotate.focus();
  await page.keyboard.press("Enter");
  await expect.poll(() => scene.getAttribute("data-azimuth")).not.toBe(angle);
  const distance = Number(await scene.getAttribute("data-distance"));
  await page.getByRole("button", { name: "Zoom view in", exact: true }).click();
  await expect
    .poll(async () => Number(await scene.getAttribute("data-distance")))
    .toBeLessThan(distance);
  await page
    .getByRole("button", { name: "Reset spatial view", exact: true })
    .click();
  await expect
    .poll(async () => Number(await scene.getAttribute("data-distance")))
    .toBeCloseTo(distance, 1);
  await page
    .getByRole("button", { name: "Pause ambient motion", exact: true })
    .click();
  await expect(scene).toHaveAttribute("data-motion", "paused");
  await expect.poll(() => stableFrames(page)).toBe(true);
  await page
    .getByRole("button", { name: "Resume ambient motion", exact: true })
    .click();
  await expect(scene).toHaveAttribute("data-motion", "running");
  expect(errors).toEqual([]);
});

test("offscreen GPU ambience stops while the document remains scrollable", async ({
  page,
}) => {
  await page.setViewportSize({ width: 1536, height: 500 });
  await passport(page);
  const scene = await interactive(page);
  await expect(scene).toHaveAttribute("data-motion", "running");
  await page.evaluate(() =>
    scrollTo({
      top: document.documentElement.scrollHeight,
      behavior: "instant",
    }),
  );
  await expect(scene).toHaveAttribute("data-motion", "paused");
  await expect
    .poll(async () => Number(await scene.getAttribute("data-frames")))
    .toBeGreaterThanOrEqual(3);
  expect(await stableFrames(page)).toBe(true);
  await scene.scrollIntoViewIfNeeded();
  await expect(scene).toHaveAttribute("data-motion", "running");
});

test("reduced motion keeps a still GPU scene with explicit controls and no ambient frame loop", async ({
  page,
}) => {
  await page.emulateMedia({ reducedMotion: "reduce" });
  await page.goto("/gateway/forge?lang=en");
  const scene = await interactive(page);
  await expect(scene).toHaveAttribute("data-motion", "paused");
  expect(await stableFrames(page)).toBe(true);
  const angle = await scene.getAttribute("data-azimuth");
  await page
    .getByRole("button", { name: "Rotate view left", exact: true })
    .click();
  await expect.poll(() => scene.getAttribute("data-azimuth")).not.toBe(angle);
  expect(await stableFrames(page)).toBe(true);
  await expect(page.locator(".spatial-ambience")).toHaveAttribute(
    "data-animated",
    "false",
  );
});

test("quiet mode and GPU context loss preserve passport records and allow scene retry without another API read", async ({
  page,
  request,
}) => {
  await passport(page);
  const scene = await interactive(page);
  await page
    .getByLabel("What would you like to accomplish?")
    .fill("an unsaved local draft");
  await page.getByRole("button", { name: "Quiet view", exact: true }).click();
  await expect(scene).toHaveAttribute("data-renderer", "simple");
  await expect(scene.locator("canvas")).toHaveCount(0);
  await expect(page.getByTestId("spatial-passport")).toContainText(
    "CONTRACT_TEST_PROPERTY",
  );
  await page.getByRole("button", { name: "3D view", exact: true }).click();
  await expect(scene).toHaveAttribute("data-renderer", "interactive", {
    timeout: 20000,
  });
  const lost = await scene.evaluate((node) => {
    const gl = node.querySelector("canvas")?.getContext("webgl2");
    const extension = gl?.getExtension("WEBGL_lose_context");
    extension?.loseContext();
    return !!extension;
  });
  expect(lost).toBe(true);
  await expect(scene).toHaveAttribute("data-renderer", "unavailable");
  await expect(page.getByTestId("spatial-passport")).toContainText(
    "Currency not specified in this record",
  );
  await expect(
    page.getByLabel("What would you like to accomplish?"),
  ).toHaveValue("an unsaved local draft");
  await page.getByRole("button", { name: "Retry scene", exact: true }).click();
  await expect(scene).toHaveAttribute("data-renderer", "interactive", {
    timeout: 20000,
  });
  const calls = (await (await request.get(fixture + "/_test/requests")).json())
    .requests as { path: string }[];
  expect(
    calls.filter((r) => r.path === `/v1/properties/${property}/passport`),
  ).toHaveLength(1);
});

test("WebGL unavailability leaves the actual passport facts readable and primary actions usable", async ({
  page,
}) => {
  await page.addInitScript(() => {
    const getContext = HTMLCanvasElement.prototype.getContext;
    HTMLCanvasElement.prototype.getContext = function (
      this: HTMLCanvasElement,
      type: string,
      ...args: unknown[]
    ) {
      if (
        type === "webgl2" ||
        type === "webgl" ||
        type === "experimental-webgl"
      )
        return null;
      return getContext.call(this, type, ...args);
    } as typeof getContext;
  });
  await passport(page);
  const scene = page.getByTestId("spatial-environment").first();
  await scene.scrollIntoViewIfNeeded();
  await expect(scene).toHaveAttribute("data-renderer", "unavailable", {
    timeout: 20000,
  });
  await expect(page.getByTestId("spatial-passport")).toContainText(
    "CONTRACT_TEST_PROPERTY",
  );
  await page
    .getByRole("button", { name: "Continue to decision support", exact: true })
    .click();
  await expect(page).toHaveURL(/view=decision/);
});

test("a fresh server price is exact for assistive technology while its visual value interpolates", async ({
  page,
}) => {
  await passport(page);
  await interactive(page);
  await page.getByRole("button", { name: "Quiet view", exact: true }).click();
  await page.evaluate(() => {
    const node = document.querySelector(
      '[data-value="1090000"] [aria-hidden="true"]',
    );
    const samples: string[] = [];
    (window as Window & { numberSamples?: string[] }).numberSamples = samples;
    if (node)
      new MutationObserver(() => samples.push(node.textContent ?? "")).observe(
        node,
        { childList: true, characterData: true, subtree: true },
      );
  });
  await page.route("**/api/oula/v1/properties/*/passport", async (route) => {
    const response = await route.fetch();
    const record = await response.json();
    await route.fulfill({
      response,
      json: { ...record, askingPrice: 1885000 },
    });
  });
  await page.getByRole("button", { name: "Read record", exact: true }).click();
  const amount = page.locator('.spatial-passport [data-value="1885000"]');
  await expect(amount.locator(".spatial-sr-only")).toHaveText("1,885,000");
  await expect(amount.locator('[aria-hidden="true"]')).toHaveText("1,885,000");
  const samples = await page.evaluate(
    () => (window as Window & { numberSamples?: string[] }).numberSamples ?? [],
  );
  expect(
    samples.some((s) => {
      const value = Number(s.replaceAll(",", ""));
      return value > 1090000 && value < 1885000;
    }),
  ).toBe(true);
});

for (const width of [320, 390])
  test(`mobile passport metrics support swipe alternatives and maintain accessible glass navigation at ${width}px`, async ({
    page,
  }) => {
    await page.emulateMedia({ reducedMotion: "reduce" });
    await page.setViewportSize({ width, height: 844 });
    await passport(page);
    await interactive(page);
    const rail = page.locator(".spatial-metric-rail");
    expect(
      await rail.evaluate((node) => node.scrollWidth > node.clientWidth),
    ).toBe(true);
    const before = await rail.evaluate((node) => node.scrollLeft);
    const next = page.getByRole("button", { name: "Next metric", exact: true });
    await next.focus();
    await page.keyboard.press("Enter");
    await expect
      .poll(() => rail.evaluate((node) => node.scrollLeft))
      .not.toBe(before);
    await page
      .getByRole("button", { name: "Previous metric", exact: true })
      .click();
    await expect(
      page.getByRole("button", { name: "Previous metric", exact: true }),
    ).toBeDisabled();
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth - innerWidth,
      ),
    ).toBeLessThanOrEqual(1);
    await expect(page.locator(".spatial-navigation")).toHaveCSS(
      "position",
      "fixed",
    );
    const audit = await new AxeBuilder({ page }).analyze();
    expect(audit.violations).toEqual([]);
  });
