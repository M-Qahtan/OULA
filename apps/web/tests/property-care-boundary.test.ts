import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { bridgeRequest } from "../lib/server/bridge";
import { resolveApiContract } from "../lib/api-contracts";
import { obligationInputSchema } from "../lib/property-care-contracts";
import type { ServerSession } from "../lib/server/session";

const workspace = "123e4567-e89b-42d3-a456-426614174001";
const other = "123e4567-e89b-42d3-a456-426614174002";
const property = "123e4567-e89b-42d3-a456-426614174003";
const session: ServerSession = {
  mode: "OIDC",
  accessToken: "synthetic-care-token",
  subject: "care-test",
  actorId: other,
  workspaceId: workspace,
  workspaceIds: [workspace],
  purposes: ["PROPERTY_MANAGEMENT"],
  scopes: [
    "oula.property.management.read",
    "oula.property.management.write",
    "oula.property.guardian.run",
    "oula.property.action.complete",
  ],
  exp: Math.floor(Date.now() / 1000) + 900,
};
const enrollment = {
  id: other,
  workspaceId: workspace,
  propertyId: property,
  managerActorId: other,
  status: "ACTIVE",
  activatedAt: "2026-10-19T00:00:00Z",
  closedAt: null,
  version: 0,
};
const overview = {
  propertyId: property,
  enrollment,
  obligations: [],
  guardianSignals: [],
  actions: [],
};
const input = {
  obligationType: "MAINTENANCE",
  title: "Review fire safety documents",
  dueAt: "2026-10-20T12:00:00Z",
  priority: "HIGH",
  sourceType: "USER_DECLARED",
};
const base = `/v1/properties/${property}`;
function request(
  path: string,
  method = "GET",
  body?: unknown,
  origin = "http://127.0.0.1:3000",
) {
  return new Request("http://127.0.0.1:3000/api/oula" + path, {
    method,
    headers: {
      Origin: origin,
      "Content-Type": "application/json",
      "Idempotency-Key": "same-care-command",
    },
    ...(body ? { body: JSON.stringify(body) } : {}),
  });
}
beforeEach(() => {
  vi.stubEnv("OULA_BACKEND_URL", "http://127.0.0.1:8080");
  vi.stubEnv("OULA_APP_ORIGIN", "http://127.0.0.1:3000");
  vi.stubEnv("OULA_OIDC_ISSUER", "http://127.0.0.1:8099");
  vi.stubEnv("OULA_OIDC_CLIENT_ID", "oula-web");
  vi.stubEnv(
    "OULA_SESSION_SECRET",
    "test-only-care-secret-at-least-32-characters",
  );
  vi.stubEnv("OULA_ALLOW_HTTP_LOCALHOST", "true");
});
afterEach(() => {
  vi.unstubAllEnvs();
  vi.unstubAllGlobals();
});
describe("property care BFF boundary", () => {
  it("chooses the contracted purpose and forwards the same command key", async () => {
    const fetch = vi
      .fn()
      .mockResolvedValue(
        Response.json(enrollment, {
          status: 201,
          headers: { "Idempotency-Replayed": "true" },
        }),
      );
    vi.stubGlobal("fetch", fetch);
    const response = await bridgeRequest(
      request(base + "/management/enroll", "POST"),
      base + "/management/enroll",
      session,
    );
    expect(response.status).toBe(201);
    expect(response.headers.get("Idempotency-Replayed")).toBe("true");
    const headers = fetch.mock.calls[0]![1].headers as Headers;
    expect(headers.get("X-OULA-Purpose")).toBe("PROPERTY_MANAGEMENT");
    expect(headers.get("X-OULA-Workspace-ID")).toBe(workspace);
    expect(headers.get("Idempotency-Key")).toBe("same-care-command");
  });
  it("denies missing session, purpose and scopes before calling Java", async () => {
    const fetch = vi.fn();
    vi.stubGlobal("fetch", fetch);
    const path = base + "/management";
    expect((await bridgeRequest(request(path), path, null)).status).toBe(401);
    expect(
      (
        await bridgeRequest(request(path), path, {
          ...session,
          purposes: ["PROPERTY_DECISION_SUPPORT"],
        })
      ).status,
    ).toBe(403);
    expect(
      (await bridgeRequest(request(path), path, { ...session, scopes: [] }))
        .status,
    ).toBe(403);
    expect(fetch).not.toHaveBeenCalled();
  });
  it.each(["enrollment", "obligations", "guardianSignals", "actions"])(
    "rejects mixed workspace or property data in %s",
    async (field) => {
      const corrupted = structuredClone(overview) as Record<string, unknown>;
      const item = { ...enrollment, workspaceId: other, propertyId: property };
      if (field === "enrollment") corrupted.enrollment = item;
      else if (field === "obligations")
        corrupted.obligations = [
          {
            ...item,
            obligationType: "MAINTENANCE",
            title: "Private foreign item",
            dueAt: "2026-10-20T12:00:00Z",
            priority: "HIGH",
            status: "OPEN",
            sourceType: "USER_DECLARED",
            sourceReference: null,
            createdBy: other,
            createdAt: "2026-10-19T12:00:00Z",
            completedAt: null,
          },
        ];
      else if (field === "guardianSignals")
        corrupted.guardianSignals = [
          {
            ...item,
            obligationId: other,
            signalType: "DUE",
            severity: "HIGH",
            status: "OPEN",
            message: "Private foreign signal",
            recommendedAction: "REVIEW",
            detectedAt: "2026-10-19T12:00:00Z",
            resolvedAt: null,
          },
        ];
      else
        corrupted.actions = [
          {
            ...item,
            obligationId: other,
            guardianSignalId: other,
            actionType: "REVIEW",
            title: "Private foreign action",
            status: "OPEN",
            dueAt: "2026-10-20T12:00:00Z",
            assignedActorId: other,
            resolutionNote: null,
            createdAt: "2026-10-19T12:00:00Z",
            completedAt: null,
          },
        ];
      vi.stubGlobal(
        "fetch",
        vi.fn().mockResolvedValue(Response.json(corrupted)),
      );
      const path = base + "/management";
      const response = await bridgeRequest(request(path), path, session);
      expect(response.status).toBe(502);
      expect(await response.json()).toEqual({ code: "CONTRACT_MISMATCH" });
    },
  );
  it("requires origin, valid declared input, and a finite existing endpoint", async () => {
    vi.stubGlobal("fetch", vi.fn());
    const path = base + "/obligations";
    expect(
      (
        await bridgeRequest(
          request(path, "POST", input, "https://attacker.invalid"),
          path,
          session,
        )
      ).status,
    ).toBe(403);
    expect(
      (
        await bridgeRequest(
          request(path, "POST", {
            ...input,
            sourceType: "VERIFIED_GOVERNMENT",
          }),
          path,
          session,
        )
      ).status,
    ).toBe(400);
    expect(
      obligationInputSchema.safeParse({ ...input, priority: "invented" })
        .success,
    ).toBe(false);
    expect(resolveApiContract("DELETE", base + "/management")).toBeNull();
    expect(resolveApiContract("POST", base + "/guardian/run")).toBeNull();
    expect(
      resolveApiContract("POST", "/v1/property-actions/not-a-uuid/complete"),
    ).toBeNull();
  });
  it("accepts an authorized complete overview and rejects a wrong root property", async () => {
    const fetch = vi
      .fn()
      .mockResolvedValueOnce(Response.json(overview))
      .mockResolvedValueOnce(Response.json({ ...overview, propertyId: other }));
    vi.stubGlobal("fetch", fetch);
    const path = base + "/management";
    expect((await bridgeRequest(request(path), path, session)).status).toBe(
      200,
    );
    expect((await bridgeRequest(request(path), path, session)).status).toBe(
      502,
    );
  });
});
