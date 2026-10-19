import { expect, test, type Page } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";

const fixture = "http://127.0.0.1:8099";
const propertyId = "123e4567-e89b-42d3-a456-426614174003";
const transactionId = "123e4567-e89b-42d3-a456-426614174008";
async function english(page: Page) {
  const toggle = page.getByRole("button", { name: "Switch to English" });
  if (await toggle.count()) await toggle.click();
  await expect(page.locator("html")).toHaveAttribute("lang", "en");
}
async function consent(page: Page) {
  const record = page.getByRole("button", {
    name: "Record consent",
    exact: true,
  });
  if (await record.count()) {
    await page.getByLabel(/I consent to processing my needs/).check();
    await record.click();
    await expect(
      page.getByRole("button", { name: "Consent recorded", exact: true }),
    ).toBeVisible();
  }
}
async function login(page: Page) {
  await page.goto("/workspace");
  await page.getByRole("link", { name: "تسجيل الدخول" }).click();
  await expect(page).toHaveURL(/connection=server/);
  await expect(page.getByText("جلسة متصلة").first()).toBeVisible();
  await english(page);
}
test.beforeEach(async ({ request }) => {
  await request.post(fixture + "/_test/reset");
});

test("main workspaces respond to role, keyboard, language and local assistant context", async ({
  page,
}) => {
  await page.goto("/workspace");
  await page.keyboard.press("Tab");
  await expect(
    page.getByRole("link", { name: "تجاوز إلى المحتوى" }),
  ).toBeFocused();
  await page.keyboard.press("Enter");
  await expect(page).toHaveURL(/#workspace-main$/);
  await english(page);
  await page.getByLabel("Your role's workspace").selectOption("manager");
  await expect(page.locator(".spatial-brand")).toContainText("HABITAT");
  await expect(page.getByRole("heading", { level: 1 })).toContainText(
    "Care for assets",
  );
  await page.getByLabel("Your role's workspace").selectOption("engineer");
  await page.getByRole("button", { name: /Open studio/ }).click();
  await expect(
    page.getByText(
      "Interactive concept · no scientific simulation results or live government data",
    ),
  ).toBeVisible();
  await page.getByLabel("Environment and spaces").check();
  await expect(page.locator(".studio-canvas")).toHaveAttribute(
    "data-environment",
    "true",
  );
  await page
    .getByLabel("What would you like to accomplish?")
    .fill("Find a home for my family");
  await page.getByRole("button", { name: "Understand my request" }).click();
  await page.getByRole("button", { name: /Open the right workspace/ }).click();
  await expect(page.locator(".spatial-brand")).toContainText("PULSE");
  await expect(page.getByRole("heading", { level: 1 })).toHaveText("My intent");
  await page
    .getByLabel("What would you like to accomplish?")
    .fill("pay now and sign the contract");
  await page.getByRole("button", { name: "Understand my request" }).click();
  await expect(page.locator(".assistant-plan")).toContainText(
    "cannot sign, pay or submit offers",
  );
  await expect(page.locator(".assistant-plan button")).toHaveCount(0);
});

test("a real OIDC code/PKCE flow protects the BFF journey with encrypted cookies", async ({
  page,
  request,
}) => {
  await login(page);
  const session = await (await page.request.get("/api/session")).json();
  expect(session.status).toBe("authenticated");
  expect(session).not.toHaveProperty("accessToken");
  const cookies = await page.context().cookies();
  expect(
    cookies.find((cookie) => cookie.name === "oula-session")?.httpOnly,
  ).toBe(true);
  expect(await page.evaluate(() => document.cookie)).not.toContain(
    "oula-session",
  );
  await page.getByRole("button", { name: "My intent", exact: true }).click();
  await consent(page);
  await page.getByRole("button", { name: /Find my options/ }).click();
  await expect(page.getByText("Workspace property · 1")).toBeVisible();
  await page.getByRole("button", { name: /Open property passport/ }).click();
  await expect(
    page.getByText("CONTRACT_TEST_PROPERTY", { exact: true }),
  ).toBeVisible();
  await expect(
    page.getByText("CONTRACT_HARNESS", { exact: false }).first(),
  ).toBeVisible();
  await expect(
    page.getByText("Currency not specified in this record", { exact: true }),
  ).toBeVisible();
  await page
    .getByRole("button", { name: /Continue to decision support/ })
    .click();
  await page
    .getByLabel("Eligible, source-authorized evidence IDs — comma-separated")
    .fill(propertyId);
  await page.getByRole("button", { name: /Prepare a recommendation/ }).click();
  await expect(
    page.getByRole("heading", {
      name: "Synthetic recommendation for protocol testing only.",
    }),
  ).toBeVisible();
  await expect(
    page.getByRole("button", { name: "Record my decision" }),
  ).toBeDisabled();
  await page.getByLabel(/I reviewed the recommendation/).check();
  await page.getByRole("button", { name: "Record my decision" }).click();
  await expect(
    page.getByText(/Your decision was recorded by the service/),
  ).toBeVisible();
  const calls = (await (await request.get(fixture + "/_test/requests")).json())
    .requests;
  expect(
    calls.filter((call: { path: string }) => call.path === "/v1/intents"),
  ).toHaveLength(1);
  expect(calls.every((call: { hasBearer: boolean }) => call.hasBearer)).toBe(
    true,
  );
  expect(
    calls.find((call: { path: string }) => call.path.endsWith("/decisions")),
  ).toMatchObject({ purpose: "PROPERTY_DECISION_SUPPORT" });
  expect(await page.evaluate(() => localStorage.length)).toBe(0);
});

test("workspace denial and conflicting writes stay errors, with the same idempotency key on retry", async ({
  page,
  request,
}) => {
  await login(page);
  await page.getByRole("button", { name: "My intent", exact: true }).click();
  await consent(page);
  await request.post(fixture + "/_test/scenario", {
    data: { scenario: "409" },
  });
  await consent(page);
  await page.getByRole("button", { name: /Find my options/ }).click();
  await expect(
    page.locator(".oula-workspace .workspace-status.error"),
  ).toContainText("Conflict with the server version");
  await consent(page);
  const retryResponse = page.waitForResponse(
    (response) =>
      response.url().endsWith("/api/oula/v1/intents") &&
      response.request().method() === "POST",
  );
  await page.getByRole("button", { name: /Find my options/ }).click();
  expect((await retryResponse).status()).toBe(409);
  const calls = (await (await request.get(fixture + "/_test/requests")).json())
    .requests;
  const attempts = calls.filter(
    (call: { path: string }) => call.path === "/v1/intents",
  );
  expect(attempts[0].key).toBe(attempts[1].key);
  await request.post(fixture + "/_test/scenario", {
    data: { scenario: "normal" },
  });
  await consent(page);
  await page.getByRole("button", { name: /Find my options/ }).click();
  await expect(page.getByText("Workspace property · 1")).toBeVisible();
  const denied = await page.request.post("/api/session", {
    headers: { Origin: "http://127.0.0.1:3000" },
    data: { workspaceId: "123e4567-e89b-42d3-a456-426614174099" },
  });
  expect(denied.status()).toBe(403);
  expect(
    (await (await page.request.get("/api/session")).json()).workspaceId,
  ).toBe("123e4567-e89b-42d3-a456-426614174001");
});

test("empty, denied, malformed and unavailable responses never show demo property data", async ({
  page,
  request,
}) => {
  await login(page);
  for (const scenario of ["403", "malformed", "503"]) {
    await page.goto("/workspace?connection=server&view=intent&lang=en");
    await request.post(fixture + "/_test/scenario", {
      data: { scenario: "normal" },
    });
    await consent(page);
    await request.post(fixture + "/_test/scenario", { data: { scenario } });
    await consent(page);
    await page.getByRole("button", { name: /Find my options/ }).click();
    await expect(
      page.locator(".oula-workspace .workspace-status.error"),
    ).toBeVisible();
    await expect(page.locator(".candidate")).toHaveCount(0);
    await expect(page.locator(".connected-panel")).not.toContainText(
      "Sample home",
    );
  }
  await request.post(fixture + "/_test/scenario", {
    data: { scenario: "normal" },
  });
  await page.goto("/workspace?connection=server&view=intent&lang=en");
  await consent(page);
  await request.post(fixture + "/_test/scenario", {
    data: { scenario: "empty" },
  });
  await consent(page);
  await page.getByRole("button", { name: /Find my options/ }).click();
  await expect(
    page.getByText("No options match your current constraints."),
  ).toBeVisible();
});

test("existing transactions and property care read the scoped service, without fabricating missing observations", async ({
  page,
  request,
}) => {
  await login(page);
  await page.getByRole("button", { name: "Deal room", exact: true }).click();
  await page.getByLabel("Open a saved transaction").fill(transactionId);
  await page
    .getByRole("button", { name: "Reload transaction", exact: true })
    .click();
  await expect(
    page
      .getByTestId("workspace-deal-state")
      .getByText("Viewing requested", { exact: true }),
  ).toBeVisible();
  await page.getByLabel("Your role's workspace").selectOption("manager");
  await page
    .getByRole("button", { name: "Property care", exact: true })
    .click();
  await page.getByLabel("Managed property ID").fill(propertyId);
  await page.getByRole("button", { name: "Read record", exact: true }).click();
  await expect(page.getByText("UNKNOWN", { exact: true })).toBeVisible();
  await expect(
    page.getByText("Synthetic contract harness; no operational evidence."),
  ).toBeVisible();
  const calls = (await (await request.get(fixture + "/_test/requests")).json())
    .requests;
  expect(
    calls
      .filter(
        (call: { path: string }) =>
          call.path.includes("/vitals/") ||
          call.path.endsWith("operational-advice"),
      )
      .every(
        (call: { purpose: string }) => call.purpose === "PROPERTY_MANAGEMENT",
      ),
  ).toBe(true);
  await page.getByRole("button", { name: "Sign out", exact: true }).click();
  await expect(
    page.getByRole("button", { name: "Sign out", exact: true }),
  ).toHaveCount(0);
  expect((await (await page.request.get("/api/session")).json()).status).toBe(
    "signed-out",
  );
  expect(
    (
      await page.request.get(`/api/oula/v1/properties/${propertyId}/passport`)
    ).status(),
  ).toBe(401);
});

for (const viewport of [
  { width: 1920, height: 1080 },
  { width: 1024, height: 768 },
  { width: 390, height: 844 },
]) {
  test(`responsive and accessible main workspaces at ${viewport.width}px`, async ({
    page,
  }) => {
    await page.setViewportSize(viewport);
    await page.emulateMedia({ reducedMotion: "reduce" });
    await page.goto("/workspace");
    await page.evaluate(() => document.fonts.ready);
    await page.screenshot({
      path: `test-results/oula-workspace-ar-${viewport.width}.png`,
      fullPage: true,
    });
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= window.innerWidth,
      ),
    ).toBe(true);
    await expect(
      page.getByRole("button", { name: /ابدأ من هنا/ }),
    ).toBeVisible();
    const arabic = await new AxeBuilder({ page })
      .include(".oula-workspace")
      .withTags(["wcag2a", "wcag2aa", "wcag21aa", "wcag22aa"])
      .analyze();
    expect(arabic.violations).toEqual([]);
    await english(page);
    const englishResult = await new AxeBuilder({ page })
      .include(".oula-workspace")
      .withTags(["wcag2a", "wcag2aa", "wcag21aa", "wcag22aa"])
      .analyze();
    expect(englishResult.violations).toEqual([]);
    await page.getByRole("button", { name: /Start here/ }).click();
    await page.getByLabel("Maximum budget — workspace currency").fill("0");
    await consent(page);
    await page.getByRole("button", { name: /Find my options/ }).click();
    await expect(
      page.locator(".oula-workspace .workspace-status.error"),
    ).toContainText("Enter a positive budget");
    await expect(
      page.getByLabel("Maximum budget — workspace currency"),
    ).toBeFocused();
  });
}
