import { NextRequest, NextResponse } from "next/server";
import { z } from "zod";
import {
  getServerConfig,
  localDevAuthEnabled,
} from "../../../../lib/server/config";
import { sameOrigin } from "../../../../lib/server/bridge";
import { SESSION_COOKIE, readSession } from "../../../../lib/server/session";
export const runtime = "nodejs";
/** Seeds simulated commute signals only, never verified evidence or financial truth. */
export async function POST(request: NextRequest) {
  if (!localDevAuthEnabled())
    return NextResponse.json({ code: "NOT_FOUND" }, { status: 404 });
  if (!sameOrigin(request))
    return NextResponse.json({ code: "ORIGIN_DENIED" }, { status: 403 });
  const session = await readSession(request.cookies.get(SESSION_COOKIE)?.value);
  if (
    !session ||
    session.mode !== "DEV_TEST_ONLY" ||
    session.fixture !== "buyer"
  )
    return NextResponse.json({ code: "AUTH_REQUIRED" }, { status: 401 });
  const input = z
    .object({ intentId: z.uuid() })
    .safeParse(await request.json().catch(() => null));
  if (!input.success)
    return NextResponse.json({ code: "VALIDATION_FAILED" }, { status: 400 });
  try {
    const upstream = await fetch(
      new URL(
        `/dev-auth/golden-path/intents/${input.data.intentId}/prepare`,
        getServerConfig().backend,
      ),
      {
        method: "POST",
        headers: {
          Authorization: `Bearer ${session.accessToken}`,
          "X-OULA-Workspace-ID": session.workspaceId,
          "X-OULA-Purpose": "PROPERTY_DECISION_SUPPORT",
        },
        cache: "no-store",
        redirect: "error",
        signal: AbortSignal.timeout(10000),
      },
    );
    if (!upstream.ok)
      return NextResponse.json(
        {
          code:
            upstream.status === 403
              ? "PERMISSION_DENIED"
              : "BACKEND_UNAVAILABLE",
        },
        { status: upstream.status >= 500 ? 502 : upstream.status },
      );
    const value = z
      .object({
        mode: z.literal("DEV_TEST_ONLY"),
        intentId: z.literal(input.data.intentId),
        verifiedExternalData: z.literal(false),
        signalSource: z.literal("DEV_FIXTURE_SIMULATION"),
      })
      .parse(await upstream.json());
    return NextResponse.json(value, {
      headers: { "Cache-Control": "no-store" },
    });
  } catch {
    return NextResponse.json({ code: "BACKEND_UNAVAILABLE" }, { status: 502 });
  }
}
