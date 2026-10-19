import { z } from "zod";
import type { ApiContract } from "./api-contracts";

const uuid = z.uuid();
const stamp = z.iso.datetime();
const version = z.number().int().nonnegative();
const owned = { id: uuid, workspaceId: uuid, propertyId: uuid };
export const enrollmentSchema = z.looseObject({
  ...owned,
  managerActorId: uuid,
  status: z.string(),
  activatedAt: stamp,
  closedAt: stamp.nullable(),
  version,
});
export const obligationInputSchema = z.strictObject({
  obligationType: z.string().trim().min(1).max(80),
  title: z.string().trim().min(1).max(255),
  dueAt: stamp,
  priority: z.enum(["LOW", "MEDIUM", "HIGH", "CRITICAL"]),
  sourceType: z.literal("USER_DECLARED"),
  sourceReference: z.string().trim().max(255).optional(),
});
export const obligationSchema = z.looseObject({
  ...owned,
  obligationType: z.string(),
  title: z.string(),
  dueAt: stamp,
  priority: z.string(),
  status: z.string(),
  sourceType: z.string(),
  sourceReference: z.string().nullable(),
  createdBy: uuid,
  createdAt: stamp,
  completedAt: stamp.nullable(),
  version,
});
export const signalSchema = z.looseObject({
  ...owned,
  obligationId: uuid.nullable(),
  signalType: z.string(),
  severity: z.string(),
  status: z.string(),
  message: z.string().nullable(),
  recommendedAction: z.string().nullable(),
  detectedAt: stamp,
  resolvedAt: stamp.nullable(),
  version,
});
export const actionSchema = z.looseObject({
  ...owned,
  obligationId: uuid.nullable(),
  guardianSignalId: uuid.nullable(),
  actionType: z.string(),
  title: z.string(),
  status: z.string(),
  dueAt: stamp.nullable(),
  assignedActorId: uuid.nullable(),
  resolutionNote: z.string().nullable(),
  createdAt: stamp,
  completedAt: stamp.nullable(),
  version,
});
export const managementSchema = z.looseObject({
  propertyId: uuid,
  enrollment: enrollmentSchema,
  obligations: z.array(obligationSchema),
  guardianSignals: z.array(signalSchema),
  actions: z.array(actionSchema),
});
export const assessmentSchema = z.looseObject({
  propertyId: uuid,
  assessedAt: stamp,
  obligationsEvaluated: version,
  signalsCreated: version,
  actionsCreated: version,
});
export const resolutionInputSchema = z.strictObject({
  resolutionNote: z.string().trim().min(1).max(1000),
});
export type PropertyCare = z.infer<typeof managementSchema>;
export type ObligationInput = z.infer<typeof obligationInputSchema>;
export type GuardianAssessment = z.infer<typeof assessmentSchema>;

/** Exact existing Java endpoints only. Public role selections never confer these scopes. */
export function resolveCareContract(
  method: string,
  path: string,
): ApiContract | null {
  const parts = path.split("/");
  if (parts[1] !== "v1" || !uuid.safeParse(parts[3]).success) return null;
  const id = parts[3];
  const base = {
    purpose: "PROPERTY_MANAGEMENT" as const,
    id,
    resourceField: "propertyId",
  };
  if (parts[2] === "properties") {
    if (method === "GET" && parts.length === 5 && parts[4] === "management")
      return {
        ...base,
        scope: "oula.property.management.read",
        response: managementSchema,
        nestedPropertyResources: true,
      };
    if (
      method === "POST" &&
      parts.length === 6 &&
      parts[4] === "management" &&
      parts[5] === "enroll"
    )
      return {
        ...base,
        scope: "oula.property.management.write",
        response: enrollmentSchema,
      };
    if (method === "POST" && parts.length === 5 && parts[4] === "obligations")
      return {
        ...base,
        scope: "oula.property.management.write",
        request: obligationInputSchema,
        response: obligationSchema,
      };
    if (
      method === "POST" &&
      parts.length === 6 &&
      parts[4] === "guardian" &&
      parts[5] === "assess"
    )
      return {
        ...base,
        scope: "oula.property.guardian.run",
        response: assessmentSchema,
      };
  }
  if (
    method === "POST" &&
    parts[2] === "property-actions" &&
    parts.length === 5 &&
    parts[4] === "complete"
  )
    return {
      ...base,
      resourceField: "id",
      scope: "oula.property.action.complete",
      request: resolutionInputSchema,
      response: actionSchema,
    };
  return null;
}

/** A valid shape is insufficient: every nested resource must belong to this task and workspace. */
export function careBelongsTo(
  overview: PropertyCare,
  propertyId: string,
  workspaceId: string,
): boolean {
  return (
    overview.propertyId === propertyId &&
    [
      overview.enrollment,
      ...overview.obligations,
      ...overview.guardianSignals,
      ...overview.actions,
    ].every(
      (item) =>
        item.workspaceId === workspaceId && item.propertyId === propertyId,
    )
  );
}
