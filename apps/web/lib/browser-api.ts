import type {
  DeclaredProperty,
  DraftListing,
  PropertyInput,
} from "./property-intake-client";
import {
  OulaApiError,
  type DealRoomResponse,
  type TransactionCreateRequest,
  type TransactionViewingRequest,
  type TransactionViewingCommandResponse,
  type TransactionOfferRequest,
  type TransactionOfferCommandResponse,
  type ConsentResponse,
  type ConsentDecisionRequest,
  type IntentCreateRequest,
  type IntentResponse,
  type MatchRunResponse,
  type PropertyPassport,
  type TransactionResponse,
  type TransactionTransitionRequest,
} from "./api";
import {
  declaredPropertySchema,
  draftListingSchema,
  dealRoomSchema,
  viewingCommandSchema,
  offerCommandSchema,
  consentSchema,
  intentSchema,
  matchesSchema,
  passportSchema,
  transactionSchema,
  vitalsSchema,
  advisorySchema,
  recommendationSchema,
  explanationSchema,
  decisionSchema,
  type PropertyVitals,
  type OperationalAdvice,
  type Recommendation,
  type RecommendationExplanation,
  type HumanDecision,
} from "./api-contracts";
import type { z } from "zod";
import {
  enrollmentSchema,
  managementSchema,
  obligationSchema,
  assessmentSchema,
  actionSchema,
  type PropertyCare,
  type ObligationInput,
  type GuardianAssessment,
} from "./property-care-contracts";

