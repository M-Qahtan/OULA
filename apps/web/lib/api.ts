/** Explicit server contract boundary. No implicit fallbacks to fictional data. */
export type AuthenticatedOulaContext = Readonly<{
  accessToken: string;
  workspaceId: string;
  purpose: "PROPERTY_DECISION_SUPPORT" | "PROPERTY_MANAGEMENT" | "TRANSACTION_EXECUTION";
}>;

export type IntentCreateRequest = Readonly<{ intentType: "BUY" | "RENT"; budgetMax: number; minimumBedrooms: number; preferredDistricts: string[] }>;
export type IntentResponse = IntentCreateRequest & Readonly<{ id: string; workspaceId: string; status: "DRAFT" | "ACTIVE" | "PAUSED" | "FULFILLED" | "CANCELLED" }>;
export class OulaApiError extends Error {
  constructor(readonly status: number, message: string) { super(message); this.name = "OulaApiError"; }
}

/** Caller must obtain a valid OIDC user token in an approved authentication integration. */
export function createOulaApi(baseUrl: string, ctx: AuthenticatedOulaContext) {
  if (!/^https?:\/\//.test(baseUrl)) throw new Error("Explicit HTTP(S) backend URL required");
  if (!ctx.accessToken || !ctx.workspaceId) throw new Error("Backend session and workspace are required");
  async function request(path: string, init: RequestInit = {}): Promise<unknown> {
    const response = await fetch(new URL(path, baseUrl), {
      ...init,
      cache: "no-store",
      headers: {
        ...init.headers,
        Authorization: `Bearer ${ctx.accessToken}`,
        "X-OULA-Workspace-ID": ctx.workspaceId,
        "X-OULA-Purpose": ctx.purpose,
        Accept: "application/json",
      },
    });
    if (!response.ok) throw new OulaApiError(response.status, `OULA API request failed: ${response.status}`);
    return response.json() as Promise<unknown>;
  }
  return {
    createIntent: (input: IntentCreateRequest, idempotencyKey: string): Promise<IntentResponse> => {
      if (!idempotencyKey.trim()) throw new Error("Idempotency-Key required");
      return request("/v1/intents", { method: "POST", headers: { "Content-Type": "application/json", "Idempotency-Key": idempotencyKey }, body: JSON.stringify(input) }) as Promise<IntentResponse>;
    },
    getIntent: (intentId: string): Promise<IntentResponse> => request(`/v1/intents/${encodeURIComponent(intentId)}`) as Promise<IntentResponse>,
    /** The current OpenAPI v1.10.0 passport success schema requires review before DTO typing. */
    passport: (propertyId: string) => request(`/v1/properties/${encodeURIComponent(propertyId)}/passport`),
    transaction: (transactionId: string) => request(`/v1/transactions/${encodeURIComponent(transactionId)}`),
  };
}
