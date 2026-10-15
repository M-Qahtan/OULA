import type { AuthenticatedOulaContext } from "./api";

export type ManagementOverview = Readonly<{
  propertyId: string;
  enrollment: Readonly<{ status: string }>;
  obligations: readonly Readonly<{ id: string; title: string; status: string }>[];
  guardianSignals: readonly Readonly<{ id: string }>[];
  actions: readonly Readonly<{ id: string; status: string }>[];
}>;
export type GuardianResult = Readonly<{
  propertyId: string;
  obligationsEvaluated: number;
  signalsCreated: number;
  actionsCreated: number;
}>;
export type ObligationInput = Readonly<{
  obligationType: string;
  title: string;
  dueAt: string;
  priority: "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";
  sourceType: string;
  sourceReference: string;
}>;

/** Canonical Java boundary; refuses missing credentials and never falls back to mocks. */
export function createGuardianClient(baseUrl: string, ctx: AuthenticatedOulaContext) {
  const origin = new URL(baseUrl);
  if (!["http:", "https:"].includes(origin.protocol) || origin.username || origin.password ||
      origin.search || origin.hash || origin.pathname !== "/") {
    throw new Error("Explicit clean API origin required");
  }
  if (ctx.purpose !== "PROPERTY_MANAGEMENT" || !ctx.accessToken || !ctx.workspaceId) {
    throw new Error("Authenticated PROPERTY_MANAGEMENT workspace required");
  }
  const resource = (propertyId: string) => {
    if (!/^[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}$/i.test(propertyId)) {
      throw new Error("Property UUID required");
    }
    return "/v1/properties/" + encodeURIComponent(propertyId);
  };
  const mutate = (key: string, body?: object): RequestInit => {
    if (!key.trim()) throw new Error("Idempotency-Key required");
    return { method: "POST", headers: {
      "Idempotency-Key": key,
      ...(body ? { "Content-Type": "application/json" } : {}),
    }, ...(body ? { body: JSON.stringify(body) } : {}) };
  };
  async function call<T>(path: string, init: RequestInit = {}): Promise<T> {
    const response = await fetch(new URL(path, origin), {
      ...init,
      cache: "no-store",
      headers: {
        ...init.headers,
        Authorization: "Bearer " + ctx.accessToken,
        "X-OULA-Workspace-ID": ctx.workspaceId,
        "X-OULA-Purpose": ctx.purpose,
        Accept: "application/json",
      },
    });
    if (!response.ok) throw new Error("OULA API returned " + response.status);
    return response.json() as Promise<T>;
  }
  return {
    enroll: (id: string, key: string) =>
      call<{ status: string }>(resource(id) + "/management/enroll", mutate(key)),
    createObligation: (id: string, body: ObligationInput, key: string) =>
      call<{ id: string; status: string }>(resource(id) + "/obligations", mutate(key, body)),
    assess: (id: string, key: string) =>
      call<GuardianResult>(resource(id) + "/guardian/assess", mutate(key)),
    overview: (id: string) =>
      call<ManagementOverview>(resource(id) + "/management"),
  };
}
