import { expect, test } from "@playwright/test";

test("signed browser session completes the canonical real-DB golden path", async ({ page }) => {
  await page.goto("/journey");

  await expect(page.getByTestId("backend-proof")).toBeVisible();
  await expect(page.getByTestId("backend-session")).toHaveText("NOT CONNECTED");

  await page.getByTestId("backend-connect").click();
  await expect(page.getByTestId("backend-session")).toHaveText("CONNECTED");
  await expect(page.getByTestId("backend-workspace")).toContainText(
    "20000000-0000-4000-8000-000000000001",
  );

  await page.getByTestId("backend-record-consent").click();
  await expect(page.getByTestId("backend-consent")).toHaveText("GRANTED");

  await page.getByTestId("backend-create-match").click();
  await expect(page.getByTestId("backend-match-count")).toHaveText("3");

  await page.getByTestId("backend-open-passport").click();
  await expect(page.getByTestId("backend-passport")).toBeVisible();
  await expect(page.getByTestId("backend-passport")).toContainText("DECLARED/DEV_FIXTURE");
  await expect(page.getByTestId("backend-passport")).toContainText("truth coverage 0%");

  await page.getByTestId("backend-create-transaction").click();
  await expect(page.getByTestId("backend-transaction-stage")).toHaveText("DRAFT");
  await expect(page.getByTestId("backend-deal-room")).toContainText("DRAFT");

  await page.getByTestId("backend-qualify").click();
  await expect(page.getByTestId("backend-transaction-stage")).toHaveText("QUALIFIED");

  await page.getByTestId("backend-request-viewing").click();
  await expect(page.getByTestId("backend-transaction-stage")).toHaveText("VIEWING");
  await expect(page.getByTestId("backend-deal-room")).toContainText("VIEWINGS 1");

  await page.getByTestId("backend-submit-offer").click();
  await expect(page.getByTestId("backend-transaction-stage")).toHaveText("OFFERING");
  await expect(page.getByTestId("backend-deal-room")).toContainText("OFFERS 1");
  await expect(page.getByTestId("backend-deal-room")).toContainText(
    "HUMAN_CONFIRMATION_REQUIRED true",
  );

  await expect(page.getByTestId("backend-error")).toHaveCount(0);
});
