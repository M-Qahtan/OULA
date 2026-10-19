import { z } from "zod";
import { resolveCareContract } from "./property-care-contracts";

const uuid = z.uuid();
const text = z.string();
const fraction = z.number().min(0).max(1);
const count = z.number().int().nonnegative();
export const purposes = [
  "PROPERTY_DECISION_SUPPORT",
  "PROPERTY_MANAGEMENT",
  "TRANSACTION_EXECUTION",
] as const;
export type Purpose = (typeof purposes)[number];

export const intentInputSchema = z.strictObject({
  intentType: z.enum(["BUY", "RENT"]),
  budgetMax: z.number().positive(),
  minimumBedrooms: count,
  preferredDistricts: z
    .array(text.trim().min(1).max(100))
    .max(12)
    .refine((values) => new Set(values).size === values.length),
});
export const intentSchema = z.looseObject({
  ...intentInputSchema.shape,
  id: uuid,
  workspaceId: uuid,
  status: z.enum(["DRAFT", "ACTIVE", "PAUSED", "FULFILLED", "CANCELLED"]),
});
export const matchesSchema = z.looseObject({
  matchRunId: uuid,
  matches: z.array(
    z.looseObject({
      propertyId: uuid,
      rank: count.min(1),
      score: z.number().min(0).max(100),
      confidence: fraction,
      dimensions: z.record(text, z.number()),
    }),
  ),
});
const snapshotSchema = z.looseObject({
  snapshotId: uuid,
  workspaceId: uuid,
  propertyId: uuid,
  version: count,
  effectiveAt: text,
  recordedAt: text,
  stateBasis: text,
  state: z.record(text, z.unknown()),
  sourceType: text,
});
export const passportSchema = z.looseObject({
  propertyId: uuid,
  workspaceId: uuid,
  assetType: text.nullish(),
  district: text.nullish(),
  bedrooms: z.number().int().nullish(),
  askingPrice: z.number().nullish(),
  facts: z.array(
    z.looseObject({
      id: uuid,
      key: text,
      valueJson: text,
      truthStatus: text,
      sourceType: text,
      confidence: z.number().nullish(),
      validFrom: text.nullish(),
      validTo: text.nullish(),
    }),
  ),
  latestState: snapshotSchema.nullish(),
  verifiedFactCoverage: fraction,
  generatedAt: text,
});
export const transactionSchema = z.looseObject({
  id: uuid,
  workspaceId: uuid,
  stage: text,
  version: count,
});
export const consentInputSchema = z.strictObject({
  consentCode: z.enum([
    "LIFE_CONTEXT_PROCESSING",
    "PERSONALIZATION_MEMORY",
    "LOCATION_CONTEXT",
    "FINANCIAL_CONTEXT",
  ]),
  decision: z.enum(["GRANTED", "REVOKED"]),
  reason: text.trim().max(500).optional(),
});
export const consentSchema = z.looseObject({
  consentCode: text,
  decision: z.enum(["GRANTED", "REVOKED"]),
  reason: text.nullish(),
  source: text,
  occurredAt: text,
});
export const createTransactionSchema = z.strictObject({
  intentId: uuid,
  propertyId: uuid,
});
export const viewingInputSchema = z.strictObject({
  expectedVersion: count,
  preferredAt: z.iso.datetime().nullish(),
  note: text.trim().max(500).nullish(),
  humanConfirmed: z.literal(true),
});
export const offerInputSchema = z.strictObject({
  expectedVersion: count,
  amount: z.number().positive().max(1e12),
  currency: text.regex(/^[A-Z]{3}$/),
  termsSummary: text.trim().max(1000).nullish(),
  humanConfirmed: z.literal(true),
});
export const viewingSchema = z.looseObject({
  id: uuid,
  transactionId: uuid,
  preferredAt: text.nullish(),
  note: text.nullish(),
  status: z.enum(["REQUESTED", "CANCELLED", "COMPLETED"]),
  requestedBy: uuid,
  createdAt: text,
});
export const offerSchema = z.looseObject({
  id: uuid,
  transactionId: uuid,
  amount: z.number(),
  currency: text,
  termsSummary: text.nullish(),
  status: z.enum(["SUBMITTED", "WITHDRAWN", "ACCEPTED", "REJECTED"]),
  submittedBy: uuid,
  createdAt: text,
});
export const viewingCommandSchema = z.looseObject({
  transaction: transactionSchema,
  viewing: viewingSchema,
});
export const offerCommandSchema = z.looseObject({
  transaction: transactionSchema,
  offer: offerSchema,
});
export const dealRoomSchema = z.looseObject({
  transaction: transactionSchema,
  viewings: z.array(viewingSchema),
  offers: z.array(offerSchema),
  availableCommands: z.array(
    z.enum(["QUALIFY", "REQUEST_VIEWING", "SUBMIT_OFFER"]),
  ),
  humanConfirmationRequired: z.literal(true),
});
export const propertyInputSchema = z.strictObject({
  assetType: z.enum(["RESIDENTIAL", "COMMERCIAL"]),
  district: text.trim().min(1).max(160),
  bedrooms: count.max(100),
  askingPrice: z.number().min(0.01),
  demo: z.boolean(),
});
export const declaredPropertySchema = z.looseObject({
  id: uuid,
  workspaceId: uuid,
  assetType: z.enum(["RESIDENTIAL", "COMMERCIAL"]),
  district: text,
  bedrooms: count,
  askingPrice: z.number(),
  dataOrigin: z.enum(["DEMO", "USER_DECLARED", "UNKNOWN"]),
  sourceType: text,
  truthStatus: z.enum(["DECLARED", "UNKNOWN"]),
});
export const listingInputSchema = z.strictObject({
  transactionType: z.enum(["SALE", "RENT"]),
  askingPrice: z.number().min(0.01),
});
export const draftListingSchema = z.looseObject({
  id: uuid,
  workspaceId: uuid,
  propertyId: uuid,
  transactionType: z.enum(["SALE", "RENT"]),
  status: z.literal("DRAFT"),
  askingPrice: z.number(),
  currency: z.literal("SAR"),
  dataOrigin: z.enum(["DEMO", "USER_DECLARED", "UNKNOWN"]),
  sourceType: text,
  truthStatus: z.enum(["DECLARED", "UNKNOWN"]),
});
export const recommendationInputSchema = z.strictObject({
  matchRunId: uuid,
  evidenceIds: z.array(uuid).min(1).max(25),
  assumptionIds: z.array(uuid).max(25).optional(),
  validHours: count.min(1).max(720),
});
export const recommendationSchema = z.looseObject({
  recommendationId: uuid,
  matchRunId: uuid,
  recommendedPropertyId: uuid,
  lifeFitScore: z.number().min(0).max(100),
  confidence: fraction,
  modelId: text,
  modelVersion: text,
  evidenceIds: z.array(uuid),
  assumptionIds: z.array(uuid),
});
export const explanationSchema = z.looseObject({
  recommendationId: uuid,
  executiveSummary: text,
  recommendedPropertyId: uuid,
  lifeFitScore: z.number(),
  confidence: z.number(),
  confidenceBreakdown: z.record(text, z.unknown()),
  uncertainty: z.record(text, z.unknown()),
  modelId: text,
  modelVersion: text,
  modelType: text,
  riskClass: text,
  evidenceIds: z.array(uuid),
  assumptionIds: z.array(uuid),
  alternatives: z.array(
    z.looseObject({
      propertyId: uuid,
      rank: count.min(1),
      lifeFitScore: z.number(),
      confidence: z.number(),
      dimensions: z.record(text, z.unknown()),
    }),
  ),
});
export const decisionInputSchema = z.strictObject({
  selectedPropertyId: uuid,
  overrideReason: text.optional(),
});
export const decisionSchema = z.looseObject({
  decisionId: uuid,
  recommendationId: uuid,
  selectedPropertyId: uuid,
  acceptedRecommendation: z.boolean(),
});
export type Recommendation = z.infer<typeof recommendationSchema>;
export type RecommendationExplanation = z.infer<typeof explanationSchema>;
export type HumanDecision = z.infer<typeof decisionSchema>;
export const transitionSchema = z.strictObject({
  expectedVersion: count,
  target: z.enum([
    "DRAFT",
    "QUALIFIED",
    "NEGOTIATING",
    "DUE_DILIGENCE",
    "CONTRACTING",
    "CLOSING",
    "HANDOVER",
    "COMPLETED",
    "CANCELLED",
  ]),
  reason: text.trim().min(1).max(500),
});
const vitalStatus = z.enum(["GREEN", "AMBER", "RED", "UNKNOWN"]);
export const vitalsSchema = z.looseObject({
  id: uuid,
  workspaceId: uuid,
  propertyId: uuid,
  policyKey: text,
  policyVersion: text,
  overallStatus: vitalStatus,
  knownDimensionCount: count.max(8),
  assessedAt: text,
  obligationStatus: vitalStatus.optional(),
  guardianStatus: vitalStatus.optional(),
  executionStatus: vitalStatus.optional(),
  costStatus: vitalStatus.optional(),
  evidenceStatus: vitalStatus.optional(),
  truthStatus: vitalStatus.optional(),
  freshnessStatus: vitalStatus.optional(),
  providerStatus: vitalStatus.optional(),
  openObligations: count.optional(),
  overdueObligations: count.optional(),
  openGuardianSignals: count.optional(),
  openWorkOrders: count.optional(),
  verifiedFactCoverage: fraction.nullish(),
});
export type PropertyVitals = z.infer<typeof vitalsSchema>;
export const advisorySchema = z.looseObject({
  workspaceId: uuid,
  propertyId: uuid,
  sourceSnapshotId: uuid,
  snapshotPolicyKey: text,
  snapshotPolicyVersion: text,
  advisoryRulesVersion: text,
  assessmentState: z.enum(["ASSESSABLE", "LIMITED_EVIDENCE", "STALE"]),
  observedTrend: text,
  sourceAssessedAt: text,
  generatedAt: text,
  limitations: z.array(text),
  recommendations: z.array(
    z.looseObject({
      dimension: text,
      observedStatus: vitalStatus,
      trendDirection: text,
      priority: z.enum(["HIGH", "MEDIUM", "DATA_QUALITY"]),
      actionCode: text,
      explanation: text,
      executionGate: z.literal("HUMAN_REVIEW_REQUIRED"),
      observedEvidence: z.array(z.looseObject({ name: text, value: text })),
    }),
  ),
});
export type OperationalAdvice = z.infer<typeof advisorySchema>;

