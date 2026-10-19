import {
  expect,
  test,
  type APIRequestContext,
  type Page,
} from "@playwright/test";

const fixture = "http://127.0.0.1:8099";
const privateRequest = "Private request: find a family home near my office";

async function login(page: Page) {
  await page.goto("/workspace?lang=en");
  await page.getByRole("link", { name: "Sign in", exact: true }).click();
  await expect(page.getByText("Connected session").first()).toBeVisible();
  await expect(page.locator("html")).toHaveAttribute("lang", "en");
}

async function replaceSession(
  page: Page,
  request: APIRequestContext,
  profile: string,
) {
  expect(
    (
      await request.post(fixture + "/_test/identity", { data: { profile } })
    ).ok(),
  ).toBe(true);
  await page.goto("/api/auth/login");
  await expect(page.getByText("جلسة متصلة").first()).toBeVisible();
}

async function recheck(page: Page) {
  const response = page.waitForResponse(
    (value) =>
      new URL(value.url()).pathname === "/api/session" &&
      value.request().method() === "GET",
  );
  await page.evaluate(() => window.dispatchEvent(new Event("focus")));
  await response;
}

async function privateDraft(page: Page) {
  await page.getByRole("button", { name: "My intent", exact: true }).click();
  await page.getByLabel("Maximum budget — workspace currency").fill("1987654");
  await page
    .getByLabel("What would you like to accomplish?")
    .fill(privateRequest);
  await page.getByRole("button", { name: "Understand my request" }).click();
}

test.beforeEach(async ({ request }) => {
  await request.post(fixture + "/_test/reset");
});

test("sign-out clears private assistant text and its proposed plan", async ({
  page,
}) => {
  await login(page);
  await page
    .getByLabel("What would you like to accomplish?")
    .fill(privateRequest);
  await page.getByRole("button", { name: "Understand my request" }).click();
  await expect(page.locator(".assistant-plan")).toBeVisible();
  await page.getByRole("button", { name: "Sign out", exact: true }).click();
  await expect(
    page.getByRole("link", { name: "Sign in", exact: true }),
  ).toBeVisible();
  await expect(
    page.getByLabel("What would you like to accomplish?"),
  ).toHaveValue("");
  await expect(page.locator(".assistant-plan")).toHaveCount(0);
  await expect(page).toHaveURL(/lang=en/);
});

test("an existing tab notices sign-out from another tab and clears private input", async ({
  page,
  context,
}) => {
  await login(page);
  await page
    .getByLabel("What would you like to accomplish?")
    .fill(privateRequest);
  const otherTab = await context.newPage();
  await otherTab.goto("/workspace?lang=en&connection=server");
  await expect(otherTab.getByText("Connected session").first()).toBeVisible();
  await otherTab.getByRole("button", { name: "Sign out", exact: true }).click();
  await page.bringToFront();
  await expect(
    page.getByRole("button", { name: "Sign out", exact: true }),
  ).toHaveCount(0);
  await expect(
    page.getByLabel("What would you like to accomplish?"),
  ).toHaveValue("");
});

test("revalidating the same session keeps its draft, language and navigation", async ({
  page,
}) => {
  await login(page);
  await privateDraft(page);
  const url = page.url();
  await recheck(page);
  await expect(
    page.getByLabel("Maximum budget — workspace currency"),
  ).toHaveValue("1987654");
  await expect(
    page.getByLabel("What would you like to accomplish?"),
  ).toHaveValue(privateRequest);
  await expect(page.locator(".assistant-plan")).toBeVisible();
  expect(page.url()).toBe(url);
});

for (const profile of ["other", "reader"]) {
  test(`a ${profile} session clears the previous account or authority context in an existing tab`, async ({
    page,
    context,
    request,
  }) => {
    await login(page);
    await privateDraft(page);
    const url = page.url();
    const otherTab = await context.newPage();
    await replaceSession(otherTab, request, profile);
    await page.bringToFront();
    await recheck(page);
    await expect(
      page.getByLabel("What would you like to accomplish?"),
    ).toHaveValue("");
    await expect(
      page.getByLabel("Maximum budget — workspace currency"),
    ).toHaveValue("2000000");
    await expect(page.locator(".assistant-plan")).toHaveCount(0);
    expect(page.url()).toBe(url);
    const current = await (await page.request.get("/api/session")).json();
    if (profile === "other")
      expect(current.subject).toBe("contract-test-other-person");
    else {
      expect(current.subject).toBe("contract-test-user");
      expect(current.scopes).not.toContain("oula.intent.write");
      await expect(
        page.getByRole("button", { name: /Find my options/ }),
      ).toBeDisabled();
      const denied = await page.request.post("/api/oula/v1/intents", {
        headers: {
          Origin: "http://127.0.0.1:3000",
          "Idempotency-Key": "reader-denial",
        },
        data: {
          intentType: "BUY",
          budgetMax: 1000000,
          minimumBedrooms: 2,
          preferredDistricts: [],
        },
      });
      expect(denied.status()).toBe(403);
    }
  });
}

