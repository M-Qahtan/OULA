import { expect, test, type Page } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";

const fixture = "http://127.0.0.1:8099";
const propertyId = "123e4567-e89b-42d3-a456-426614174003";
const transactionId = "123e4567-e89b-42d3-a456-426614174008";
async function login(page: Page, view = "passport") {
  await page.goto(
    "/api/auth/login?returnTo=" +
      encodeURIComponent(
        `/workspace?world=PULSE&view=${view}&lang=en&connection=server`,
      ),
  );
  await expect(page.getByText("Connected session").first()).toBeVisible();
}
test.beforeEach(async ({ request }) => {
  await request.post(fixture + "/_test/reset");
});

for (const width of [390, 768, 1024])
  test(`assistant drawer keeps focus and private draft across responsive modes at ${width}px`, async ({
    page,
  }) => {
    await page.setViewportSize({ width, height: 900 });
    await page.goto("/workspace?lang=en");
    const trigger = page.getByRole("button", {
      name: "Open OULA assistant",
      exact: true,
    });
    const dock = page.locator("#oula-assistant-dock");
    await expect(trigger).toBeVisible();
    await expect(dock).not.toBeVisible();
    await trigger.click();
    await expect(dock).toBeVisible();
    expect(await dock.evaluate((e) => e.matches(":modal"))).toBe(true);
    const input = page.getByLabel("What would you like to accomplish?");
    await input.fill("A private draft for this session");
    for (let i = 0; i < 14; i++) {
      await page.keyboard.press("Tab");
      expect(
        await page.evaluate(
          () => !!document.activeElement?.closest("#oula-assistant-dock"),
        ),
      ).toBe(true);
    }
    await page.keyboard.press("Escape");
    await expect(dock).not.toBeVisible();
    await expect(trigger).toBeFocused();
    await trigger.click();
    await expect(input).toHaveValue("A private draft for this session");
    await page.setViewportSize({ width: 1536, height: 900 });
    await expect(dock).toBeVisible();
    await expect
      .poll(() => dock.evaluate((e) => e.matches(":modal")))
      .toBe(false);
    await expect(input).toHaveValue("A private draft for this session");
    await page.setViewportSize({ width, height: 900 });
    await expect(dock).not.toBeVisible();
    expect(await page.evaluate(() => document.body.style.overflow)).not.toBe(
      "hidden",
    );
  });

test("all worlds share physical navigation/canvas/assistant columns and contextual glow", async ({
  page,
}) => {
  await page.setViewportSize({ width: 1536, height: 1000 });
  for (const world of ["pulse", "habitat", "nexus", "forge", "civic"]) {
    await page.goto(`/gateway/${world}?lang=ar`);
    await expect(page.locator(".spatial-app")).toHaveAttribute(
      "data-tone",
      world.toUpperCase(),
    );
    const nav = await page.locator(".spatial-navigation").boundingBox();
    const main = await page.locator(".spatial-main").boundingBox();
    const assistant = await page
      .locator(".spatial-assistant-dock")
      .boundingBox();
    expect(nav!.x + nav!.width).toBeLessThanOrEqual(main!.x + 1);
    expect(main!.x + main!.width).toBeLessThanOrEqual(assistant!.x + 1);
    await expect(page.locator(".spatial-scene-label")).toHaveText("مشهد تصوري");
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth - innerWidth,
      ),
    ).toBeLessThanOrEqual(1);
  }
});

