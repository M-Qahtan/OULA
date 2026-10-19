import { expect, test } from "@playwright/test";

test("HABITAT records, assesses and resolves a declared obligation through BFF, Java and PostgreSQL", async ({
  page,
}) => {
  await page.goto("/workspace?lang=en");
  await page
    .getByRole("button", { name: "Try as property manager", exact: true })
    .click();
  await page
    .getByLabel("District", { exact: true })
    .fill("اختبار متابعة أصل — الرياض");
  await page.getByLabel(/I confirm these are declared details/).check();
  await page
    .getByRole("button", { name: "Save property", exact: true })
    .click();
  await expect(page.getByTestId("workspace-property")).toContainText(
    "DECLARED",
  );
  const id = await page.getByLabel("Reopen a saved property").inputValue();
  await page
    .getByRole("button", { name: "Property care", exact: true })
    .click();
  await page.getByLabel("Managed property ID", { exact: true }).fill(id);
  const care = page.getByTestId("workspace-care");
  const approval = care.getByRole("checkbox");
  await expect(
    care.getByRole("button", { name: "Enroll property care", exact: true }),
  ).toBeDisabled();
  await approval.check();
  await care
    .getByRole("button", { name: "Enroll property care", exact: true })
    .click();
  await expect(care.locator(".care-summary")).toBeVisible();
  await care
    .getByLabel("Obligation title", { exact: true })
    .fill("Review maintenance documents — fictional test");
  await care
    .getByLabel("Due date — local time", { exact: true })
    .fill("2020-01-01T10:00");
  await care.getByLabel("Priority", { exact: true }).selectOption("HIGH");
  await approval.check();
  const pending = page.waitForRequest(
    (request) =>
      request.method() === "POST" &&
      request.url().endsWith(`/properties/${id}/obligations`),
  );
  await care
    .getByRole("button", { name: "Save obligation", exact: true })
    .click();
  const command = await pending;
  await expect(care.locator(".care-records")).toContainText(
    "Review maintenance documents",
  );
  const replay = await page.request.post(
    `/api/oula/v1/properties/${id}/obligations`,
    {
      headers: {
        Origin: "http://127.0.0.1:3000",
        "Idempotency-Key": command.headers()["idempotency-key"]!,
      },
      data: command.postDataJSON(),
    },
  );
  expect(replay.status()).toBe(201);
  expect(replay.headers()["idempotency-replayed"]).toBe("true");
  await approval.check();
  await care
    .getByRole("button", { name: "Assess deadlines", exact: true })
    .click();
  await expect(
    care.getByRole("button", { name: "Record resolution", exact: true }),
  ).toBeVisible();
  await care
    .getByLabel("Resolution note", { exact: true })
    .fill(
      "I reviewed the fictional documents; this is not a certified inspection.",
    );
  await approval.check();
  await care
    .getByRole("button", { name: "Record resolution", exact: true })
    .click();
  await expect(care.locator(".care-records")).toContainText("COMPLETED");
  await page.reload();
  await page.getByLabel("Managed property ID", { exact: true }).fill(id);
  await care
    .getByRole("button", { name: "Read care record", exact: true })
    .click();
  await expect(care).toContainText("I reviewed the fictional documents");
  const saved = await (
    await page.request.get(`/api/oula/v1/properties/${id}/management`)
  ).json();
  expect(saved.obligations).toHaveLength(1);
  expect(saved.obligations[0].sourceType).toBe("USER_DECLARED");
  expect(saved.obligations[0].status).toBe("SATISFIED");
  expect(saved.actions[0].status).toBe("COMPLETED");
  expect(saved.guardianSignals[0].status).toBe("RESOLVED");
});
