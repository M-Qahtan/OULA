import { expect, test } from "@playwright/test";

test("signed developer roles get distinct Java-authorized OULA world capabilities", async ({ page }) => {
  await page.goto("/gateway");
  const proof = page.getByTestId("experience-lens-proof");
  await expect(proof).toBeVisible();
  await page.getByTestId("lens-buyer").click();
  await expect(page.getByTestId("lens-identity")).toContainText("PULSE");
  await expect(page.getByTestId("lens-capability-INTENT_CREATE")).toBeVisible();
  await expect(page.getByTestId("lens-capability-MATCH_RUN")).toBeVisible();
  await expect(page.getByTestId("lens-capability-MANAGEMENT_OVERVIEW")).toHaveCount(0);
  await expect(page.getByTestId("lens-capability-PROPERTY_INTAKE")).toHaveCount(0);
  await expect(page.getByTestId("lens-open-owner-intake")).toHaveCount(0);
  await expect(page.getByTestId("lens-open-workspace")).toHaveAttribute("href", "/");

  await page.getByTestId("lens-manager").click();
  await expect(page.getByTestId("lens-identity")).toContainText("HABITAT");
  await expect(page.getByTestId("lens-capability-MANAGEMENT_OVERVIEW")).toBeVisible();
  await expect(page.getByTestId("lens-capability-PROPERTY_INTAKE")).toBeVisible();
  await expect(page.getByTestId("lens-capability-PRIVATE_LISTING_DRAFT")).toBeVisible();
  await expect(page.getByTestId("lens-capability-PRIVATE_LISTING_READ")).toBeVisible();
  await expect(page.getByTestId("lens-open-owner-intake")).toHaveAttribute("href", "/owner-intake-proof");
  await expect(page.getByTestId("lens-capability-INTENT_CREATE")).toHaveCount(0);
  await expect(page.getByTestId("lens-open-workspace")).toHaveAttribute("href", "/guardian-proof");
  await expect(page.getByTestId("lens-error")).toHaveCount(0);
});

test("manifest endpoint fails closed for missing scope and wrong workspace", async ({ request }) => {
  const base = "http://127.0.0.1:8080";
  const noToken = await request.get(base + "/v1/experience/manifest", {
    headers: {
      "X-OULA-Workspace-ID": "20000000-0000-4000-8000-000000000001",
      "X-OULA-Purpose": "PROPERTY_DECISION_SUPPORT",
    },
  });
  expect(noToken.status()).toBe(401);
  const tokenResponse = await request.post(base + "/dev-auth/token", { data: { fixture: "buyer-readonly" } });
  expect(tokenResponse.status()).toBe(200);
  const session = await tokenResponse.json() as { accessToken: string };
  const headers = {
    Authorization: "Bearer " + session.accessToken,
    "X-OULA-Workspace-ID": "20000000-0000-4000-8000-000000000001",
    "X-OULA-Purpose": "PROPERTY_DECISION_SUPPORT",
  };
  const noScope = await request.get(base + "/v1/experience/manifest", { headers });
  expect(noScope.status()).toBe(403);

  const buyerResponse = await request.post(base + "/dev-auth/token", { data: { fixture: "buyer" } });
  const buyer = await buyerResponse.json() as { accessToken: string };
  const crossWorkspace = await request.get(base + "/v1/experience/manifest", {
    headers: { ...headers, Authorization: "Bearer " + buyer.accessToken,
      "X-OULA-Workspace-ID": "20000000-0000-4000-8000-000000000002" },
  });
  expect(crossWorkspace.status()).toBe(403);
});
