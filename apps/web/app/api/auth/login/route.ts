import { NextResponse } from "next/server";
import { startLogin } from "../../../../lib/server/oidc";
import {
  FLOW_COOKIE,
  sealCookie,
  cookieOptions,
} from "../../../../lib/server/session";
export const runtime = "nodejs";
export async function GET() {
  try {
    const { url, flow } = await startLogin();
    const response = NextResponse.redirect(url);
    response.headers.set("Cache-Control", "no-store");
    response.cookies.set(
      FLOW_COOKIE,
      await sealCookie(flow, Math.floor(Date.now() / 1000) + 600),
      cookieOptions(600),
    );
    return response;
  } catch {
    return NextResponse.json({ code: "AUTH_UNAVAILABLE" }, { status: 503 });
  }
}
