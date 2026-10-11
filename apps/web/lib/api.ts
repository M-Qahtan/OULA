/** Explicit server contract boundary. No implicit fallbacks to fictional data. */
export type AuthenticatedOulaContext = Readonly<{
  accessToken: string;
  workspaceId: string;
  purpose: "PROPERTY_DECISION_SUPPORT" | "PROPERTY_MANAGEMENT" | "TRANSACTION_EXECUTION";
}>;

export type IntentCreateRequest = Readonly<{
  intentType: "BUY" | "RENT";
  budgetMax: number;
  minimumBedrooms: number;
  preferredDistricts: string[];
}>;

export type IntentResponse = IntentCreateRequest & Readonly<{
  id: string;
  workspaceId: string;
  status: "DRAFT" | "ACTIVE" | "PAUSED" | "FULFILLED" | "CANCELLED";
}>;

export type MatchItem = Readonly<{
  propertyId: string;
  rank: number;
  score: number;
  confidence: number;
  dimensions: Readonly<Record<string, number>>;
}>;

export type MatchRunResponse = Readonly<{
  matchRunId: string;
  matches: readonly MatchItem[];
}>;

export type PropertyPassportFact = Readonly<{
  id: string;
  key: string;
  /** JSON text returned as a string by the Java contract. Do not silently parse into authoritative truth. */
  valueJson: string;
  truthStatus: string;
  sourceType: string;
  confidence?: number | null;
  validFrom?: string | null;
  validTo?: string | null;
}>;

export type PropertyStateSnapshot = Readonly<{
  snapshotId: string;
  workspaceId: string;
  propertyId: string;
  version: number;
  effectiveAt: string;
  recordedAt: string;
  stateBasis: string;
  state: Readonly<Record<string, unknown>>;
  sourceType: string;
  sourceReference?: string | null;
  supersedesSnapshotId?: string | null;
}>;

export type PropertyPassport = Readonly<{
  propertyId: string;
  workspaceId: string;
  assetType?: string | null;
  district?: string | null;
  bedrooms?: number | null;
  askingPrice?: number | null;
  facts: readonly PropertyPassportFact[];
  latestState?: PropertyStateSnapshot | null;
  verifiedFactCoverage: number;
  generatedAt: string;
}>;

export class OulaApiError extends Error {
  constructor(readonly status: number, message: string) { super(message); this.name = "OulaApiError"; }
}

/** Caller must obtain a valid OIDC user token in an approved authentication integration. */
export function createOulaApi(baseUrl: string, ctx: AuthenticatedOulaContext) {
  if (!/^https?:\/\//.test(baseUrl)) throw new Error("Explicit HTTP(S) backend URL required");
  if (!ctx.accessToken || !ctx.workspaceId) throw new Error("Backend session and workspace are required");
  async function request<T = unknown>(path: string, init: RequestInit = {}): Promise<T> {
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
    return response.json() as Promise<T>;
  }
  return {
    createIntent: (input: IntentCreateRequest, idempotencyKey: string): Promise<IntentResponse> => {
      if (!idempotencyKey.trim()) throw new Error("Idempotency-Key required");
      return request<IntentResponse>("/v1/intents", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "Idempotency-Key": idempotencyKey,
        },
        body: JSON.stringify(input),
      });
    },
    getIntent: (intentId: string): Promise<IntentResponse> =>
      request<IntentResponse>(`/v1/intents/${encodeURIComponent(intentId)}`),
    runMatching: (intentId: string, idempotencyKey: string): Promise<MatchRunResponse> => {
      if (!idempotencyKey.trim()) throw new Error("Idempotency-Key required");
      return request<MatchRunResponse>(`/v1/intents/${encodeURIComponent(intentId)}/matches`, {
        method: "POST",
        headers: { "Idempotency-Key": idempotencyKey },
      });
    },
    getPropertyPassport: (propertyId: string): Promise<PropertyPassport> =>
      request<PropertyPassport>(`/v1/properties/${encodeURIComponent(propertyId)}/passport`),
    transaction: (transactionId: string) => request(`/v1/transactions/${encodeURIComponent(transactionId)}`),
  };
}
