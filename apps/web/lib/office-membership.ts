import type { AuthenticatedOulaContext } from "./api";
import { OulaApiError } from "./api";

export type OfficeMember = Readonly<{
  workspaceId: string; actorId: string;
  role: "ADMIN" | "BROKER" | "STAFF"; status: "ACTIVE" | "REVOKED";
}>;
export type OfficeInvitation = Readonly<{
  invitationId: string; workspaceId: string; actorId: string;
  role: "BROKER" | "STAFF"; status: "PENDING" | "ACCEPTED" | "CANCELLED";
  expiresAt: string;
}>;
export type OfficeInviteCommand = Readonly<{
  actorId: string; issuer: string; subject: string;
  role: "BROKER" | "STAFF"; expiresAt: string;
}>;

/** Office membership client: never grant UI role permissions locally. */
export function createOfficeMembershipApi(
  baseUrl: string, ctx: AuthenticatedOulaContext, fetcher: typeof fetch = fetch,
) {
  if (!/^https?:\/\//.test(baseUrl)) throw new Error("Explicit HTTP(S) office API required");
  if (!ctx.accessToken || !ctx.workspaceId || ctx.purpose !== "TRANSACTION_EXECUTION") {
    throw new Error("Signed transaction-execution context required");
  }
  async function request<T>(path: string, method: "GET" | "POST" = "GET",
                            data?: unknown, idempotencyKey?: string): Promise<T> {
    if (method === "POST" && !idempotencyKey?.trim()) throw new Error("Idempotency-Key required");
    const response=await fetcher(new URL(path,baseUrl),{
      method,cache:"no-store",
      headers:{
        Authorization:`Bearer ${ctx.accessToken}`,
        "X-OULA-Workspace-ID":ctx.workspaceId,
        "X-OULA-Purpose":ctx.purpose,
        Accept:"application/json",
        ...(idempotencyKey?{"Idempotency-Key":idempotencyKey}:{}),
        ...(data===undefined?{}:{"Content-Type":"application/json"}),
      },
      ...(data===undefined?{}:{body:JSON.stringify(data)}),
    });
    if(!response.ok) throw new OulaApiError(response.status,`Office API denied: ${response.status}`);
    return await response.json() as T;
  }
  function check<T extends {workspaceId:string}>(row:T):T {
    if(row.workspaceId!==ctx.workspaceId)throw new Error("Office response workspace mismatch");
    return row;
  }
  const list=<T extends {workspaceId:string}>(path:string)=>request<T[]>(path).then(rows=>rows.map(check));
  return {
    activeMembers:()=>list<OfficeMember>("/v1/offices/members"),
    pendingInvitations:()=>list<OfficeInvitation>("/v1/offices/members/invitations"),
    myInvitations:()=>list<OfficeInvitation>("/v1/offices/members/invitations/mine"),
    invite:async(data:OfficeInviteCommand,key:string)=>
      check(await request<OfficeInvitation>("/v1/offices/members/invitations","POST",data,key)),
    accept:async(id:string,key:string)=>
      check(await request<OfficeMember>(`/v1/offices/members/invitations/${encodeURIComponent(id)}/accept`,"POST",undefined,key)),
    cancel:async(id:string,key:string)=>
      check(await request<OfficeInvitation>(`/v1/offices/members/invitations/${encodeURIComponent(id)}/cancel`,"POST",undefined,key)),
    revoke:async(actorId:string,key:string)=>
      check(await request<OfficeMember>(`/v1/offices/members/${encodeURIComponent(actorId)}/revoke`,"POST",undefined,key)),
  };
}
