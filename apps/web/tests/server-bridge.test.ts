import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { bridgeRequest } from "../lib/server/bridge";
import { getServerConfig, trustedUrl } from "../lib/server/config";
import {
  openCookie,
  publicSession,
  readSession,
  sealCookie,
  sessionFromClaims,
  type ServerSession,
} from "../lib/server/session";
import { resolveApiContract } from "../lib/api-contracts";
import { createBrowserOulaApi } from "../lib/browser-api";

const workspace = "123e4567-e89b-42d3-a456-426614174001";
const otherWorkspace = "123e4567-e89b-42d3-a456-426614174002";
const id = "123e4567-e89b-42d3-a456-426614174003";
const baseSession: ServerSession = {
  mode: "OIDC",
  accessToken: "synthetic-test-bearer",
  subject: "test-subject",
  actorId: id,
  workspaceIds: [workspace],
  workspaceId: workspace,
  purposes: [
    "PROPERTY_DECISION_SUPPORT",
    "PROPERTY_MANAGEMENT",
    "TRANSACTION_EXECUTION",
  ],
  scopes: [
    "oula.intent.write",
    "oula.intent.read",
    "oula.matching.run",
    "oula.property.passport.read",
    "oula.property.vitals.read",
    "oula.property.advisory.read",
    "oula.transaction.read",
    "oula.transaction.advance",
    "oula.intelligence.recommend",
    "oula.intelligence.read",
    "oula.intelligence.decide",
  ],
  exp: Math.floor(Date.now() / 1000) + 900,
};
const intentBody = {
  intentType: "BUY",
  budgetMax: 1500000,
  minimumBedrooms: 3,
  preferredDistricts: ["الملقا"],
};
const intentResult = {
  ...intentBody,
  id,
  workspaceId: workspace,
  status: "ACTIVE",
};
const url = "http://127.0.0.1:3000/api/oula";
function request(
  path = "/v1/intents",
  method = "POST",
  data: unknown = intentBody,
  headers: Record<string, string> = {},
) {
  return new Request(url + path, {
    method,
    headers: {
      Origin: "http://127.0.0.1:3000",
      "Content-Type": "application/json",
      "Idempotency-Key": "same-command-key",
      ...headers,
    },
    ...(method === "POST"
      ? { body: data == null ? undefined : JSON.stringify(data) }
      : {}),
  });
}
beforeEach(() => {
  vi.stubEnv("OULA_BACKEND_URL", "http://127.0.0.1:8080");
  vi.stubEnv("OULA_APP_ORIGIN", "http://127.0.0.1:3000");
  vi.stubEnv("OULA_OIDC_ISSUER", "http://127.0.0.1:8099");
  vi.stubEnv("OULA_OIDC_CLIENT_ID", "oula-web");
  vi.stubEnv(
    "OULA_SESSION_SECRET",
    "test-only-secret-with-at-least-32-characters",
  );
  vi.stubEnv("OULA_ALLOW_HTTP_LOCALHOST", "true");
});
afterEach(() => {
  vi.unstubAllEnvs();
  vi.unstubAllGlobals();
});

describe("server session and URL boundaries", () => {
  it("encrypts a bounded session, rejects tampering and keeps bearer data out of the public session", async () => {
    const encrypted = await sealCookie(baseSession, baseSession.exp);
    expect(encrypted).not.toContain(baseSession.accessToken);
    expect(await readSession(encrypted)).toEqual(baseSession);
    expect(await readSession(encrypted.slice(0, -8) + "tampered")).toBeNull();
    expect(await readSession(undefined)).toBeNull();
    expect(publicSession(baseSession)).not.toHaveProperty("accessToken");
    expect(JSON.stringify(publicSession(baseSession))).not.toContain(
      baseSession.accessToken,
    );
  });
  it("rejects expired and malformed encrypted sessions", async () => {
    const expired = await sealCookie(
      baseSession,
      Math.floor(Date.now() / 1000) - 5,
    );
    expect(await openCookie(expired)).toBeNull();
    const wrongWorkspace = await sealCookie(
      { ...baseSession, workspaceId: otherWorkspace },
      baseSession.exp,
    );
    expect(await readSession(wrongWorkspace)).toBeNull();
  });
  it("accepts only backend-compatible identity claims and bounds the session lifetime", () => {
    const now = Math.floor(Date.now() / 1000);
    const claims = {
      sub: "test-subject",
      actor_id: id,
      oula_workspace_ids: [workspace],
      oula_purposes: ["PROPERTY_MANAGEMENT"],
      scope: "oula.property.vitals.read",
      exp: now + 7200,
    };
    expect(sessionFromClaims("test-token", claims).exp).toBeLessThanOrEqual(
      now + 3600,
    );
    expect(() =>
      sessionFromClaims("test-token", { ...claims, oula_workspace_ids: [] }),
    ).toThrow();
    expect(() =>
      sessionFromClaims("test-token", {
        ...claims,
        actor_id: "untrusted-role",
      }),
    ).toThrow();
    expect(() =>
      sessionFromClaims("test-token", { ...claims, exp: now - 1 }),
    ).toThrow();
  });
  it("rejects unsafe destinations and incomplete configuration", () => {
    for (const value of [
      "javascript:alert(1)",
      "http://example.com",
      "https://user:secret@example.com",
      "https://example.com?redirect=1",
    ])
      expect(() => trustedUrl(value, true)).toThrow();
    expect(() => trustedUrl("http://127.0.0.1:8080")).toThrow();
    expect(trustedUrl("http://127.0.0.1:8080", true).hostname).toBe(
      "127.0.0.1",
    );
    vi.stubEnv("OULA_SESSION_SECRET", "short");
    expect(() => getServerConfig()).toThrow();
  });
});

