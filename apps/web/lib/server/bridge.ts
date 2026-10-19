import { resolveApiContract } from "../api-contracts";
import { careBelongsTo, managementSchema } from "../property-care-contracts";
import { getServerConfig, localDevAuthEnabled } from "./config";
import type { ServerSession } from "./session";

const errorResponse = (status: number, code: string) =>
  Response.json({ code }, { status, headers: { "Cache-Control": "no-store" } });
export function sameOrigin(request: Request): boolean {
  return request.headers.get("Origin") === getServerConfig().origin.origin;
}
export async function bridgeRequest(
  request: Request,
  path: string,
  session: ServerSession | null,
): Promise<Response> {
  if (!session || session.exp <= Date.now() / 1000)
    return errorResponse(401, "AUTH_REQUIRED");
  if (session.mode === "DEV_TEST_ONLY" && !localDevAuthEnabled())
    return errorResponse(401, "AUTH_REQUIRED");
  const contract = resolveApiContract(request.method, path);
  if (!contract || new URL(request.url).search)
    return errorResponse(404, "UNSUPPORTED_OPERATION");
  const purpose = session.purposes.includes(contract.purpose)
    ? contract.purpose
    : contract.alternatePurpose;
  if (
    !purpose ||
    !session.purposes.includes(purpose) ||
    !session.scopes.includes(contract.scope) ||
    !session.workspaceIds.includes(session.workspaceId)
  )
    return errorResponse(403, "PERMISSION_DENIED");
  const headers = new Headers({
    Accept: "application/json",
    Authorization: `Bearer ${session.accessToken}`,
    "X-OULA-Workspace-ID": session.workspaceId,
    "X-OULA-Purpose": purpose,
  });
  let body: string | undefined;
  if (request.method === "POST") {
    if (!sameOrigin(request)) return errorResponse(403, "ORIGIN_DENIED");
    const key = request.headers.get("Idempotency-Key");
    if (!key || !/^[A-Za-z0-9._:-]{1,160}$/.test(key))
      return errorResponse(400, "IDEMPOTENCY_KEY_REQUIRED");
    headers.set("Idempotency-Key", key);
    if (contract.request) {
      if (!request.headers.get("Content-Type")?.startsWith("application/json"))
        return errorResponse(415, "JSON_REQUIRED");
      try {
        const raw = await request.text();
        if (raw.length > 16384) return errorResponse(413, "REQUEST_TOO_LARGE");
        const parsed = contract.request.safeParse(JSON.parse(raw));
        if (!parsed.success) return errorResponse(400, "VALIDATION_FAILED");
        const data = parsed.data as Record<string, unknown>;
        if (session.mode === "DEV_TEST_ONLY" && path === "/v1/properties")
          data.demo = true;
        body = JSON.stringify(data);
        headers.set("Content-Type", "application/json");
      } catch {
        return errorResponse(400, "VALIDATION_FAILED");
      }
    } else if ((await request.text()).trim())
      return errorResponse(400, "UNEXPECTED_BODY");
  }
  try {
    const upstream = await fetch(new URL(path, getServerConfig().backend), {
      method: request.method,
      headers,
      body,
      cache: "no-store",
      redirect: "error",
      signal: AbortSignal.timeout(12000),
    });
    if (!upstream.ok) {
      const code =
        (
          {
            400: "VALIDATION_FAILED",
            401: "SESSION_EXPIRED",
            403: "PERMISSION_DENIED",
            404: "NOT_FOUND",
            409: "CONFLICT",
          } as Record<number, string>
        )[upstream.status] ?? "BACKEND_UNAVAILABLE";
      return errorResponse(
        upstream.status >= 500 ? 502 : upstream.status,
        code,
      );
    }
    const raw = await upstream.json();
    const parsed = contract.response.safeParse(raw);
    if (!parsed.success) return errorResponse(502, "CONTRACT_MISMATCH");
    const data = parsed.data as Record<string, unknown>;
    if (contract.nestedPropertyResources) {
      const overview = managementSchema.safeParse(parsed.data);
      if (
        !overview.success ||
        !contract.id ||
        !careBelongsTo(overview.data, contract.id, session.workspaceId)
      )
        return errorResponse(502, "CONTRACT_MISMATCH");
    }
    if (
      (data.workspaceId && data.workspaceId !== session.workspaceId) ||
      (contract.resourceField && data[contract.resourceField] !== contract.id)
    )
      return errorResponse(502, "CONTRACT_MISMATCH");
    if (
      contract.id &&
      data.transaction &&
      typeof data.transaction === "object"
    ) {
      const transaction = data.transaction as Record<string, unknown>;
      if (
        transaction.id !== contract.id ||
        transaction.workspaceId !== session.workspaceId
      )
        return errorResponse(502, "CONTRACT_MISMATCH");
      for (const name of ["viewings", "offers"]) {
        const items = data[name];
        if (
          Array.isArray(items) &&
          items.some((item) => item.transactionId !== contract.id)
        )
          return errorResponse(502, "CONTRACT_MISMATCH");
      }
      for (const name of ["viewing", "offer"]) {
        const item = data[name];
        if (
          item &&
          typeof item === "object" &&
          (item as Record<string, unknown>).transactionId !== contract.id
        )
          return errorResponse(502, "CONTRACT_MISMATCH");
      }
    }
    const responseHeaders = new Headers({ "Cache-Control": "no-store" });
    if (upstream.headers.has("Idempotency-Replayed"))
      responseHeaders.set(
        "Idempotency-Replayed",
        upstream.headers.get("Idempotency-Replayed")!,
      );
    return Response.json(parsed.data, {
      status: upstream.status,
      headers: responseHeaders,
    });
  } catch {
    return errorResponse(502, "BACKEND_UNAVAILABLE");
  }
}
