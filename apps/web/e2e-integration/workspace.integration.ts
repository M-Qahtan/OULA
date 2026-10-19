import { expect, test } from "@playwright/test";

test("connected workspace saves a human-confirmed buyer journey through BFF, Java and PostgreSQL", async ({
  page,
}) => {
  await page.goto("/workspace?lang=en");
  await page
    .getByRole("button", { name: "Try as local buyer", exact: true })
    .click();
  await expect(
    page.getByText("Connected session", { exact: true }).first(),
  ).toBeVisible();
  const publicSession = await (await page.request.get("/api/session")).json();
  expect(publicSession.mode).toBe("DEV_TEST_ONLY");
  expect(publicSession).not.toHaveProperty("accessToken");
  expect(
    (await page.context().cookies()).find(
      (cookie) => cookie.name === "oula-session",
    )?.httpOnly,
  ).toBe(true);
  await page.getByRole("button", { name: "My intent", exact: true }).click();
  await expect(
    page.getByRole("button", { name: /Find my options/ }),
  ).toBeDisabled();
  await page.getByLabel(/I consent to processing my needs/).check();
  await page
    .getByRole("button", { name: "Record consent", exact: true })
    .click();
  await expect(
    page.getByRole("button", { name: "Consent recorded", exact: true }),
  ).toBeVisible();
  await page.getByRole("button", { name: /Find my options/ }).click();
  await expect(page.locator(".candidate")).toHaveCount(3);
  await page
    .getByRole("button", { name: /Open property passport/ })
    .first()
    .click();
  await expect(page.getByText("DECLARED", { exact: true })).toBeVisible();
  await page
    .getByRole("button", { name: /Continue to decision support/ })
    .click();
  // No verified evidence is fabricated to unlock the material recommendation gate.
  await page.getByRole("button", { name: /Prepare a recommendation/ }).click();
  await expect(
    page.locator(".oula-workspace .workspace-status.error"),
  ).toContainText("at least one eligible evidence");
  await page
    .getByRole("button", {
      name: "Review my choice in the deal room",
      exact: true,
    })
    .click();
  await expect(
    page.getByRole("button", { name: "Create transaction draft", exact: true }),
  ).toBeDisabled();
  const approve = () =>
    page
      .getByLabel(/I reviewed the information and personally confirm/)
      .check();
  await approve();
  await page
    .getByRole("button", { name: "Create transaction draft", exact: true })
    .click();
  await expect(page.getByTestId("workspace-deal-state")).toContainText("Draft");
  await approve();
  await page
    .getByRole("button", { name: "Confirm qualification", exact: true })
    .click();
  await expect(page.getByTestId("workspace-deal-state")).toContainText(
    "Qualified",
  );
  await approve();
  await page
    .getByRole("button", { name: "Record viewing request", exact: true })
    .click();
  await expect(page.getByTestId("workspace-deal-state")).toContainText(
    "Viewing requested",
  );
  await page.getByLabel("Offer amount — Saudi riyals").fill("1050000.50");
  await page
    .getByLabel("Offer terms — optional")
    .fill("Fictional test offer. No signing or payment.");
  await approve();
  await page.getByRole("button", { name: "Submit offer", exact: true }).click();
  await expect(page.getByTestId("workspace-deal-state")).toContainText(
    "Offer submitted",
  );
  const id = await page.getByLabel("Open a saved transaction").inputValue();
  await page.reload();
  await page.getByRole("button", { name: "Deal room", exact: true }).click();
  await page.getByLabel("Open a saved transaction").fill(id);
  await page
    .getByRole("button", { name: "Reload transaction", exact: true })
    .click();
  await expect(page.getByTestId("workspace-deal-state")).toContainText(
    "Offer submitted",
  );
  await expect(
    page.getByText("Fictional test offer.", { exact: false }),
  ).toBeVisible();
  const room = await (
    await page.request.get(`/api/oula/v1/transactions/${id}/deal-room`)
  ).json();
  expect(room.transaction.stage).toBe("OFFERING");
  expect(room.viewings).toHaveLength(1);
  expect(room.offers).toHaveLength(1);
  expect(room.offers[0].amount).toBe(1050000.5);
  const denied = await page.request.post(
    `/api/oula/v1/transactions/${id}/offers`,
    {
      headers: {
        Origin: "https://attacker.invalid",
        "Idempotency-Key": "denied-write",
      },
      data: {
        expectedVersion: 3,
        amount: 1,
        currency: "SAR",
        humanConfirmed: true,
      },
    },
  );
  expect(denied.status()).toBe(403);
  expect(await page.evaluate(() => document.cookie)).not.toContain(
    "oula-session",
  );
  expect(await page.evaluate(() => localStorage.length)).toBe(0);
});

test("HABITAT saves a declared property and private draft through the protected session", async ({
  page,
}) => {
  await page.goto("/workspace?lang=en");
  await page
    .getByRole("button", { name: "Try as property manager", exact: true })
    .click();
  await expect(page.getByRole("heading", { level: 1 })).toHaveText(
    "Add a property",
  );
  await page.getByLabel("District", { exact: true }).fill("حي تجريبي — الرياض");
  await page
    .getByLabel("Asking price — Saudi riyals", { exact: true })
    .fill("1500000.50");
  await page.getByLabel(/I confirm these are declared details/).check();
  await page
    .getByRole("button", { name: "Save property", exact: true })
    .click();
  await expect(page.getByTestId("workspace-property")).toContainText(
    "DECLARED",
  );
  await expect(page.getByTestId("workspace-property")).toContainText("DEMO");
  const id = await page.getByLabel("Reopen a saved property").inputValue();
  await page.getByLabel(/I confirm creating a private draft/).check();
  await page
    .getByRole("button", { name: "Save listing draft", exact: true })
    .click();
  await expect(page.getByTestId("workspace-listing")).toContainText("DRAFT");
  await page.reload();
  await page
    .getByRole("button", { name: "Add a property", exact: true })
    .click();
  await page.getByLabel("Reopen a saved property").fill(id);
  await page
    .getByRole("button", { name: "Read property", exact: true })
    .click();
  await expect(page.getByTestId("workspace-property")).toContainText(
    "حي تجريبي — الرياض",
  );
  const saved = await (
    await page.request.get(`/api/oula/v1/properties/${id}`)
  ).json();
  expect(saved.workspaceId).toBe("20000000-0000-4000-8000-000000000002");
  expect(saved.dataOrigin).toBe("DEMO");
  expect(saved.truthStatus).toBe("DECLARED");
});
