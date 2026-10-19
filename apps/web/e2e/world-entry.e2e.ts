import { expect, test } from "@playwright/test";
const fixture = "http://127.0.0.1:8099";
test.beforeEach(async ({ request }) => {
  await request.post(fixture + "/_test/reset");
});
test("world task survives OIDC, reads a scoped record and returns with role and language", async ({
  page,
}) => {
  await page.goto(
    "/gateway/pulse?section=passport&branch=evidence&role=owner&lang=en",
  );
  await page.getByRole("link", { name: /Open path in workspace/ }).click();
  await expect(page.locator(".workspace-origin")).toContainText(
    "Sources and trust",
  );
  const links = page.getByRole("link", { name: "Sign in", exact: true });
  await expect(links).toHaveCount(2);
  expect(await links.nth(0).getAttribute("href")).toBe(
    await links.nth(1).getAttribute("href"),
  );
  await page
    .getByRole("region", { name: "OULA journey" })
    .getByRole("link", { name: "Sign in", exact: true })
    .click();
  await expect(page.getByText("Connected session").first()).toBeVisible();
  expect(Object.fromEntries(new URL(page.url()).searchParams)).toMatchObject({
    world: "PULSE",
    view: "passport",
    section: "passport",
    branch: "evidence",
    role: "owner",
    lang: "en",
  });
  await expect(page.getByLabel("Property ID", { exact: true })).toBeFocused();
  await page
    .getByLabel("Property ID", { exact: true })
    .fill("123e4567-e89b-42d3-a456-426614174003");
  await page.getByRole("button", { name: "Read record", exact: true }).click();
  await expect(
    page.getByText("CONTRACT_TEST_PROPERTY", { exact: true }),
  ).toBeVisible();
  await page.getByRole("link", { name: /Return to branch/ }).click();
  await expect(page).toHaveURL(/\/gateway\/pulse\?/);
  await expect(page.locator(".ow-task-detail h3")).toHaveText(
    "Sources and trust",
  );
});
test("cancelled sign-in retains an Arabic care task", async ({
  page,
  request,
}) => {
  await request.post(fixture + "/_test/scenario", {
    data: { scenario: "auth-denied" },
  });
  await page.goto(
    "/gateway/habitat?section=guardian&branch=priorities&role=owner&lang=ar",
  );
  await page.getByRole("link", { name: /افتح المسار في مساحة العمل/ }).click();
  await page
    .getByRole("complementary", { name: "مساعد عُلا" })
    .getByRole("link", { name: "تسجيل الدخول", exact: true })
    .click();
  await expect(
    page.getByText("لم يكتمل تسجيل الدخول. يمكنك إعادة المحاولة."),
  ).toBeVisible();
  expect(Object.fromEntries(new URL(page.url()).searchParams)).toMatchObject({
    world: "HABITAT",
    section: "guardian",
    branch: "priorities",
    role: "owner",
    lang: "ar",
    authError: "1",
  });
});
test("login rejects external return destinations", async ({ page }) => {
  await page.goto(
    "/api/auth/login?returnTo=" +
      encodeURIComponent("https://evil.example/workspace"),
  );
  await expect(page.getByText("جلسة متصلة").first()).toBeVisible();
  expect(new URL(page.url()).origin).toBe("http://127.0.0.1:3000");
  expect(new URL(page.url()).pathname).toBe("/workspace");
});
test("a public bank preference cannot grant access without a session", async ({
  page,
}) => {
  await page.goto(
    "/gateway/pulse?section=passport&branch=identity&role=bank&lang=en",
  );
  await expect(page.getByLabel("I am exploring as")).toHaveValue("bank");
  await page.getByRole("link", { name: /Open path in workspace/ }).click();
  await expect(
    page.getByRole("button", { name: "Read record", exact: true }),
  ).toBeDisabled();
  expect(
    (
      await page.request.get(
        "/api/oula/v1/properties/123e4567-e89b-42d3-a456-426614174003/passport",
      )
    ).status(),
  ).toBe(401);
});
test("another tab's sign-out clears gateway request text and search", async ({
  page,
  context,
}) => {
  await page.goto("/api/auth/login");
  await expect(page.getByText("جلسة متصلة").first()).toBeVisible();
  await page.goto("/gateway?lang=en");
  await expect(page.locator(".og-root")).toHaveAttribute(
    "data-session-status",
    "authenticated",
  );
  await page
    .getByLabel("Tell me what you need inside OULA")
    .fill("my private maintenance request");
  await page
    .getByRole("button", { name: "Find my world", exact: true })
    .click();
  await expect(page.locator(".og-search-dialog")).toBeVisible();
  const other = await context.newPage();
  await other.goto("/workspace?connection=server&lang=en");
  await expect(other.getByText("Connected session").first()).toBeVisible();
  await other.getByRole("button", { name: "Sign out", exact: true }).click();
  await expect(page.locator(".og-root")).toHaveAttribute(
    "data-session-status",
    "signed-out",
  );
  await expect(page.locator(".og-search-dialog")).not.toBeVisible();
  await expect(
    page.getByLabel("Tell me what you need inside OULA"),
  ).toHaveValue("");
});
