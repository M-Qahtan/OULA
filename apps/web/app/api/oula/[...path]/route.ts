import { NextRequest } from "next/server";
import { bridgeRequest } from "../../../../lib/server/bridge";
import { SESSION_COOKIE, readSession } from "../../../../lib/server/session";
export const runtime = "nodejs";
type Context = { params: Promise<{ path: string[] }> };
async function handle(request: NextRequest, context: Context) {
  const { path } = await context.params;
  return bridgeRequest(
    request,
    "/" + path.join("/"),
    await readSession(request.cookies.get(SESSION_COOKIE)?.value),
  );
}
export const GET = handle;
export const POST = handle;