test("changing an authorized workspace clears private drafts without changing the selected route", async ({
  page,
  request,
}) => {
  await replaceSession(page, request, "multi-workspace");
  await page.getByRole("button", { name: "Switch to English" }).click();
  await privateDraft(page);
  const url = page.url();
  await page
    .getByLabel("Authorized workspace")
    .selectOption("123e4567-e89b-42d3-a456-426614174002");
  await expect(page.getByLabel("Authorized workspace")).toHaveValue(
    "123e4567-e89b-42d3-a456-426614174002",
  );
  await expect(
    page.getByLabel("What would you like to accomplish?"),
  ).toHaveValue("");
  await expect(
    page.getByLabel("Maximum budget — workspace currency"),
  ).toHaveValue("2000000");
  expect(page.url()).toBe(url);
});

test("a property response delayed until after sign-out cannot restore a private record", async ({
  page,
}) => {
  await login(page);
  await page
    .getByRole("button", { name: "Property passport", exact: true })
    .click();
  await page
    .getByLabel("Property ID", { exact: true })
    .fill("123e4567-e89b-42d3-a456-426614174003");
  let release!: () => void;
  let started!: () => void;
  let completed!: () => void;
  const pending = new Promise<void>((done) => {
    release = done;
  });
  const intercepted = new Promise<void>((done) => {
    started = done;
  });
  const delivered = new Promise<void>((done) => {
    completed = done;
  });
  await page.route("**/api/oula/v1/properties/*/passport", async (route) => {
    const response = await route.fetch();
    started();
    await pending;
    try {
      await route.fulfill({ response });
    } catch {
      // An obsolete request is allowed to have been aborted on unmount.
    } finally {
      completed();
    }
  });
  await page.getByRole("button", { name: "Read record", exact: true }).click();
  await intercepted;
  await page.getByRole("button", { name: "Sign out", exact: true }).click();
  await expect(
    page.getByRole("link", { name: "Sign in", exact: true }),
  ).toBeVisible();
  release();
  await delivered;
  await expect(page.getByLabel("Property ID", { exact: true })).toHaveValue("");
  await expect(
    page.getByText("CONTRACT_TEST_PROPERTY", { exact: true }),
  ).toHaveCount(0);
});

test("cross-tab sign-out works through a storage signal when BroadcastChannel is unavailable", async ({
  page,
  context,
}) => {
  await context.addInitScript(() => {
    Object.defineProperty(window, "BroadcastChannel", { value: undefined });
  });
  await login(page);
  await page
    .getByLabel("What would you like to accomplish?")
    .fill(privateRequest);
  const otherTab = await context.newPage();
  await otherTab.goto("/workspace?lang=en&connection=server");
  await expect(otherTab.getByText("Connected session").first()).toBeVisible();
  await otherTab.getByRole("button", { name: "Sign out", exact: true }).click();
  // No focus event is needed for a storage event from another tab.
  await expect(
    page.getByRole("button", { name: "Sign out", exact: true }),
  ).toHaveCount(0);
  await expect(
    page.getByLabel("What would you like to accomplish?"),
  ).toHaveValue("");
  expect(await otherTab.evaluate(() => localStorage.length)).toBe(0);
});

test("visible-tab polling notices a cookie cleared outside the UI", async ({
  page,
}) => {
  await page.clock.install();
  await login(page);
  await page
    .getByLabel("What would you like to accomplish?")
    .fill(privateRequest);
  const cleared = await page.request.delete("/api/session", {
    headers: { Origin: "http://127.0.0.1:3000" },
  });
  expect(cleared.ok()).toBe(true);
  await page.clock.fastForward(30_001);
  await expect(
    page.getByRole("button", { name: "Sign out", exact: true }),
  ).toHaveCount(0);
  await expect(
    page.getByLabel("What would you like to accomplish?"),
  ).toHaveValue("");
});
