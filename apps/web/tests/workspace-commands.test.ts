import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { bridgeRequest } from "../lib/server/bridge";
import { localDevAuthEnabled } from "../lib/server/config";
import {
  offerInputSchema,
  viewingInputSchema,
  recommendationInputSchema,
} from "../lib/api-contracts";
import type { ServerSession } from "../lib/server/session";

const workspace = "20000000-0000-4000-8000-000000000001";
const id = "30000000-0000-4000-8000-000000000001";
const other = "30000000-0000-4000-8000-000000000002";
const session: ServerSession = {
  mode: "OIDC",
  accessToken: "opaque-test-token",
  subject: "test",
  actorId: id,
  workspaceId: workspace,
  workspaceIds: [workspace],
  purposes: ["TRANSACTION_EXECUTION"],
  scopes: [
    "oula.transaction.create",
    "oula.transaction.read",
    "oula.transaction.viewing.request",
    "oula.transaction.offer.submit",
  ],
  exp: Math.floor(Date.now() / 1000) + 900,
};
const transaction = { id, workspaceId: workspace, stage: "DRAFT", version: 0 };
const room = {
  transaction,
  viewings: [],
  offers: [],
  availableCommands: ["QUALIFY"],
  humanConfirmationRequired: true,
};
const request = (path: string, method = "GET", body?: unknown) =>
  new Request("http://127.0.0.1:3000/api/oula" + path, {
    method,
    headers: {
      Origin: "http://127.0.0.1:3000",
      "Content-Type": "application/json",
      "Idempotency-Key": "command-1",
    },
    ...(body ? { body: JSON.stringify(body) } : {}),
  });
beforeEach(() => {
  vi.stubEnv("OULA_BACKEND_URL", "http://127.0.0.1:8080");
  vi.stubEnv("OULA_APP_ORIGIN", "http://127.0.0.1:3000");
  vi.stubEnv("OULA_OIDC_ISSUER", "http://localhost:8080/dev-auth");
  vi.stubEnv("OULA_OIDC_CLIENT_ID", "oula-web");
  vi.stubEnv("OULA_SESSION_SECRET", "test-secret-with-at-least-32-characters");
  vi.stubEnv("OULA_ALLOW_HTTP_LOCALHOST", "true");
  vi.stubEnv("OULA_LOCAL_DEV_AUTH", "false");
});
afterEach(() => {
  vi.unstubAllEnvs();
  vi.unstubAllGlobals();
});

describe("workspace transaction boundary", () => {
  it("forwards a draft to the canonical transaction contract", async () => {
    const fetcher = vi.fn(async () =>
      Response.json(transaction, { status: 201 }),
    );
    vi.stubGlobal("fetch", fetcher);
    const path = "/v1/transactions";
    const response = await bridgeRequest(
      request(path, "POST", { intentId: other, propertyId: id }),
      path,
      session,
    );
    expect(response.status).toBe(201);
    const headers = new Headers(
      (fetcher.mock.calls[0] as unknown as [URL, RequestInit])[1].headers,
    );
    expect(headers.get("X-OULA-Purpose")).toBe("TRANSACTION_EXECUTION");
    expect(headers.get("Idempotency-Key")).toBe("command-1");
  });
  it.each([
    { ...room, transaction: { ...transaction, workspaceId: other } },
    { ...room, transaction: { ...transaction, id: other } },
    {
      ...room,
      viewings: [
        {
          id: other,
          transactionId: other,
          status: "REQUESTED",
          requestedBy: id,
          createdAt: new Date().toISOString(),
        },
      ],
    },
  ])(
    "rejects a nested resource outside the requested transaction",
    async (value) => {
      vi.stubGlobal(
        "fetch",
        vi.fn(async () => Response.json(value)),
      );
      const path = `/v1/transactions/${id}/deal-room`;
      const response = await bridgeRequest(request(path), path, session);
      expect(response.status).toBe(502);
      expect(await response.json()).toEqual({ code: "CONTRACT_MISMATCH" });
    },
  );
  it("denies viewing and offers without human confirmation before contacting Java", async () => {
    const fetcher = vi.fn();
    vi.stubGlobal("fetch", fetcher);
    for (const endpoint of ["viewings", "offers"]) {
      const path = `/v1/transactions/${id}/${endpoint}`;
      const response = await bridgeRequest(
        request(path, "POST", {
          expectedVersion: 0,
          amount: 100,
          currency: "SAR",
          humanConfirmed: false,
        }),
        path,
        session,
      );
      expect(response.status).toBe(400);
    }
    expect(fetcher).not.toHaveBeenCalled();
  });
  it("denies an existing fixture after local identity is disabled", async () => {
    const fetcher = vi.fn();
    vi.stubGlobal("fetch", fetcher);
    const path = `/v1/transactions/${id}/deal-room`;
    expect(
      (
        await bridgeRequest(request(path), path, {
          ...session,
          mode: "DEV_TEST_ONLY",
        })
      ).status,
    ).toBe(401);
    expect(fetcher).not.toHaveBeenCalled();
  });
});

describe("local identity and evidence admission", () => {
  it("requires an explicit flag and literal loopback services", () => {
    expect(localDevAuthEnabled()).toBe(false);
    vi.stubEnv("OULA_LOCAL_DEV_AUTH", "true");
    expect(localDevAuthEnabled()).toBe(true);
    vi.stubEnv("OULA_APP_ORIGIN", "https://app.example.com");
    expect(localDevAuthEnabled()).toBe(false);
  });
  it("requires eligible evidence and positive, human-confirmed offer inputs", () => {
    expect(
      recommendationInputSchema.safeParse({
        matchRunId: id,
        evidenceIds: [],
        validHours: 24,
      }).success,
    ).toBe(false);
    expect(
      viewingInputSchema.safeParse({
        expectedVersion: 0,
        humanConfirmed: false,
      }).success,
    ).toBe(false);
    expect(
      offerInputSchema.safeParse({
        expectedVersion: 0,
        amount: 0,
        currency: "SAR",
        humanConfirmed: true,
      }).success,
    ).toBe(false);
    expect(
      offerInputSchema.safeParse({
        expectedVersion: 0,
        amount: 1050000.5,
        currency: "SAR",
        humanConfirmed: true,
      }).success,
    ).toBe(true);
  });
});
