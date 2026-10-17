import { NextRequest, NextResponse } from "next/server";
import { decodeJwt } from "jose";
import { z } from "zod";
import {
  getServerConfig,
  localDevAuthEnabled,
} from "../../../../lib/server/config";
import { sameOrigin } from "../../../../lib/server/bridge";
import {
  SESSION_COOKIE,
  sessionFromClaims,
  sealCookie,
  cookieOptions,
  publicSession,
} from "../../../../lib/server/session";

export const runtime = "nodejs";
/** Explicit local fixture, never an alternative when production OIDC fails. */
export async function POST(request: NextRequest) {
  if (!localDevAuthEnabled())
    return NextResponse.json({ code: "NOT_FOUND" }, { status: 404 });
  if (!sameOrigin(request))
    return NextResponse.json({ code: "ORIGIN_DENIED" }, { status: 403 });
  const input = z
    .object({ fixture: z.enum(["buyer", "property-manager"]) })
    .safeParse(await request.json().catch(() => null));
  if (!input.success)
    return NextResponse.json({ code: "VALIDATION_FAILED" }, { status: 400 });
  try {
    const config = getServerConfig();
    const upstream = await fetch(new URL("/dev-auth/token", config.backend), {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(input.data),
      cache: "no-store",
      redirect: "error",
      signal: AbortSignal.timeout(10000),
    });
    if (!upstream.ok) throw new Error("Fixture unavailable");
    const issued = z
      .object({
        accessToken: z.string(),
        mode: z.literal("DEV_TEST_ONLY"),
        fixture: z.literal(input.data.fixture),
        workspaceId: z.uuid(),
      })
      .parse(await upstream.json());
    // Issued directly by the configured loopback Java service, then proved by its JWT decoder.
    const session = {
      ...sessionFromClaims(issued.accessToken, decodeJwt(issued.accessToken)),
      mode: "DEV_TEST_ONLY" as const,
      fixture: input.data.fixture,
    };
    if (session.workspaceId !== issued.workspaceId)
      throw new Error("Fixture workspace mismatch");
    const proof = await fetch(
      new URL("/v1/experience/manifest", config.backend),
      {
        headers: {
          Authorization: `Bearer ${session.accessToken}`,
          "X-OULA-Workspace-ID": session.workspaceId,
          "X-OULA-Purpose": session.purposes[0]!,
        },
        cache: "no-store",
        redirect: "error",
        signal: AbortSignal.timeout(10000),
      },
    );
    if (!proof.ok) throw new Error("Signed identity denied by Java");
    const manifest = z
      .object({
        workspaceId: z.literal(session.workspaceId),
        purpose: z.literal(session.purposes[0]!),
        source: z.literal("AUTHENTICATED_SCOPE_PROJECTION"),
      })
      .parse(await proof.json());
    if (!manifest) throw new Error("Invalid identity projection");
    const response = NextResponse.json(publicSession(session), {
      headers: { "Cache-Control": "no-store" },
    });
    response.cookies.set(
      SESSION_COOKIE,
      await sealCookie(session, session.exp),
      cookieOptions(session.exp - Math.floor(Date.now() / 1000)),
    );
    return response;
  } catch {
    return NextResponse.json({ code: "AUTH_UNAVAILABLE" }, { status: 503 });
  }
}
