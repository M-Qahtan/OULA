import { afterEach, describe, expect, it, vi } from "vitest";
import { readFileSync } from "node:fs";
import { demoProperties, previewMatch } from "../lib/model";
import { createOulaApi, OulaApiError } from "../lib/api";

const intent = { budgetSar: 1200000, minBedrooms: 3, minAreaSqm: 155, preferredDistrict: "الملقا" };

describe("OULA preview boundary", () => {
  it("denies candidates violating mandatory constraints", () => {
    expect(previewMatch(intent, demoProperties[1]!).blocked).toBe(true);
    expect(previewMatch(intent, demoProperties[2]!).blocked).toBe(true);
  });
  it("exposes a deterministic transparent demo score", () => {
    const result = previewMatch(intent, demoProperties[0]!);
    expect(result.blocked).toBe(false);
    expect(result.score).toBeGreaterThan(0);
    expect(result.reasons.some((x) => x.includes("توضيحي"))).toBe(true);
  });
  it("does not allow an unauthenticated API to masquerade as live", () => {
    expect(() => createOulaApi("http://localhost:8080", { accessToken: "", workspaceId: "demo", purpose: "PROPERTY_MANAGEMENT" })).toThrow();
  });
  it("declares demo properties only, never verified inventory", () => {
    expect(demoProperties.every((p) => p.id.startsWith("demo-") && p.evidence !== "EXPIRED")).toBe(true);
  });
});

describe("OULA Java OpenAPI v1.10 contract", () => {
  afterEach(() => vi.unstubAllGlobals());

  const context = {
    accessToken: "test-token-not-a-production-secret",
    workspaceId: "123e4567-e89b-42d3-a456-426614174000",
    purpose: "TRANSACTION_EXECUTION" as const,
  };

  it("uses exact workspace and transaction-purpose headers required by OpenAPI", async () => {
    const fetchStub = vi.fn(async (_url: RequestInfo | URL, _init?: RequestInit): Promise<Response> =>
      new Response(JSON.stringify({ id: "test" }), { status: 200, headers: { "Content-Type": "application/json" } }));
    vi.stubGlobal("fetch", fetchStub);

    const api = createOulaApi("http://localhost:8080", context);
    await api.transaction("123e4567-e89b-42d3-a456-426614174001");

    expect(fetchStub).toHaveBeenCalledTimes(1);
    const call = fetchStub.mock.calls[0];
    expect(String(call?.[0])).toBe("http://localhost:8080/v1/transactions/123e4567-e89b-42d3-a456-426614174001");
    const headers = new Headers(call?.[1]?.headers);
    expect(headers.get("Authorization")).toBe("Bearer test-token-not-a-production-secret");
    expect(headers.get("X-OULA-Workspace-ID")).toBe(context.workspaceId);
    expect(headers.get("X-OULA-Workspace")).toBeNull();
    expect(headers.get("X-OULA-Purpose")).toBe("TRANSACTION_EXECUTION");
  });

  it("does not replace an authentication rejection with demo results", async () => {
    const fetchStub = vi.fn(async () => new Response(JSON.stringify({ error: "Forbidden" }), { status: 403 }));
    vi.stubGlobal("fetch", fetchStub);
    await expect(createOulaApi("http://localhost:8080", context).passport("123e4567-e89b-42d3-a456-426614174002"))
      .rejects.toMatchObject({ name: "OulaApiError", status: 403 } satisfies Partial<OulaApiError>);
    expect(fetchStub).toHaveBeenCalledTimes(1);
  });

  it("keeps workspace and purpose names pinned to the real OpenAPI contract", () => {
    const contract = readFileSync(new URL("../../../contracts/openapi.yaml", import.meta.url), "utf8");
    expect(contract).toContain("name: X-OULA-Workspace-ID");
    expect(contract).toContain("name: X-OULA-Purpose");
    expect(contract).toContain("PROPERTY_DECISION_SUPPORT, TRANSACTION_EXECUTION, PROPERTY_MANAGEMENT");
  });
});