test("passport cache is reused for navigation, explicit refresh reaches the service, and sign-out removes private facts", async ({
  page,
  request,
}) => {
  await login(page, "intent");
  await page.getByLabel(/I consent to processing my needs/).check();
  await page
    .getByRole("button", { name: "Record consent", exact: true })
    .click();
  await page.getByRole("button", { name: /Find my options/ }).click();
  await page.getByRole("button", { name: /Open property passport/ }).click();
  await expect(page.getByTestId("spatial-passport")).toContainText(
    "CONTRACT_TEST_PROPERTY",
  );
  await page.getByRole("button", { name: "Explore", exact: true }).click();
  await page.getByRole("button", { name: /Open property passport/ }).click();
  const count = async () =>
    (
      (await (await request.get(fixture + "/_test/requests")).json())
        .requests as { path: string }[]
    ).filter((c) => c.path === `/v1/properties/${propertyId}/passport`).length;
  expect(await count()).toBe(1);
  await page.getByRole("button", { name: "Read record", exact: true }).click();
  await expect.poll(count).toBe(2);
  await expect(page.getByTestId("spatial-passport")).toContainText(
    "Currency not specified in this record",
  );
  await page.getByRole("button", { name: "Sign out", exact: true }).click();
  await expect(page.getByTestId("spatial-passport")).toHaveCount(0);
  expect(await page.evaluate(() => localStorage.length)).toBe(0);
});

test("pending and failed offer never advance the recorded stage; identical retry uses the original command key", async ({
  page,
}) => {
  await login(page, "deal");
  await page.getByLabel("Open a saved transaction").fill(transactionId);
  await page
    .getByRole("button", { name: "Reload transaction", exact: true })
    .click();
  await expect(page.getByTestId("workspace-deal-state")).toContainText(
    "Viewing requested",
  );
  const keys: string[] = [];
  let release!: () => void;
  const gate = new Promise<void>((resolve) => {
    release = resolve;
  });
  const url = "**/api/oula/v1/transactions/*/offers";
  await page.route(url, async (route) => {
    keys.push(route.request().headers()["idempotency-key"]!);
    await gate;
    await route.fulfill({ status: 503, json: { code: "BACKEND_UNAVAILABLE" } });
  });
  await page
    .getByLabel(/I reviewed the information and personally confirm/)
    .check();
  await page.getByRole("button", { name: "Submit offer", exact: true }).click();
  await expect(page.locator(".spatial-loading")).toBeVisible();
  await expect(page.getByTestId("workspace-deal-state")).not.toContainText(
    "Offer submitted",
  );
  release();
  await expect(page.locator("#deal-error")).toBeVisible();
  await expect(page.getByTestId("workspace-deal-state")).toContainText(
    "Viewing requested",
  );
  await page.unroute(url);
  page.on("request", (r) => {
    if (r.method() === "POST" && r.url().endsWith("/offers"))
      keys.push(r.headers()["idempotency-key"]!);
  });
  await page.getByRole("button", { name: "Submit offer", exact: true }).click();
  await expect(page.getByTestId("workspace-deal-state")).toContainText(
    "Offer submitted",
  );
  expect(keys).toHaveLength(2);
  expect(keys[0]).toBe(keys[1]);
});

test("Arabic passport and an open mobile assistant remain accessible with reduced motion", async ({
  page,
}) => {
  await login(page);
  await page
    .getByRole("button", { name: "التبديل إلى العربية", exact: true })
    .click();
  await page.getByLabel("معرّف العقار", { exact: true }).fill(propertyId);
  await page.getByRole("button", { name: "قراءة السجل", exact: true }).click();
  await expect(page.getByTestId("spatial-passport")).toBeVisible();
  await page.emulateMedia({ reducedMotion: "reduce" });
  await page.setViewportSize({ width: 390, height: 844 });
  await page.getByRole("button", { name: "قراءة السجل", exact: true }).hover();
  expect(
    (
      await new AxeBuilder({ page })
        .include(".spatial-app")
        .withTags(["wcag2a", "wcag2aa", "wcag21aa", "wcag22aa"])
        .analyze()
    ).violations,
  ).toEqual([]);
  await page
    .getByRole("button", { name: "افتح مساعد عُلا", exact: true })
    .click();
  expect(
    (
      await new AxeBuilder({ page })
        .include("#oula-assistant-dock")
        .withTags(["wcag2a", "wcag2aa", "wcag21aa", "wcag22aa"])
        .analyze()
    ).violations,
  ).toEqual([]);
});
