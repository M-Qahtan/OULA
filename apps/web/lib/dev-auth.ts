import type { AuthenticatedOulaContext } from "./api";

export type DevAuthSession = Readonly<{
  accessToken: string;
  tokenType: "Bearer";
  expiresAt: string;
  mode: "DEV_TEST_ONLY";
  fixture: string;
  actorId: string;
  workspaceId: string;
  purposes: readonly string[];
  scopes: readonly string[];
}>;

export type PreparedGoldenPathFixture = Readonly<{
  mode: "DEV_TEST_ONLY";
  intentId: string;
  propertyIds: readonly string[];
  signalSource: "DEV_FIXTURE_SIMULATION";
  verifiedExternalData: false;
}>;

function requireBaseUrl(baseUrl: string): URL {
  if (!/^https?:\/\//.test(baseUrl)) {
    throw new Error("Explicit HTTP(S) backend URL required for dev auth");
  }
  return new URL(baseUrl);
}

export async function acquireDevAuthSession(
  baseUrl: string,
  fixture = "buyer",
): Promise<DevAuthSession> {
  const root = requireBaseUrl(baseUrl);
  const response = await fetch(new URL("/dev-auth/token", root), {
    method: "POST",
    cache: "no-store",
    headers: { "Content-Type": "application/json", Accept: "application/json" },
    body: JSON.stringify({ fixture }),
  });
  if (!response.ok) {
    throw new Error(`DEV_AUTH token request failed: ${response.status}`);
  }
  const session = await response.json() as DevAuthSession;
  if (session.mode !== "DEV_TEST_ONLY" || session.tokenType !== "Bearer") {
    throw new Error("Unexpected dev auth session contract");
  }
  if (!session.accessToken || !session.workspaceId) {
    throw new Error("Incomplete dev auth session");
  }
  return session;
}

export function devContext(
  session: DevAuthSession,
  purpose: AuthenticatedOulaContext["purpose"],
): AuthenticatedOulaContext {
  if (!session.purposes.includes(purpose)) {
    throw new Error(`Fixture is not authorized for purpose: ${purpose}`);
  }
  return {
    accessToken: session.accessToken,
    workspaceId: session.workspaceId,
    purpose,
  };
}

export async function prepareDevGoldenPath(
  baseUrl: string,
  session: DevAuthSession,
  intentId: string,
): Promise<PreparedGoldenPathFixture> {
  const root = requireBaseUrl(baseUrl);
  const response = await fetch(
    new URL(`/dev-auth/golden-path/intents/${encodeURIComponent(intentId)}/prepare`, root),
    {
      method: "POST",
      cache: "no-store",
      headers: {
        Authorization: `Bearer ${session.accessToken}`,
        "X-OULA-Workspace-ID": session.workspaceId,
        "X-OULA-Purpose": "PROPERTY_DECISION_SUPPORT",
        Accept: "application/json",
      },
    },
  );
  if (!response.ok) {
    throw new Error(`DEV_AUTH fixture preparation failed: ${response.status}`);
  }
  const prepared = await response.json() as PreparedGoldenPathFixture;
  if (prepared.mode !== "DEV_TEST_ONLY" || prepared.verifiedExternalData !== false) {
    throw new Error("Unexpected dev fixture provenance contract");
  }
  return prepared;
}
