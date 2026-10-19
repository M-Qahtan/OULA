import { NextRequest, NextResponse } from "next/server";
import { z } from "zod";
import { finishLogin } from "../../../../lib/server/oidc";
import { getServerConfig } from "../../../../lib/server/config";
import { safeWorkspaceReturnTo } from "../../../../lib/family-catalog";
import {
  FLOW_COOKIE,
  SESSION_COOKIE,
  openCookie,
  sealCookie,
  cookieOptions,
} from "../../../../lib/server/session";
export const runtime = "nodejs";
export async function GET(request: NextRequest) {
  let returnTo = "/workspace?connection=server";
  try {
    const config = getServerConfig();
    const flow = z
      .object({
        verifier: z.string(),
        state: z.string(),
        nonce: z.string(),
        returnTo: z.string().optional(),
      })
      .parse(await openCookie(request.cookies.get(FLOW_COOKIE)?.value));
    returnTo = safeWorkspaceReturnTo(flow.returnTo);
    const callback = new URL("/api/auth/callback", config.origin);
    callback.search = request.nextUrl.search;
    const session = await finishLogin(callback, flow);
    const response = NextResponse.redirect(new URL(returnTo, config.origin));
    response.cookies.set(
      SESSION_COOKIE,
      await sealCookie(session, session.exp),
      cookieOptions(session.exp - Math.floor(Date.now() / 1000)),
    );
    response.cookies.delete(FLOW_COOKIE);
    response.headers.set("Cache-Control", "no-store");
    return response;
  } catch {
    let origin: URL;
    try {
      origin = getServerConfig().origin;
    } catch {
      return NextResponse.json({ code: "AUTH_UNAVAILABLE" }, { status: 503 });
    }
    const destination = new URL(returnTo, origin);
    destination.searchParams.set("authError", "1");
    const response = NextResponse.redirect(destination);
    response.cookies.delete(FLOW_COOKIE);
    response.cookies.delete(SESSION_COOKIE);
    response.headers.set("Cache-Control", "no-store");
    return response;
  }
}