/** Cookie-authenticated same-origin transport. Bearer tokens never enter client state. */
export function createBrowserOulaApi(signal?: AbortSignal) {
  async function request<T>(
    path: string,
    schema: z.ZodType,
    init: RequestInit = {},
  ): Promise<T> {
    const response = await fetch("/api/oula" + path, {
      signal,
      ...init,
      credentials: "same-origin",
      cache: "no-store",
    });
    const data: unknown = await response.json().catch(() => null);
    if (!response.ok) {
      const code =
        data &&
        typeof data === "object" &&
        "code" in data &&
        typeof data.code === "string"
          ? data.code
          : "BACKEND_UNAVAILABLE";
      throw new OulaApiError(response.status, code);
    }
    const parsed = schema.safeParse(data);
    if (!parsed.success) throw new OulaApiError(502, "CONTRACT_MISMATCH");
    return parsed.data as T;
  }
  const idempotent = (key: string) => {
    if (!key.trim()) throw new Error("Idempotency-Key required");
    return { "Idempotency-Key": key, "Content-Type": "application/json" };
  };
  return {
    getPropertyCare: (id: string) =>
      request<PropertyCare>(
        `/v1/properties/${encodeURIComponent(id)}/management`,
        managementSchema,
      ),
    enrollPropertyCare: (id: string, key: string) =>
      request(
        `/v1/properties/${encodeURIComponent(id)}/management/enroll`,
        enrollmentSchema,
        { method: "POST", headers: idempotent(key) },
      ),
    createObligation: (id: string, input: ObligationInput, key: string) =>
      request(
        `/v1/properties/${encodeURIComponent(id)}/obligations`,
        obligationSchema,
        {
          method: "POST",
          headers: idempotent(key),
          body: JSON.stringify(input),
        },
      ),
    assessGuardian: (id: string, key: string) =>
      request<GuardianAssessment>(
        `/v1/properties/${encodeURIComponent(id)}/guardian/assess`,
        assessmentSchema,
        { method: "POST", headers: idempotent(key) },
      ),
    completePropertyAction: (id: string, resolutionNote: string, key: string) =>
      request(
        `/v1/property-actions/${encodeURIComponent(id)}/complete`,
        actionSchema,
        {
          method: "POST",
          headers: idempotent(key),
          body: JSON.stringify({ resolutionNote }),
        },
      ),
    createProperty: (input: PropertyInput, key: string) =>
      request<DeclaredProperty>("/v1/properties", declaredPropertySchema, {
        method: "POST",
        headers: idempotent(key),
        body: JSON.stringify(input),
      }),
    getProperty: (id: string) =>
      request<DeclaredProperty>(
        `/v1/properties/${encodeURIComponent(id)}`,
        declaredPropertySchema,
      ),
    createListing: (
      id: string,
      transactionType: "SALE" | "RENT",
      askingPrice: number,
      key: string,
    ) =>
      request<DraftListing>(
        `/v1/properties/${encodeURIComponent(id)}/listings`,
        draftListingSchema,
        {
          method: "POST",
          headers: idempotent(key),
          body: JSON.stringify({ transactionType, askingPrice }),
        },
      ),
    getListing: (id: string) =>
      request<DraftListing>(
        `/v1/listings/${encodeURIComponent(id)}`,
        draftListingSchema,
      ),
    decideConsent: (input: ConsentDecisionRequest, key: string) =>
      request<ConsentResponse>("/v1/consents/decisions", consentSchema, {
        method: "POST",
        headers: idempotent(key),
        body: JSON.stringify(input),
      }),
    createTransaction: (input: TransactionCreateRequest, key: string) =>
      request<TransactionResponse>("/v1/transactions", transactionSchema, {
        method: "POST",
        headers: idempotent(key),
        body: JSON.stringify(input),
      }),
    getDealRoom: (id: string) =>
      request<DealRoomResponse>(
        `/v1/transactions/${encodeURIComponent(id)}/deal-room`,
        dealRoomSchema,
      ),
    requestViewing: (
      id: string,
      input: TransactionViewingRequest,
      key: string,
    ) =>
      request<TransactionViewingCommandResponse>(
        `/v1/transactions/${encodeURIComponent(id)}/viewings`,
        viewingCommandSchema,
        {
          method: "POST",
          headers: idempotent(key),
          body: JSON.stringify(input),
        },
      ),
    submitOffer: (id: string, input: TransactionOfferRequest, key: string) =>
      request<TransactionOfferCommandResponse>(
        `/v1/transactions/${encodeURIComponent(id)}/offers`,
        offerCommandSchema,
        {
          method: "POST",
          headers: idempotent(key),
          body: JSON.stringify(input),
        },
      ),
    createIntent: (input: IntentCreateRequest, key: string) =>
      request<IntentResponse>("/v1/intents", intentSchema, {
        method: "POST",
        headers: idempotent(key),
        body: JSON.stringify(input),
      }),
    getIntent: (id: string) =>
      request<IntentResponse>(
        `/v1/intents/${encodeURIComponent(id)}`,
        intentSchema,
      ),
    runMatching: (id: string, key: string) =>
      request<MatchRunResponse>(
        `/v1/intents/${encodeURIComponent(id)}/matches`,
        matchesSchema,
        { method: "POST", headers: idempotent(key) },
      ),
    getPropertyPassport: (id: string) =>
      request<PropertyPassport>(
        `/v1/properties/${encodeURIComponent(id)}/passport`,
        passportSchema,
      ),
    getTransaction: (id: string) =>
      request<TransactionResponse>(
        `/v1/transactions/${encodeURIComponent(id)}`,
        transactionSchema,
      ),
    advanceTransaction: (
      id: string,
      input: TransactionTransitionRequest,
      key: string,
    ) =>
      request<TransactionResponse>(
        `/v1/transactions/${encodeURIComponent(id)}/transitions`,
        transactionSchema,
        {
          method: "POST",
          headers: idempotent(key),
          body: JSON.stringify(input),
        },
      ),
    getPropertyVitals: (id: string) =>
      request<PropertyVitals>(
        `/v1/properties/${encodeURIComponent(id)}/vitals/latest`,
        vitalsSchema,
      ),
    getOperationalAdvice: (id: string) =>
      request<OperationalAdvice>(
        `/v1/properties/${encodeURIComponent(id)}/operational-advice`,
        advisorySchema,
      ),
    generateRecommendation: (
      matchRunId: string,
      evidenceIds: string[],
      key: string,
    ) =>
      request<Recommendation>(
        "/v1/intelligence/recommendations",
        recommendationSchema,
        {
          method: "POST",
          headers: idempotent(key),
          body: JSON.stringify({ matchRunId, evidenceIds, validHours: 24 }),
        },
      ),
    explainRecommendation: (id: string) =>
      request<RecommendationExplanation>(
        `/v1/intelligence/recommendations/${encodeURIComponent(id)}/explain`,
        explanationSchema,
      ),
    recordDecision: (id: string, propertyId: string, key: string) =>
      request<HumanDecision>(
        `/v1/intelligence/recommendations/${encodeURIComponent(id)}/decisions`,
        decisionSchema,
        {
          method: "POST",
          headers: idempotent(key),
          body: JSON.stringify({ selectedPropertyId: propertyId }),
        },
      ),
  };
}
