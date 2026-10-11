import { afterEach, describe, expect, it, vi } from "vitest";
import { readFileSync } from "node:fs";
import { canonicalStageForDemoDealStep, canonicalTransactionStages, demoDealProjection, demoProperties, nextDemoDealStep, previewMatch, type DemoDealStep } from "../lib/model";
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

  it("creates an Intent with the exact OpenAPI idempotency and purpose boundary", async () => {
    const responseBody = {
      id: "123e4567-e89b-42d3-a456-426614174010",
      workspaceId: context.workspaceId,
      intentType: "BUY",
      status: "ACTIVE",
      budgetMax: 1200000,
      minimumBedrooms: 3,
      preferredDistricts: ["الملقا"],
    };
    const fetchStub = vi.fn(async (_url: RequestInfo | URL, _init?: RequestInit): Promise<Response> =>
      new Response(JSON.stringify(responseBody), { status: 201, headers: { "Content-Type": "application/json" } }));
    vi.stubGlobal("fetch", fetchStub);

    const decisionApi = createOulaApi("http://localhost:8080", { ...context, purpose: "PROPERTY_DECISION_SUPPORT" });
    const input = { intentType: "BUY" as const, budgetMax: 1200000, minimumBedrooms: 3, preferredDistricts: ["الملقا"] };
    await expect(decisionApi.createIntent(input, "intent-test-key-001")).resolves.toEqual(responseBody);

    const call = fetchStub.mock.calls[0];
    expect(String(call?.[0])).toBe("http://localhost:8080/v1/intents");
    expect(call?.[1]?.method).toBe("POST");
    const headers = new Headers(call?.[1]?.headers);
    expect(headers.get("Authorization")).toBe("Bearer test-token-not-a-production-secret");
    expect(headers.get("X-OULA-Workspace-ID")).toBe(context.workspaceId);
    expect(headers.get("X-OULA-Purpose")).toBe("PROPERTY_DECISION_SUPPORT");
    expect(headers.get("Idempotency-Key")).toBe("intent-test-key-001");
    expect(headers.get("Content-Type")).toBe("application/json");
    expect(JSON.parse(String(call?.[1]?.body))).toEqual(input);
  });

  it("reads an Intent through the workspace-scoped canonical route", async () => {
    const fetchStub = vi.fn(async (_url: RequestInfo | URL, _init?: RequestInit): Promise<Response> =>
      new Response(JSON.stringify({
        id: "123e4567-e89b-42d3-a456-426614174011",
        workspaceId: context.workspaceId,
        intentType: "RENT",
        status: "ACTIVE",
        budgetMax: 90000,
        minimumBedrooms: 2,
        preferredDistricts: ["حطين"],
      }), { status: 200, headers: { "Content-Type": "application/json" } }));
    vi.stubGlobal("fetch", fetchStub);

    const decisionApi = createOulaApi("http://localhost:8080", { ...context, purpose: "PROPERTY_DECISION_SUPPORT" });
    await decisionApi.getIntent("123e4567-e89b-42d3-a456-426614174011");

    const call = fetchStub.mock.calls[0];
    expect(String(call?.[0])).toBe("http://localhost:8080/v1/intents/123e4567-e89b-42d3-a456-426614174011");
    const headers = new Headers(call?.[1]?.headers);
    expect(headers.get("X-OULA-Purpose")).toBe("PROPERTY_DECISION_SUPPORT");
    expect(headers.get("Idempotency-Key")).toBeNull();
  });

  it("rejects an empty Intent idempotency key before any network call", () => {
    const fetchStub = vi.fn();
    vi.stubGlobal("fetch", fetchStub);
    const decisionApi = createOulaApi("http://localhost:8080", { ...context, purpose: "PROPERTY_DECISION_SUPPORT" });
    expect(() => decisionApi.createIntent(
      { intentType: "BUY", budgetMax: 1000000, minimumBedrooms: 2, preferredDistricts: [] },
      "   ",
    )).toThrow("Idempotency-Key required");
    expect(fetchStub).not.toHaveBeenCalled();
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
    expect(contract).toContain("/v1/intents:");
    expect(contract).toContain("operationId: createIntent");
    expect(contract).toContain("operationId: getIntent");
    expect(contract).toContain("IntentCreateRequest:");
    expect(contract).toContain("IntentResponse:");
  });
});


describe("OULA demo transaction projection", () => {
  const steps: DemoDealStep[] = ["NONE", "VIEWING_REQUESTED", "OFFER_PREPARED", "HUMAN_APPROVED"];

  it("maps every browser-only milestone to a canonical backend stage", () => {
    for (const step of steps) {
      expect(canonicalTransactionStages).toContain(canonicalStageForDemoDealStep(step));
    }
  });

  it("does not claim that local human approval advanced the authoritative backend", () => {
    expect(demoDealProjection.HUMAN_APPROVED.canonicalStage).toBe("OFFERING");
  });

  it("advances only through the explicit demo sequence and stops after approval", () => {
    let step: DemoDealStep = "NONE";
    step = nextDemoDealStep(step);
    expect(step).toBe("VIEWING_REQUESTED");
    step = nextDemoDealStep(step);
    expect(step).toBe("OFFER_PREPARED");
    step = nextDemoDealStep(step);
    expect(step).toBe("HUMAN_APPROVED");
    expect(nextDemoDealStep(step)).toBe("HUMAN_APPROVED");
  });

  it("keeps the projection pinned to the canonical OpenAPI stage vocabulary", () => {
    const contract = readFileSync(new URL("../../../contracts/openapi.yaml", import.meta.url), "utf8");
    for (const stage of canonicalTransactionStages) {
      expect(contract).toContain(stage);
    }
  });
});
