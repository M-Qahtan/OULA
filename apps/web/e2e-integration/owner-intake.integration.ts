import { expect, test } from "@playwright/test";

test("manager creates DECLARED property and private DRAFT listing in canonical Postgres", async ({ page }) => {
  await page.goto("/owner-intake-proof");
  await expect(page.getByTestId("intake-create")).toBeDisabled();
  await page.getByTestId("intake-connect").click();
  await expect(page.getByTestId("intake-workspace")).toContainText("20000000-0000-4000-8000-000000000002");
  await page.getByTestId("intake-create").click();
  await expect(page.getByTestId("intake-property")).toContainText("DECLARED");
  await expect(page.getByTestId("intake-property")).toContainText("DEMO_FIXTURE");
  await page.getByTestId("intake-listing").click();
  await expect(page.getByTestId("intake-draft")).toContainText("DRAFT");
  await page.getByTestId("intake-verify").click();
  await expect(page.getByTestId("intake-database-verified")).toBeVisible();
  await expect(page.getByTestId("intake-error")).toHaveCount(0);
  await page.reload();
  await expect(page.getByTestId("intake-create")).toBeDisabled(); // in-memory development token only
});
