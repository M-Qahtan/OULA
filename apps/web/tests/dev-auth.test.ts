import { afterEach, describe, expect, it, vi } from "vitest";
import { acquireDevAuthSession, devContext, prepareDevGoldenPath } from "../lib/dev-auth";

afterEach(() => {
  vi.unstubAllGlobals();
});

const session = {
  accessToken: "signed-local-test-token",
  tokenType: "Bearer" as const,
  expiresAt: "2026-10-15T05:00:00Z",
  mode: "DEV_TEST_ONLY" as const,
  fixture: "buyer",
  actorId: "10000000-0000-4000-8000-000000000001",
  workspaceId: "20000000-0000-4000-8000-000000000001",
  purposes: ["PROPERTY_DECISION_SUPPORT", "TRANSACTION_EXECUTION"],
  scopes: ["oula.matching.run"],
};

describe("dev auth browser seam", () => {
  it("acquires only the explicit DEV_TEST_ONLY fixture contract", async () => {
    const fetchStub = vi.fn(async (_url: RequestInfo | URL, _init?: RequestInit): Promise<Response> =>
      new Response(JSON.stringify(session), {
        status: 200,
        headers: { "Content-Type": "application/json", "Cache-Control": "no-store" },
      }));
    vi.stubGlobal("fetch", fetchStub);

    await expect(acquireDevAuthSession("http://127.0.0.1:8080", "buyer")).resolves.toEqual(session);

    const call = fetchStub.mock.calls[0];
    expect(String(call?.[0])).toBe("http://127.0.0.1:8080/dev-auth/token");
    expect(call?.[1]?.method).toBe("POST");
    expect(call?.[1]?.cache).toBe("no-store");
    expect(JSON.parse(String(call?.[1]?.body))).toEqual({ fixture: "buyer" });
  });

  it("refuses to construct a purpose context not granted to the fixture", () => {
    expect(devContext(session, "TRANSACTION_EXECUTION")).toEqual({
      accessToken: session.accessToken,
      workspaceId: session.workspaceId,
      purpose: "TRANSACTION_EXECUTION",
    });
    expect(() => devContext(session, "PROPERTY_MANAGEMENT"))
      .toThrow("Fixture is not authorized for purpose: PROPERTY_MANAGEMENT");
  });

  it("prepares matching fixtures only through the authenticated dev-only endpoint", async () => {
    const prepared = {
      mode: "DEV_TEST_ONLY",
      intentId: "123e4567-e89b-42d3-a456-426614174050",
      propertyIds: [
        "30000000-0000-4000-8000-000000000001",
        "30000000-0000-4000-8000-000000000002",
        "30000000-0000-4000-8000-000000000003",
      ],
      signalSource: "DEV_FIXTURE_SIMULATION",
      verifiedExternalData: false,
    } as const;
    const fetchStub = vi.fn(async (_url: RequestInfo | URL, _init?: RequestInit): Promise<Response> =>
      new Response(JSON.stringify(prepared), { status: 200, headers: { "Content-Type": "application/json" } }));
    vi.stubGlobal("fetch", fetchStub);

    await expect(prepareDevGoldenPath(
      "http://127.0.0.1:8080",
      session,
      prepared.intentId,
    )).resolves.toEqual(prepared);

    const call = fetchStub.mock.calls[0];
    expect(String(call?.[0])).toBe(
      "http://127.0.0.1:8080/dev-auth/golden-path/intents/123e4567-e89b-42d3-a456-426614174050/prepare",
    );
    expect(call?.[1]?.method).toBe("POST");
    const headers = new Headers(call?.[1]?.headers);
    expect(headers.get("Authorization")).toBe("Bearer signed-local-test-token");
    expect(headers.get("X-OULA-Workspace-ID")).toBe(session.workspaceId);
    expect(headers.get("X-OULA-Purpose")).toBe("PROPERTY_DECISION_SUPPORT");
  });

  it("rejects a response that tries to present dev fixtures as verified external data", async () => {
    vi.stubGlobal("fetch", vi.fn(async (_url: RequestInfo | URL, _init?: RequestInit): Promise<Response> =>
      new Response(JSON.stringify({
        mode: "DEV_TEST_ONLY",
        intentId: "x",
        propertyIds: [],
        signalSource: "DEV_FIXTURE_SIMULATION",
        verifiedExternalData: true,
      }), { status: 200, headers: { "Content-Type": "application/json" } })));

    await expect(prepareDevGoldenPath("http://127.0.0.1:8080", session, "x"))
      .rejects.toThrow("Unexpected dev fixture provenance contract");
  });
});