export type ApiContract = Readonly<{
  purpose: Purpose;
  alternatePurpose?: Purpose;
  scope: string;
  response: z.ZodType;
  request?: z.ZodType;
  id?: string;
  resourceField?: string;
  nestedPropertyResources?: boolean;
}>;
/** Finite allowlist, not a general-purpose authenticated proxy. Missing routes stay missing. */
export function resolveApiContract(
  method: string,
  path: string,
): ApiContract | null {
  const care = resolveCareContract(method, path);
  if (care) return care;
  if (method === "POST" && path === "/v1/properties")
    return {
      purpose: purposes[1],
      alternatePurpose: purposes[0],
      scope: "oula.property.write",
      request: propertyInputSchema,
      response: declaredPropertySchema,
    };
  if (method === "GET" && path === "/v1/consents")
    return {
      purpose: purposes[0],
      alternatePurpose: purposes[1],
      scope: "oula.consent.read",
      response: z.array(consentSchema),
    };
  if (method === "POST" && path === "/v1/consents/decisions")
    return {
      purpose: purposes[0],
      alternatePurpose: purposes[1],
      scope: "oula.consent.write",
      request: consentInputSchema,
      response: consentSchema,
    };
  if (method === "POST" && path === "/v1/transactions")
    return {
      purpose: purposes[2],
      scope: "oula.transaction.create",
      request: createTransactionSchema,
      response: transactionSchema,
    };
  if (method === "POST" && path === "/v1/intents")
    return {
      purpose: purposes[0],
      scope: "oula.intent.write",
      request: intentInputSchema,
      response: intentSchema,
    };
  if (method === "POST" && path === "/v1/intelligence/recommendations")
    return {
      purpose: purposes[0],
      scope: "oula.intelligence.recommend",
      request: recommendationInputSchema,
      response: recommendationSchema,
    };
  const segments = path.split("/").filter(Boolean);
  if (
    segments[0] === "v1" &&
    segments[1] === "intelligence" &&
    segments[2] === "recommendations" &&
    segments.length === 5 &&
    uuid.safeParse(segments[3]).success
  ) {
    if (method === "GET" && segments[4] === "explain")
      return {
        purpose: purposes[0],
        scope: "oula.intelligence.read",
        response: explanationSchema,
        id: segments[3],
        resourceField: "recommendationId",
      };
    if (method === "POST" && segments[4] === "decisions")
      return {
        purpose: purposes[0],
        scope: "oula.intelligence.decide",
        request: decisionInputSchema,
        response: decisionSchema,
        id: segments[3],
        resourceField: "recommendationId",
      };
  }
  const id = segments[2];
  if (!id || !uuid.safeParse(id).success || segments[0] !== "v1") return null;
  if (segments[1] === "intents" && segments.length === 3 && method === "GET")
    return {
      purpose: purposes[0],
      scope: "oula.intent.read",
      response: intentSchema,
      id,
      resourceField: "id",
    };
  if (
    segments[1] === "intents" &&
    segments.length === 4 &&
    segments[3] === "matches" &&
    method === "POST"
  )
    return {
      purpose: purposes[0],
      scope: "oula.matching.run",
      response: matchesSchema,
    };
  if (segments[1] === "properties" && method === "GET") {
    if (segments.length === 4 && segments[3] === "passport")
      return {
        purpose: purposes[0],
        alternatePurpose: purposes[1],
        scope: "oula.property.passport.read",
        response: passportSchema,
        id,
        resourceField: "propertyId",
      };
    if (
      segments.length === 5 &&
      segments[3] === "vitals" &&
      segments[4] === "latest"
    )
      return {
        purpose: purposes[1],
        scope: "oula.property.vitals.read",
        response: vitalsSchema,
        id,
        resourceField: "propertyId",
      };
    if (segments.length === 4 && segments[3] === "operational-advice")
      return {
        purpose: purposes[1],
        scope: "oula.property.advisory.read",
        response: advisorySchema,
        id,
        resourceField: "propertyId",
      };
  }
  if (segments[1] === "properties" && segments.length === 3 && method === "GET")
    return {
      purpose: purposes[1],
      alternatePurpose: purposes[0],
      scope: "oula.property.read",
      response: declaredPropertySchema,
      id,
      resourceField: "id",
    };
  if (
    segments[1] === "properties" &&
    segments.length === 4 &&
    segments[3] === "listings" &&
    method === "POST"
  )
    return {
      purpose: purposes[1],
      alternatePurpose: purposes[0],
      scope: "oula.listing.write",
      request: listingInputSchema,
      response: draftListingSchema,
      id,
      resourceField: "propertyId",
    };
  if (segments[1] === "listings" && segments.length === 3 && method === "GET")
    return {
      purpose: purposes[1],
      alternatePurpose: purposes[0],
      scope: "oula.listing.read",
      response: draftListingSchema,
      id,
      resourceField: "id",
    };
  if (segments[1] === "transactions") {
    if (
      segments.length === 4 &&
      segments[3] === "deal-room" &&
      method === "GET"
    )
      return {
        purpose: purposes[2],
        scope: "oula.transaction.read",
        response: dealRoomSchema,
        id,
      };
    if (
      segments.length === 4 &&
      segments[3] === "viewings" &&
      method === "POST"
    )
      return {
        purpose: purposes[2],
        scope: "oula.transaction.viewing.request",
        request: viewingInputSchema,
        response: viewingCommandSchema,
        id,
      };
    if (segments.length === 4 && segments[3] === "offers" && method === "POST")
      return {
        purpose: purposes[2],
        scope: "oula.transaction.offer.submit",
        request: offerInputSchema,
        response: offerCommandSchema,
        id,
      };
    if (segments.length === 3 && method === "GET")
      return {
        purpose: purposes[2],
        scope: "oula.transaction.read",
        response: transactionSchema,
        id,
        resourceField: "id",
      };
    if (
      segments.length === 4 &&
      segments[3] === "transitions" &&
      method === "POST"
    )
      return {
        purpose: purposes[2],
        scope: "oula.transaction.advance",
        request: transitionSchema,
        response: transactionSchema,
        id,
        resourceField: "id",
      };
  }
  return null;
}