describe("cookie-authenticated finite API bridge", () => {
  it("passes exact identity, purpose and idempotency headers and validates a server response", async () => {
    const fetchStub = vi.fn(async () =>
      Response.json(intentResult, {
        status: 201,
        headers: { "Idempotency-Replayed": "false" },
      }),
    );
    vi.stubGlobal("fetch", fetchStub);
    const response = await bridgeRequest(request(), "/v1/intents", baseSession);
    expect(response.status).toBe(201);
    expect(await response.json()).toEqual(intentResult);
    const call = fetchStub.mock.calls[0] as unknown as [URL, RequestInit];
    expect(String(call[0])).toBe("http://127.0.0.1:8080/v1/intents");
    const headers = new Headers(call[1].headers);
    expect(headers.get("Authorization")).toBe("Bearer synthetic-test-bearer");
    expect(headers.get("X-OULA-Workspace-ID")).toBe(workspace);
    expect(headers.get("X-OULA-Purpose")).toBe("PROPERTY_DECISION_SUPPORT");
    expect(headers.get("Idempotency-Key")).toBe("same-command-key");
    expect(call[1].redirect).toBe("error");
    expect(call[1].cache).toBe("no-store");
    expect(response.headers.get("Cache-Control")).toBe("no-store");
  });
  it.each([
    ["unsigned", null, {}, 401],
    ["expired", { ...baseSession, exp: 1 }, {}, 401],
    ["scope", { ...baseSession, scopes: [] }, {}, 403],
    ["purpose", { ...baseSession, purposes: ["PROPERTY_MANAGEMENT"] }, {}, 403],
    ["workspace", { ...baseSession, workspaceId: otherWorkspace }, {}, 403],
    ["origin", baseSession, { Origin: "https://attacker.example" }, 403],
    ["idempotency", baseSession, { "Idempotency-Key": "" }, 400],
  ])(
    "denies %s before contacting the backend",
    async (_name, session, headers, expected) => {
      const fetchStub = vi.fn();
      vi.stubGlobal("fetch", fetchStub);
      const response = await bridgeRequest(
        request(
          "/v1/intents",
          "POST",
          intentBody,
          headers as Record<string, string>,
        ),
        "/v1/intents",
        session as ServerSession | null,
      );
      expect(response.status).toBe(expected);
      expect(fetchStub).not.toHaveBeenCalled();
    },
  );
  it("ignores client-forged workspace/purpose/authorization headers", async () => {
    const fetchStub = vi.fn(async () =>
      Response.json(intentResult, { status: 201 }),
    );
    vi.stubGlobal("fetch", fetchStub);
    await bridgeRequest(
      request("/v1/intents", "POST", intentBody, {
        "X-OULA-Workspace-ID": otherWorkspace,
        "X-OULA-Purpose": "TRANSACTION_EXECUTION",
        Authorization: "Bearer browser-forged",
      }),
      "/v1/intents",
      baseSession,
    );
    const headers = new Headers(
      (fetchStub.mock.calls[0] as unknown as [URL, RequestInit])[1].headers,
    );
    expect(headers.get("X-OULA-Workspace-ID")).toBe(workspace);
    expect(headers.get("X-OULA-Purpose")).toBe("PROPERTY_DECISION_SUPPORT");
  });
  it.each([401, 403, 404, 409, 503])(
    "preserves an upstream %s failure without a fixture fallback or automatic retry",
    async (status) => {
      const fetchStub = vi.fn(async () =>
        Response.json({ secret: "never forward error details" }, { status }),
      );
      vi.stubGlobal("fetch", fetchStub);
      const response = await bridgeRequest(
        request(),
        "/v1/intents",
        baseSession,
      );
      expect(response.status).toBe(status === 503 ? 502 : status);
      expect(await response.text()).not.toContain("secret");
      expect(fetchStub).toHaveBeenCalledTimes(1);
    },
  );
  it("fails closed on wrong response shape, workspace, resource identity and network failure", async () => {
    const fetchStub = vi.fn();
    vi.stubGlobal("fetch", fetchStub);
    for (const result of [
      { fiction: true },
      { ...intentResult, workspaceId: otherWorkspace },
      { ...intentResult, id: otherWorkspace },
    ]) {
      fetchStub.mockResolvedValueOnce(Response.json(result));
      const response = await bridgeRequest(
        request(`/v1/intents/${id}`, "GET"),
        `/v1/intents/${id}`,
        baseSession,
      );
      expect(response.status).toBe(502);
      expect(await response.json()).toEqual({ code: "CONTRACT_MISMATCH" });
    }
    fetchStub.mockRejectedValueOnce(new Error("private service address"));
    const response = await bridgeRequest(request(), "/v1/intents", baseSession);
    expect(await response.json()).toEqual({ code: "BACKEND_UNAVAILABLE" });
  });
  it("rejects invalid inputs and uncontracted mutation routes", async () => {
    const fetchStub = vi.fn();
    vi.stubGlobal("fetch", fetchStub);
    expect(
      (
        await bridgeRequest(
          request("/v1/intents", "POST", { ...intentBody, budgetMax: 0 }),
          "/v1/intents",
          baseSession,
        )
      ).status,
    ).toBe(400);
    for (const path of [
      `/v1/properties/${id}/guardian/assess`,
      "/v1/../actuator/env",
      "/v1/intents?destination=external",
    ]) {
      expect(
        (await bridgeRequest(request(path), path, baseSession)).status,
      ).toBe(404);
    }
    expect(fetchStub).not.toHaveBeenCalled();
  });
  it("uses the permitted alternate passport purpose and management purpose for vitals", () => {
    expect(
      resolveApiContract("GET", `/v1/properties/${id}/passport`)
        ?.alternatePurpose,
    ).toBe("PROPERTY_MANAGEMENT");
    expect(
      resolveApiContract("GET", `/v1/properties/${id}/vitals/latest`)?.purpose,
    ).toBe("PROPERTY_MANAGEMENT");
  });
  it("validates transaction version and reason instead of changing state locally", async () => {
    const fetchStub = vi.fn();
    vi.stubGlobal("fetch", fetchStub);
    const path = `/v1/transactions/${id}/transitions`;
    expect(
      (
        await bridgeRequest(
          request(path, "POST", {
            expectedVersion: -1,
            target: "COMPLETED",
            reason: "",
          }),
          path,
          baseSession,
        )
      ).status,
    ).toBe(400);
    expect(fetchStub).not.toHaveBeenCalled();
  });
});

