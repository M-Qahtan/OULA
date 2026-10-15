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

  it("reads transaction state with exact workspace and transaction-purpose headers", async () => {
    const fetchStub = vi.fn(async (_url: RequestInfo | URL, _init?: RequestInit): Promise<Response> =>
      new Response(JSON.stringify({ id: "test" }), { status: 200, headers: { "Content-Type": "application/json" } }));
    vi.stubGlobal("fetch", fetchStub);

    const api = createOulaApi("http://localhost:8080", context);
    await api.getTransaction("123e4567-e89b-42d3-a456-426614174001");

    expect(fetchStub).toHaveBeenCalledTimes(1);
    const call = fetchStub.mock.calls[0];
    expect(String(call?.[0])).toBe("http://localhost:8080/v1/transactions/123e4567-e89b-42d3-a456-426614174001");
    const headers = new Headers(call?.[1]?.headers);
    expect(headers.get("Authorization")).toBe("Bearer test-token-not-a-production-secret");
    expect(headers.get("X-OULA-Workspace-ID")).toBe(context.workspaceId);
    expect(headers.get("X-OULA-Workspace")).toBeNull();
    expect(headers.get("X-OULA-Purpose")).toBe("TRANSACTION_EXECUTION");
  });

  it("advances a transaction with explicit optimistic version and idempotency", async () => {
    const responseBody = {
      id: "123e4567-e89b-42d3-a456-426614174001",
      workspaceId: context.workspaceId,
      stage: "QUALIFIED",
      version: 1,
    };
    const fetchStub = vi.fn(async (_url: RequestInfo | URL, _init?: RequestInit): Promise<Response> =>
      new Response(JSON.stringify(responseBody), { status: 200, headers: { "Content-Type": "application/json" } }));
    vi.stubGlobal("fetch", fetchStub);

    const api = createOulaApi("http://localhost:8080", context);
    const input = { expectedVersion: 0, target: "QUALIFIED" as const, reason: "Buyer qualified for transaction flow" };
    await expect(api.advanceTransaction(
      "123e4567-e89b-42d3-a456-426614174001",
      input,
      "tx-transition-key-001",
    )).resolves.toEqual(responseBody);

    const call = fetchStub.mock.calls[0];
    expect(String(call?.[0])).toBe("http://localhost:8080/v1/transactions/123e4567-e89b-42d3-a456-426614174001/transitions");
    expect(call?.[1]?.method).toBe("POST");
    const headers = new Headers(call?.[1]?.headers);
    expect(headers.get("Authorization")).toBe("Bearer test-token-not-a-production-secret");
    expect(headers.get("X-OULA-Workspace-ID")).toBe(context.workspaceId);
    expect(headers.get("X-OULA-Purpose")).toBe("TRANSACTION_EXECUTION");
    expect(headers.get("Idempotency-Key")).toBe("tx-transition-key-001");
    expect(headers.get("Content-Type")).toBe("application/json");
    expect(JSON.parse(String(call?.[1]?.body))).toEqual(input);
  });

  it("rejects a blank transaction idempotency key before any network call", () => {
    const fetchStub = vi.fn();
    vi.stubGlobal("fetch", fetchStub);
    const api = createOulaApi("http://localhost:8080", context);
    expect(() => api.advanceTransaction(
      "123e4567-e89b-42d3-a456-426614174001",
      { expectedVersion: 0, target: "QUALIFIED", reason: "Buyer qualified for transaction flow" },
      " ",
    )).toThrow("Idempotency-Key required");
    expect(fetchStub).not.toHaveBeenCalled();
  });

  it("surfaces stale transaction writes as conflicts instead of hiding them", async () => {
    const fetchStub = vi.fn(async () =>
      new Response(JSON.stringify({ error: "Conflict" }), { status: 409, headers: { "Content-Type": "application/json" } }));
    vi.stubGlobal("fetch", fetchStub);

    const api = createOulaApi("http://localhost:8080", context);
    await expect(api.advanceTransaction(
      "123e4567-e89b-42d3-a456-426614174001",
      { expectedVersion: 0, target: "QUALIFIED", reason: "stale client attempt" },
      "tx-transition-key-stale",
    )).rejects.toMatchObject({ name: "OulaApiError", status: 409 } satisfies Partial<OulaApiError>);
    expect(fetchStub).toHaveBeenCalledTimes(1);
  });

  it("creates a transaction through the canonical idempotent boundary", async () => {
    const responseBody = {
      id: "123e4567-e89b-42d3-a456-426614174040",
      workspaceId: context.workspaceId,
      stage: "DRAFT",
      version: 0,
    };
    const fetchStub = vi.fn(async (_url: RequestInfo | URL, _init?: RequestInit): Promise<Response> =>
      new Response(JSON.stringify(responseBody), { status: 201, headers: { "Content-Type": "application/json" } }));
    vi.stubGlobal("fetch", fetchStub);

    const api = createOulaApi("http://localhost:8080", context);
    const input = {
      intentId: "123e4567-e89b-42d3-a456-426614174041",
      propertyId: "123e4567-e89b-42d3-a456-426614174042",
    };
    await expect(api.createTransaction(input, "transaction-create-key-001")).resolves.toEqual(responseBody);

    const call = fetchStub.mock.calls[0];
    expect(String(call?.[0])).toBe("http://localhost:8080/v1/transactions");
    expect(call?.[1]?.method).toBe("POST");
    const headers = new Headers(call?.[1]?.headers);
    expect(headers.get("X-OULA-Purpose")).toBe("TRANSACTION_EXECUTION");
    expect(headers.get("Idempotency-Key")).toBe("transaction-create-key-001");
    expect(headers.get("Content-Type")).toBe("application/json");
    expect(JSON.parse(String(call?.[1]?.body))).toEqual(input);
  });

  it("reads the canonical Deal Room overview without promoting it to LIVE", async () => {
    const responseBody = {
      transaction: {
        id: "123e4567-e89b-42d3-a456-426614174040",
        workspaceId: context.workspaceId,
        stage: "QUALIFIED",
        version: 1,
      },
      viewings: [],
      offers: [],
      availableCommands: ["REQUEST_VIEWING"],
      humanConfirmationRequired: true,
    };
    const fetchStub = vi.fn(async (_url: RequestInfo | URL, _init?: RequestInit): Promise<Response> =>
      new Response(JSON.stringify(responseBody), { status: 200, headers: { "Content-Type": "application/json" } }));
    vi.stubGlobal("fetch", fetchStub);

    const api = createOulaApi("http://localhost:8080", context);
    await expect(api.getDealRoom("123e4567-e89b-42d3-a456-426614174040")).resolves.toEqual(responseBody);
    const call = fetchStub.mock.calls[0];
    expect(String(call?.[0])).toBe("http://localhost:8080/v1/transactions/123e4567-e89b-42d3-a456-426614174040/deal-room");
    expect(call?.[1]?.method).toBeUndefined();
  });

  it("requests viewing through the dedicated human-confirmed command", async () => {
    const responseBody = {
      transaction: {
        id: "123e4567-e89b-42d3-a456-426614174040",
        workspaceId: context.workspaceId,
        stage: "VIEWING",
        version: 2,
      },
      viewing: {
        id: "123e4567-e89b-42d3-a456-426614174043",
        transactionId: "123e4567-e89b-42d3-a456-426614174040",
        preferredAt: "2026-10-20T16:30:00Z",
        note: "Family viewing",
        status: "REQUESTED",
        requestedBy: "123e4567-e89b-42d3-a456-426614174044",
        createdAt: "2026-10-15T04:00:00Z",
      },
    };
    const fetchStub = vi.fn(async (_url: RequestInfo | URL, _init?: RequestInit): Promise<Response> =>
      new Response(JSON.stringify(responseBody), { status: 201, headers: { "Content-Type": "application/json" } }));
    vi.stubGlobal("fetch", fetchStub);

    const api = createOulaApi("http://localhost:8080", context);
    const input = {
      expectedVersion: 1,
      preferredAt: "2026-10-20T16:30:00Z",
      note: "Family viewing",
      humanConfirmed: true as const,
    };
    await expect(api.requestTransactionViewing(
      "123e4567-e89b-42d3-a456-426614174040",
      input,
      "viewing-key-001",
    )).resolves.toEqual(responseBody);

    const call = fetchStub.mock.calls[0];
    expect(String(call?.[0])).toBe("http://localhost:8080/v1/transactions/123e4567-e89b-42d3-a456-426614174040/viewings");
    expect(call?.[1]?.method).toBe("POST");
    const headers = new Headers(call?.[1]?.headers);
    expect(headers.get("Idempotency-Key")).toBe("viewing-key-001");
    expect(JSON.parse(String(call?.[1]?.body))).toEqual(input);
  });

  it("submits offer through the dedicated human-confirmed command", async () => {
    const responseBody = {
      transaction: {
        id: "123e4567-e89b-42d3-a456-426614174040",
        workspaceId: context.workspaceId,
        stage: "OFFERING",
        version: 3,
      },
      offer: {
        id: "123e4567-e89b-42d3-a456-426614174045",
        transactionId: "123e4567-e89b-42d3-a456-426614174040",
        amount: 1050000,
        currency: "SAR",
        termsSummary: "Subject to review",
        status: "SUBMITTED",
        submittedBy: "123e4567-e89b-42d3-a456-426614174044",
        createdAt: "2026-10-15T04:05:00Z",
      },
    };
    const fetchStub = vi.fn(async (_url: RequestInfo | URL, _init?: RequestInit): Promise<Response> =>
      new Response(JSON.stringify(responseBody), { status: 201, headers: { "Content-Type": "application/json" } }));
    vi.stubGlobal("fetch", fetchStub);

    const api = createOulaApi("http://localhost:8080", context);
    const input = {
      expectedVersion: 2,
      amount: 1050000,
      currency: "SAR",
      termsSummary: "Subject to review",
      humanConfirmed: true as const,
    };
    await expect(api.submitTransactionOffer(
      "123e4567-e89b-42d3-a456-426614174040",
      input,
      "offer-key-001",
    )).resolves.toEqual(responseBody);

    const call = fetchStub.mock.calls[0];
    expect(String(call?.[0])).toBe("http://localhost:8080/v1/transactions/123e4567-e89b-42d3-a456-426614174040/offers");
    expect(call?.[1]?.method).toBe("POST");
    const headers = new Headers(call?.[1]?.headers);
    expect(headers.get("Idempotency-Key")).toBe("offer-key-001");
    expect(JSON.parse(String(call?.[1]?.body))).toEqual(input);
  });

  it("rejects blank idempotency keys for dedicated Deal Room writes before network access", () => {
    const fetchStub = vi.fn();
    vi.stubGlobal("fetch", fetchStub);
    const api = createOulaApi("http://localhost:8080", context);

    expect(() => api.createTransaction(
      { intentId: "123e4567-e89b-42d3-a456-426614174041", propertyId: "123e4567-e89b-42d3-a456-426614174042" },
      " ",
    )).toThrow("Idempotency-Key required");
    expect(() => api.requestTransactionViewing(
      "123e4567-e89b-42d3-a456-426614174040",
      { expectedVersion: 1, humanConfirmed: true },
      "",
    )).toThrow("Idempotency-Key required");
    expect(() => api.submitTransactionOffer(
      "123e4567-e89b-42d3-a456-426614174040",
      { expectedVersion: 2, amount: 1000000, currency: "SAR", humanConfirmed: true },
      " ",
    )).toThrow("Idempotency-Key required");
    expect(fetchStub).not.toHaveBeenCalled();
  });

  it("reads and writes explicit consent through typed purpose-bound adapters", async () => {
    const responses = [
      new Response(JSON.stringify([]), { status: 200, headers: { "Content-Type": "application/json" } }),
      new Response(JSON.stringify({
        consentCode: "LIFE_CONTEXT_PROCESSING",
        decision: "GRANTED",
        reason: "User enabled life-context processing",
        source: "USER_EXPLICIT",
        occurredAt: "2026-10-15T04:20:00Z",
      }), { status: 201, headers: { "Content-Type": "application/json" } }),
    ];
    const fetchStub = vi.fn(async (_url: RequestInfo | URL, _init?: RequestInit): Promise<Response> => {
      const next = responses.shift();
      if (!next) throw new Error("unexpected fetch");
      return next;
    });
    vi.stubGlobal("fetch", fetchStub);

    const api = createOulaApi("http://localhost:8080", { ...context, purpose: "PROPERTY_DECISION_SUPPORT" });
    await expect(api.getConsents()).resolves.toEqual([]);
    const input = {
      consentCode: "LIFE_CONTEXT_PROCESSING" as const,
      decision: "GRANTED" as const,
      reason: "User enabled life-context processing",
    };
    await expect(api.decideConsent(input, "consent-key-001")).resolves.toMatchObject({
      consentCode: "LIFE_CONTEXT_PROCESSING",
      decision: "GRANTED",
      source: "USER_EXPLICIT",
    });

    expect(String(fetchStub.mock.calls[0]?.[0])).toBe("http://localhost:8080/v1/consents");
    expect(fetchStub.mock.calls[0]?.[1]?.method).toBeUndefined();

    const writeCall = fetchStub.mock.calls[1];
    expect(String(writeCall?.[0])).toBe("http://localhost:8080/v1/consents/decisions");
    expect(writeCall?.[1]?.method).toBe("POST");
    const headers = new Headers(writeCall?.[1]?.headers);
    expect(headers.get("X-OULA-Purpose")).toBe("PROPERTY_DECISION_SUPPORT");
    expect(headers.get("Idempotency-Key")).toBe("consent-key-001");
    expect(JSON.parse(String(writeCall?.[1]?.body))).toEqual(input);
  });

  it("rejects blank consent idempotency keys before network access", () => {
    const fetchStub = vi.fn();
    vi.stubGlobal("fetch", fetchStub);
    const api = createOulaApi("http://localhost:8080", { ...context, purpose: "PROPERTY_DECISION_SUPPORT" });
    expect(() => api.decideConsent(
      { consentCode: "LIFE_CONTEXT_PROCESSING", decision: "GRANTED" },
      " ",
    )).toThrow("Idempotency-Key required");
    expect(fetchStub).not.toHaveBeenCalled();
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

  it("runs matching with the canonical idempotent POST boundary", async () => {
    const responseBody = {
      matchRunId: "123e4567-e89b-42d3-a456-426614174020",
      matches: [{
        propertyId: "123e4567-e89b-42d3-a456-426614174021",
        rank: 1,
        score: 87.45,
        confidence: 0.92,
        dimensions: { financial: 88, mobility: 73, future: 86 },
      }],
    };
    const fetchStub = vi.fn(async (_url: RequestInfo | URL, _init?: RequestInit): Promise<Response> =>
      new Response(JSON.stringify(responseBody), { status: 201, headers: { "Content-Type": "application/json" } }));
    vi.stubGlobal("fetch", fetchStub);

    const decisionApi = createOulaApi("http://localhost:8080", { ...context, purpose: "PROPERTY_DECISION_SUPPORT" });
    await expect(decisionApi.runMatching(
      "123e4567-e89b-42d3-a456-426614174019",
      "match-test-key-001",
    )).resolves.toEqual(responseBody);

    const call = fetchStub.mock.calls[0];
    expect(String(call?.[0])).toBe("http://localhost:8080/v1/intents/123e4567-e89b-42d3-a456-426614174019/matches");
    expect(call?.[1]?.method).toBe("POST");
    expect(call?.[1]?.body).toBeUndefined();
    const headers = new Headers(call?.[1]?.headers);
    expect(headers.get("Authorization")).toBe("Bearer test-token-not-a-production-secret");
    expect(headers.get("X-OULA-Workspace-ID")).toBe(context.workspaceId);
    expect(headers.get("X-OULA-Purpose")).toBe("PROPERTY_DECISION_SUPPORT");
    expect(headers.get("Idempotency-Key")).toBe("match-test-key-001");
  });

  it("rejects a blank matching idempotency key before any network call", () => {
    const fetchStub = vi.fn();
    vi.stubGlobal("fetch", fetchStub);
    const decisionApi = createOulaApi("http://localhost:8080", { ...context, purpose: "PROPERTY_DECISION_SUPPORT" });
    expect(() => decisionApi.runMatching("123e4567-e89b-42d3-a456-426614174019", " "))
      .toThrow("Idempotency-Key required");
    expect(fetchStub).not.toHaveBeenCalled();
  });

  it("reads the truth-aware Property Passport without parsing valueJson into invented truth", async () => {
    const responseBody = {
      propertyId: "123e4567-e89b-42d3-a456-426614174030",
      workspaceId: context.workspaceId,
      assetType: "APARTMENT",
      district: "الملقا",
      bedrooms: 3,
      askingPrice: 1090000,
      facts: [{
        id: "123e4567-e89b-42d3-a456-426614174031",
        key: "areaSqm",
        valueJson: "{\"value\":172,\"unit\":\"sqm\"}",
        truthStatus: "DECLARED",
        sourceType: "OWNER_DECLARATION",
        confidence: 0.7,
        validFrom: null,
        validTo: null,
      }],
      latestState: null,
      verifiedFactCoverage: 0,
      generatedAt: "2026-10-11T00:00:00Z",
    };
    const fetchStub = vi.fn(async (_url: RequestInfo | URL, _init?: RequestInit): Promise<Response> =>
      new Response(JSON.stringify(responseBody), { status: 200, headers: { "Content-Type": "application/json" } }));
    vi.stubGlobal("fetch", fetchStub);

    const api = createOulaApi("http://localhost:8080", { ...context, purpose: "PROPERTY_DECISION_SUPPORT" });
    const passport = await api.getPropertyPassport("123e4567-e89b-42d3-a456-426614174030");

    expect(passport).toEqual(responseBody);
    expect(passport.facts[0]?.valueJson).toBe("{\"value\":172,\"unit\":\"sqm\"}");
    expect(typeof passport.facts[0]?.valueJson).toBe("string");
    expect(passport.verifiedFactCoverage).toBe(0);

    const call = fetchStub.mock.calls[0];
    expect(String(call?.[0])).toBe("http://localhost:8080/v1/properties/123e4567-e89b-42d3-a456-426614174030/passport");
    const headers = new Headers(call?.[1]?.headers);
    expect(headers.get("X-OULA-Purpose")).toBe("PROPERTY_DECISION_SUPPORT");
  });

  it("does not replace an authentication rejection with demo results", async () => {
    const fetchStub = vi.fn(async () => new Response(JSON.stringify({ error: "Forbidden" }), { status: 403 }));
    vi.stubGlobal("fetch", fetchStub);
    await expect(createOulaApi("http://localhost:8080", context).getPropertyPassport("123e4567-e89b-42d3-a456-426614174002"))
      .rejects.toMatchObject({ name: "OulaApiError", status: 403 } satisfies Partial<OulaApiError>);
    expect(fetchStub).toHaveBeenCalledTimes(1);
  });

  it("keeps workspace and purpose names pinned to the real OpenAPI contract", () => {
    const contract = readFileSync(new URL("../../../contracts/openapi.yaml", import.meta.url), "utf8");
    expect(contract).toContain("name: X-OULA-Workspace-ID");
    expect(contract).toContain("name: X-OULA-Purpose");
    expect(contract).toContain("PROPERTY_DECISION_SUPPORT, TRANSACTION_EXECUTION, PROPERTY_MANAGEMENT");
    expect(contract).toContain("/v1/intents:");
    expect(contract).toContain("operationId: listConsents");
    expect(contract).toContain("operationId: decideConsent");
    expect(contract).toContain("ConsentDecisionRequest:");
    expect(contract).toContain("ConsentResponse:");
    expect(contract).toContain("operationId: createIntent");
    expect(contract).toContain("operationId: getIntent");
    expect(contract).toContain("IntentCreateRequest:");
    expect(contract).toContain("IntentResponse:");
    expect(contract).toContain("operationId: runMatching");
    expect(contract).toContain("MatchRunResponse:");
    expect(contract).toContain("MatchItem:");
    expect(contract).toContain("operationId: getPropertyPassport");
    expect(contract).toContain("PropertyPassport:");
    expect(contract).toContain("PropertyPassportFact:");
    expect(contract).toContain("valueJson");
    expect(contract).toContain("verifiedFactCoverage");
    expect(contract).toContain("operationId: getTransaction");
    expect(contract).toContain("operationId: advanceTransaction");
    expect(contract).toContain("TransactionTransitionRequest:");
    expect(contract).toContain("expectedVersion");
    expect(contract).toContain("TransactionResponse:");
    expect(contract).toContain("operationId: createTransaction");
    expect(contract).toContain("operationId: getDealRoom");
    expect(contract).toContain("operationId: requestTransactionViewing");
    expect(contract).toContain("operationId: submitTransactionOffer");
    expect(contract).toContain("TransactionCreateRequest:");
    expect(contract).toContain("TransactionViewingRequest:");
    expect(contract).toContain("TransactionOfferRequest:");
    expect(contract).toContain("DealRoomResponse:");
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
