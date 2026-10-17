import type { AuthenticatedOulaContext } from "./api";
import { OulaApiError } from "./api";

/** Read-only UX discovery. Every subsequent backend command rechecks authorization. */
export type ExperienceCapability = Readonly<{
  code: "INTENT_CREATE" | "MATCH_RUN" | "PROPERTY_PASSPORT" | "MANAGEMENT_OVERVIEW" |
    "PROPERTY_INTAKE" | "PRIVATE_LISTING_DRAFT" | "PRIVATE_LISTING_READ" |
    "DEAL_READ" | "DEAL_ROOM_READ" | "DEAL_ADVANCE";
  route: string;
  scope: string;
  mode: "QUERY" | "COMMAND";
}>;
export type ExperienceManifest = Readonly<{
  workspaceId: string;
  purpose: AuthenticatedOulaContext["purpose"];
  lens: "PULSE" | "HABITAT" | "NEXUS";
  capabilities: readonly ExperienceCapability[];
  source: "AUTHENTICATED_SCOPE_PROJECTION";
}>;

export async function fetchOulaExperienceManifest(
  baseUrl: string,
  ctx: AuthenticatedOulaContext,
  fetcher: typeof fetch = fetch,
): Promise<ExperienceManifest> {
  if (!/^https?:\/\//.test(baseUrl)) throw new Error("Explicit HTTP(S) backend required");
  if (!ctx.accessToken || !ctx.workspaceId) throw new Error("Authenticated workspace required");
  const response = await fetcher(new URL("/v1/experience/manifest", baseUrl), {
    method: "GET",
    cache: "no-store",
    headers: {
      Authorization: `Bearer ${ctx.accessToken}`,
      "X-OULA-Workspace-ID": ctx.workspaceId,
      "X-OULA-Purpose": ctx.purpose,
      Accept: "application/json",
    },
  });
  if (!response.ok) throw new OulaApiError(response.status, `Experience manifest: ${response.status}`);
  const manifest: ExperienceManifest = await response.json();
  if (manifest.workspaceId !== ctx.workspaceId ||
      manifest.purpose !== ctx.purpose ||
      manifest.source !== "AUTHENTICATED_SCOPE_PROJECTION") {
    throw new Error("Experience manifest context mismatch");
  }
  return manifest;
}
