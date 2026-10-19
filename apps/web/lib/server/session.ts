import { EncryptJWT, jwtDecrypt, type JWTPayload } from "jose";
import { z } from "zod";
import { purposes } from "../api-contracts";
import { getServerConfig } from "./config";

export const SESSION_COOKIE = "oula-session";
export const FLOW_COOKIE = "oula-oidc-flow";
export const sessionSchema = z
  .object({
    mode: z.enum(["OIDC", "DEV_TEST_ONLY"]).default("OIDC"),
    fixture: z.string().optional(),
    accessToken: z.string().min(1),
    subject: z.string().min(1),
    actorId: z.uuid(),
    workspaceIds: z.array(z.uuid()).min(1),
    workspaceId: z.uuid(),
    purposes: z.array(z.enum(purposes)).min(1),
    scopes: z.array(z.string()),
    exp: z.number(),
  })
  .refine((session) => session.workspaceIds.includes(session.workspaceId));
export type ServerSession = z.infer<typeof sessionSchema>;

export async function sealCookie(
  payload: JWTPayload,
  expiresAt: number,
): Promise<string> {
  const value = await new EncryptJWT(payload)
    .setProtectedHeader({ alg: "dir", enc: "A256GCM" })
    .setIssuedAt()
    .setExpirationTime(expiresAt)
    .encrypt(getServerConfig().key);
  if (value.length > 3800) throw new Error("Session exceeds cookie capacity");
  return value;
}
export async function openCookie(
  value: string | undefined,
): Promise<JWTPayload | null> {
  if (!value) return null;
  try {
    return (
      await jwtDecrypt(value, getServerConfig().key, {
        keyManagementAlgorithms: ["dir"],
        contentEncryptionAlgorithms: ["A256GCM"],
      })
    ).payload;
  } catch {
    return null;
  }
}
export async function readSession(
  value: string | undefined,
): Promise<ServerSession | null> {
  const payload = await openCookie(value);
  const session = sessionSchema.safeParse(payload);
  return session.success ? session.data : null;
}
export function cookieOptions(maxAge: number) {
  return {
    httpOnly: true,
    sameSite: "lax" as const,
    secure: getServerConfig().origin.protocol === "https:",
    path: "/",
    maxAge,
  };
}
export function sessionFromClaims(
  accessToken: string,
  claims: JWTPayload,
): ServerSession {
  const parsed = z
    .object({
      sub: z.string().min(1),
      actor_id: z.uuid(),
      oula_workspace_ids: z.array(z.uuid()).min(1),
      oula_purposes: z.array(z.string()),
      scope: z.string(),
      exp: z.number(),
    })
    .parse(claims);
  const now = Math.floor(Date.now() / 1000);
  if (parsed.exp <= now) throw new Error("Expired access token");
  const grantedPurposes = parsed.oula_purposes.filter(
    (purpose): purpose is ServerSession["purposes"][number] =>
      purposes.includes(purpose as ServerSession["purposes"][number]),
  );
  return sessionSchema.parse({
    accessToken,
    subject: parsed.sub,
    actorId: parsed.actor_id,
    workspaceIds: parsed.oula_workspace_ids,
    workspaceId: parsed.oula_workspace_ids[0],
    purposes: grantedPurposes,
    scopes: parsed.scope.split(/\s+/).filter(Boolean),
    exp: Math.min(parsed.exp, now + 3600),
  });
}
export function publicSession(session: ServerSession) {
  return {
    status: "authenticated" as const,
    mode: session.mode,
    subject: session.subject,
    workspaceId: session.workspaceId,
    workspaceIds: session.workspaceIds,
    purposes: session.purposes,
    scopes: session.scopes,
    expiresAt: session.exp,
  };
}