describe("browser transport", () => {
  it("uses only the same-origin cookie bridge, never browser bearer credentials", async () => {
    const fetchStub = vi.fn(async () =>
      Response.json(intentResult, { status: 201 }),
    );
    vi.stubGlobal("fetch", fetchStub);
    await createBrowserOulaApi().createIntent(
      intentBody as Parameters<
        ReturnType<typeof createBrowserOulaApi>["createIntent"]
      >[0],
      "retry-key",
    );
    const call = fetchStub.mock.calls[0] as unknown as [string, RequestInit];
    expect(call[0]).toBe("/api/oula/v1/intents");
    expect(call[1].credentials).toBe("same-origin");
    expect(new Headers(call[1].headers).has("Authorization")).toBe(false);
  });
  it("rejects a malformed successful response and forwards a 409 without retry", async () => {
    const fetchStub = vi
      .fn()
      .mockResolvedValueOnce(Response.json({}))
      .mockResolvedValueOnce(
        Response.json({ code: "CONFLICT" }, { status: 409 }),
      );
    vi.stubGlobal("fetch", fetchStub);
    await expect(createBrowserOulaApi().getIntent(id)).rejects.toThrow(
      "CONTRACT_MISMATCH",
    );
    await expect(createBrowserOulaApi().getIntent(id)).rejects.toMatchObject({
      status: 409,
      message: "CONFLICT",
    });
    expect(fetchStub).toHaveBeenCalledTimes(2);
  });
});
