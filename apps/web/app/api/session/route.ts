import { NextRequest, NextResponse } from "next/server";
import { isConfigured, localDevAuthEnabled } from "../../../lib/server/config";
import {
  SESSION_COOKIE,
  readSession,
  publicSession,
  sealCookie,
  cookieOptions,
} from "../../../lib/server/session";
import { sameOrigin } from "../../../lib/server/bridge";
export const runtime = "nodejs";
export async function GET(request: NextRequest) {
  const session = await readSession(request.cookies.get(SESSION_COOKIE)?.value);
  return NextResponse.json(
    session
      ? publicSession(session)
      : {
          status: isConfigured() ? "signed-out" : "unconfigured",
          localDevAuth: localDevAuthEnabled(),
        },
    { headers: { "Cache-Control": "no-store" } },
  );
}
export async function POST(request: NextRequest) {
  const session = await readSession(request.cookies.get(SESSION_COOKIE)?.value);
  if (!session)
    return NextResponse.json({ code: "AUTH_REQUIRED" }, { status: 401 });
  if (!sameOrigin(request))
    return NextResponse.json({ code: "ORIGIN_DENIED" }, { status: 403 });
  let workspaceId: unknown;
  try {
    workspaceId = (await request.json()).workspaceId;
  } catch {
    return NextResponse.json({ code: "VALIDATION_FAILED" }, { status: 400 });
  }
  if (
    typeof workspaceId !== "string" ||
    !session.workspaceIds.includes(workspaceId)
  )
    return NextResponse.json({ code: "PERMISSION_DENIED" }, { status: 403 });
  const updated = { ...session, workspaceId };
  const response = NextResponse.json(publicSession(updated), {
    headers: { "Cache-Control": "no-store" },
  });
  response.cookies.set(
    SESSION_COOKIE,
    await sealCookie(updated, session.exp),
    cookieOptions(session.exp - Math.floor(Date.now() / 1000)),
  );
  return response;
}
export async function DELETE(request: NextRequest) {
  if (!isConfigured() || !sameOrigin(request))
    return NextResponse.json({ code: "ORIGIN_DENIED" }, { status: 403 });
  const response = NextResponse.json(
    { status: "signed-out" },
    { headers: { "Cache-Control": "no-store" } },
  );
  response.cookies.delete(SESSION_COOKIE);
  return response;
}
