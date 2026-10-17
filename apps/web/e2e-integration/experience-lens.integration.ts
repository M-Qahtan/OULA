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

  await page.getByTestId("lens-broker").click();
  await expect(page.getByTestId("lens-identity")).toContainText("NEXUS");
  await expect(page.getByTestId("lens-capability-DEAL_READ")).toBeVisible();
  await expect(page.getByTestId("lens-capability-DEAL_ROOM_READ")).toBeVisible();
  await expect(page.getByTestId("lens-capability-DEAL_ADVANCE")).toHaveCount(0);
  await expect(page.getByTestId("lens-capability-PRIVATE_LISTING_DRAFT")).toHaveCount(0);
  await expect(page.getByTestId("lens-capability-INTENT_CREATE")).toHaveCount(0);
  await expect(page.getByTestId("lens-open-owner-intake")).toHaveCount(0);
  await expect(page.getByTestId("lens-open-workspace")).toHaveAttribute("href", "/broker-proof");
  await expect(page.getByTestId("broker-preview-boundary")).toBeVisible();
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

test("signed broker workspace cannot create transactions or inspect another workspace", async ({ request }) => {
  const base = "http://127.0.0.1:8080";
  const login = await request.post(base + "/dev-auth/token", {data:{fixture:"broker-readonly"}});
  expect(login.status()).toBe(200);
  const broker = await login.json() as {accessToken:string; workspaceId:string; scopes:string[]};
  expect(broker.workspaceId).toBe("20000000-0000-4000-8000-000000000004");
  expect(broker.scopes).not.toContain("oula.transaction.create");
  expect(broker.scopes).not.toContain("oula.transaction.advance");
  const headers = {
    Authorization: "Bearer " + broker.accessToken,
    "X-OULA-Workspace-ID": broker.workspaceId,
    "X-OULA-Purpose": "TRANSACTION_EXECUTION",
  };
  const manifest = await request.get(base + "/v1/experience/manifest", {headers});
  expect(manifest.status()).toBe(200);
  const demoRoom = await request.get(base + "/v1/transactions/50000000-0000-4000-8000-000000000004/deal-room", {headers});
  expect(demoRoom.status()).toBe(200);
  const snapshot = await demoRoom.json();
  expect(snapshot.transaction.workspaceId).toBe(broker.workspaceId);
  expect(snapshot.transaction.stage).toBe("DRAFT");
  expect(snapshot.availableCommands).toEqual([]);
  expect((await manifest.json()).capabilities.map((c:{code:string})=>c.code))
    .toEqual(["DEAL_READ","DEAL_ROOM_READ"]);
  const denied = await request.post(base + "/v1/transactions", {
    headers:{...headers,"Idempotency-Key":"broker-negative-"+crypto.randomUUID()},
    data:{intentId:"30000000-0000-4000-8000-000000000001",
      propertyId:"30000000-0000-4000-8000-000000000002"},
  });
  expect(denied.status()).toBe(403);
  const crossWorkspace = await request.get(base+"/v1/experience/manifest",{
    headers:{...headers,"X-OULA-Workspace-ID":"20000000-0000-4000-8000-000000000001"}
  });
  expect(crossWorkspace.status()).toBe(403);
});

test("broker signed role reads an isolated PostgreSQL deal room in browser without write controls", async ({ page }) => {
  await page.goto("/broker-proof");
  await expect(page.getByTestId("broker-session")).toHaveText("NOT CONNECTED");
  await page.getByTestId("broker-connect").click();
  await expect(page.getByTestId("broker-session")).toHaveText("READ ONLY");
  await expect(page.getByTestId("broker-deal-stage")).toHaveText("DRAFT");
  await expect(page.getByTestId("broker-workspace")).toContainText("20000000-0000-4000-8000-000000000004");
  await expect(page.getByTestId("broker-transaction-id")).toContainText("50000000-0000-4000-8000-000000000004");
  await expect(page.getByTestId("broker-available-commands")).toHaveText("0");
  await expect(page.getByTestId("broker-error")).toHaveCount(0);
  await page.reload();
  await expect(page.getByTestId("broker-session")).toHaveText("NOT CONNECTED");
});
