import { expect, test } from "@playwright/test";

test("signed office roster preserves RTL without horizontal overflow at release viewports", async ({page})=>{
  await page.goto("/office-proof");
  await page.getByTestId("office-connect").click();
  await expect(page.getByTestId("office-session")).toHaveText("SIGNED DEV SESSION");
  await expect(page.getByTestId("office-proof")).toHaveAttribute("dir","rtl");
  for (const [width,height] of [[390,844],[1024,768],[1920,1080]]) {
    await page.setViewportSize({width,height});
    await expect.poll(()=>page.evaluate(()=>
      document.documentElement.scrollWidth<=document.documentElement.clientWidth
    )).toBe(true);
    await expect(page.getByTestId("office-refresh")).toBeVisible();
  }
});

test("signed fictional office manager invites recipient who explicitly accepts then loses access", async ({page})=>{
  await page.goto("/office-proof");
  await expect(page.getByTestId("office-session")).toHaveText("NOT CONNECTED");
  await page.getByTestId("office-connect").click();
  await expect(page.getByTestId("office-session")).toHaveText("SIGNED DEV SESSION");
  await expect(page.getByTestId("office-active-count")).toHaveText("1");
  await expect(page.getByTestId("office-pending-count")).toHaveText("0");
  await expect(page.getByTestId("office-mine-count")).toHaveText("0");

  await page.getByTestId("office-invite").click();
  await expect(page.getByTestId("office-pending-count")).toHaveText("1");
  await expect(page.getByTestId("office-mine-count")).toHaveText("1");
  await expect(page.getByTestId("office-active-count")).toHaveText("1");

  await page.getByTestId("office-accept").click();
  await expect(page.getByTestId("office-active-count")).toHaveText("2");
  await expect(page.getByTestId("office-mine-count")).toHaveText("0");
  await expect(page.getByTestId("office-pending-count")).toHaveText("0");

  await page.getByTestId("office-revoke").click();
  await expect(page.getByTestId("office-active-count")).toHaveText("1");
  await expect(page.getByTestId("office-error")).toHaveCount(0);
  await page.reload();
  await expect(page.getByTestId("office-session")).toHaveText("NOT CONNECTED");
});

test("invitee cannot read office roster and signed admin cannot access foreign workspace",async({request})=>{
  const base="http://127.0.0.1:8080";
  const issue=async(fixture:string)=>{
    const result=await request.post(base+"/dev-auth/token",{data:{fixture}});
    expect(result.status()).toBe(200);
    return result.json() as Promise<{accessToken:string;workspaceId:string;actorId:string;scopes:string[]}>;
  };
  const admin=await issue("office-admin");
  const recipient=await issue("office-invitee");
  const broker=await issue("broker-readonly");
  expect(admin.scopes).toContain("oula.office.members.manage");
  expect(recipient.scopes).not.toContain("oula.office.members.manage");
  const headers=(s:{accessToken:string;workspaceId:string})=>({
    Authorization:"Bearer "+s.accessToken,
    "X-OULA-Workspace-ID":s.workspaceId,
    "X-OULA-Purpose":"TRANSACTION_EXECUTION",
  });
  const ok=await request.get(base+"/v1/offices/members",{headers:headers(admin)});
  expect(ok.status()).toBe(200);
  expect(ok.headers()["cache-control"]).toBe("no-store");
  expect(await ok.json()).toEqual(expect.arrayContaining([
    expect.objectContaining({actorId:admin.actorId,role:"ADMIN",status:"ACTIVE"}),
  ]));
  const denied=await request.get(base+"/v1/offices/members",{headers:headers(recipient)});
  expect(denied.status()).toBe(403);
  const cross=await request.get(base+"/v1/offices/members",{
    headers:{...headers(broker),"X-OULA-Workspace-ID":admin.workspaceId},
  });
  expect(cross.status()).toBe(403);
});
