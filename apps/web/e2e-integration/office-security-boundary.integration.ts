import { expect, test, type APIRequestContext } from "@playwright/test";

/**
 * Independent, read-only authorization regression against the real Java API,
 * signed dev-auth fixtures, and PostgreSQL/PostGIS.
 *
 * These checks never enroll, revoke, or reset a member. They are safe to run
 * after the one-shot office invitation demo on the same ephemeral CI database.
 */
const BASE = "http://127.0.0.1:8080";
const PURPOSE = "TRANSACTION_EXECUTION";

type DevSession = {
  accessToken: string;
  workspaceId: string;
  actorId: string;
  scopes: string[];
};

async function session(request: APIRequestContext, fixture: string): Promise<DevSession> {
  const response = await request.post(BASE + "/dev-auth/token", { data: { fixture } });
  expect(response.status()).toBe(200);
  return await response.json() as DevSession;
}

function signedHeaders(s: DevSession) {
  return {
    Authorization: `Bearer ${s.accessToken}`,
    "X-OULA-Workspace-ID": s.workspaceId,
    "X-OULA-Purpose": PURPOSE,
  };
}

test("office roster: reject unauthenticated, unscoped, cross-purpose and cross-workspace access", async ({ request }) => {
  const [admin, invitee, foreignBroker] = await Promise.all([
    session(request, "office-admin"),
    session(request, "office-invitee"),
    session(request, "broker-readonly"),
  ]);
  expect(admin.workspaceId).toBe(invitee.workspaceId);
  expect(admin.workspaceId).not.toBe(foreignBroker.workspaceId);

  const roster = BASE + "/v1/offices/members";
  const noToken = await request.get(roster, {
    headers: {
      "X-OULA-Workspace-ID": admin.workspaceId,
      "X-OULA-Purpose": PURPOSE,
    },
  });
  expect(noToken.status()).toBe(401);

  const allowed = await request.get(roster, { headers: signedHeaders(admin) });
  expect(allowed.status()).toBe(200);
  expect(allowed.headers()["cache-control"]).toContain("no-store");
  const members = await allowed.json() as { workspaceId: string; actorId: string; role: string }[];
  expect(members.length).toBeGreaterThanOrEqual(1);
  expect(members.every(member => member.workspaceId === admin.workspaceId)).toBe(true);

  const unscoped = await request.get(roster, { headers: signedHeaders(invitee) });
  expect(unscoped.status()).toBe(403);

  const wrongPurpose = await request.get(roster, {
    headers: { ...signedHeaders(admin), "X-OULA-Purpose": "PROPERTY_DECISION_SUPPORT" },
  });
  expect(wrongPurpose.status()).toBe(403);

  const spoofedWorkspace = await request.get(roster, {
    headers: { ...signedHeaders(admin), "X-OULA-Workspace-ID": foreignBroker.workspaceId },
  });
  expect(spoofedWorkspace.status()).toBe(403);

  const foreignActor = await request.get(roster, {
    headers: { ...signedHeaders(foreignBroker), "X-OULA-Workspace-ID": admin.workspaceId },
  });
  expect(foreignActor.status()).toBe(403);
});

test("invitation endpoints: inbox is recipient-bound and unauthorized writes never bypass idempotency", async ({ request }) => {
  const [admin, invitee] = await Promise.all([
    session(request, "office-admin"),
    session(request, "office-invitee"),
  ]);
  const base = BASE + "/v1/offices/members/invitations";

  const adminInbox = await request.get(base, { headers: signedHeaders(admin) });
  expect(adminInbox.status()).toBe(200);
  expect(adminInbox.headers()["cache-control"]).toContain("no-store");

  const officeRosterFromInvitee = await request.get(base, { headers: signedHeaders(invitee) });
  expect(officeRosterFromInvitee.status()).toBe(403);

  const recipientInbox = await request.get(base + "/mine", { headers: signedHeaders(invitee) });
  expect(recipientInbox.status()).toBe(200);
  expect(recipientInbox.headers()["cache-control"]).toContain("no-store");
  const pending = await recipientInbox.json() as { workspaceId: string; actorId: string }[];
  expect(Array.isArray(pending)).toBe(true);
  expect(pending.every(entry =>
    entry.workspaceId === invitee.workspaceId && entry.actorId === invitee.actorId
  )).toBe(true);

  const adminAsRecipient = await request.get(base + "/mine", { headers: signedHeaders(admin) });
  expect(adminAsRecipient.status()).toBe(403);

  const payload = {
    actorId: invitee.actorId,
    issuer: "http://localhost:8080/dev-auth",
    subject: "dev-fixture:office-invitee",
    role: "BROKER",
    expiresAt: new Date(Date.now() + 60 * 60 * 1000).toISOString(),
  };

  // HTTP requests missing the mandatory replay key must fail before mutation.
  const missingKey = await request.post(base, {
    headers: signedHeaders(admin),
    data: payload,
  });
  expect(missingKey.status()).toBe(400);

  // Possessing a valid, signed JWT does not grant office manager authority.
  const forgedManager = await request.post(base, {
    headers: { ...signedHeaders(invitee), "Idempotency-Key": crypto.randomUUID() },
    data: payload,
  });
  expect(forgedManager.status()).toBe(403);
});
