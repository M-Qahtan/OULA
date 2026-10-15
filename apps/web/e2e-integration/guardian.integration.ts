import { expect, test } from "@playwright/test";

test("real Java + PostgreSQL Guardian journey is workspace scoped", async ({ page }) => {
  await page.goto("/guardian-proof");
  await expect(page.getByTestId("guardian-session")).toHaveText("NOT CONNECTED");
  await page.getByTestId("guardian-connect").click();
  await expect(page.getByTestId("guardian-session")).toHaveText("CONNECTED");
  await expect(page.getByTestId("guardian-workspace")).toContainText("20000000-0000-4000-8000-000000000002");
  await page.getByTestId("guardian-enroll").click();
  await expect(page.getByTestId("guardian-enrolled")).toHaveText("ACTIVE");
  await page.getByTestId("guardian-create-obligation").click();
  await expect(page.getByTestId("guardian-obligations")).toHaveText("1");
  await page.getByTestId("guardian-assess").click();
  await expect(page.getByTestId("guardian-assessment")).toContainText("Signals 1");
  await expect(page.getByTestId("guardian-assessment")).toContainText("Actions 1");
  await expect(page.getByTestId("guardian-overview")).toContainText("Obligations 1");
  await expect(page.getByTestId("guardian-overview")).toContainText("Signals 1");
  await expect(page.getByTestId("guardian-overview")).toContainText("Actions 1");
  await expect(page.getByTestId("guardian-error")).toHaveCount(0);
  await page.reload();
  // DEV_AUTH token is memory-only, never persisted in browser storage.
  await expect(page.getByTestId("guardian-session")).toHaveText("NOT CONNECTED");
});
