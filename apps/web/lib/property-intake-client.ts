import type { AuthenticatedOulaContext } from "./api";

export type DeclaredProperty = Readonly<{
  id: string;
  workspaceId: string;
  assetType: "RESIDENTIAL" | "COMMERCIAL";
  district: string;
  bedrooms: number;
  askingPrice: number;
  dataOrigin: "DEMO" | "USER_DECLARED" | "UNKNOWN";
  sourceType: string;
  truthStatus: "DECLARED" | "UNKNOWN";
}>;
export type DraftListing = Readonly<{
  id: string;
  workspaceId: string;
  propertyId: string;
  transactionType: "SALE" | "RENT";
  status: "DRAFT";
  askingPrice: number;
  currency: "SAR";
  dataOrigin: "DEMO" | "USER_DECLARED" | "UNKNOWN";
  sourceType: string;
  truthStatus: "DECLARED" | "UNKNOWN";
}>;
export type PropertyInput = Readonly<{
  assetType: "RESIDENTIAL" | "COMMERCIAL";
  district: string;
  bedrooms: number;
  askingPrice: number;
  demo: boolean;
}>;

export function createPrivatePropertyIntakeClient(apiOrigin: string, ctx: AuthenticatedOulaContext) {
  const origin = new URL(apiOrigin);
  if (!["http:", "https:"].includes(origin.protocol) || origin.pathname !== "/" ||
      origin.search || origin.hash || origin.username || origin.password) {
    throw new Error("Clean explicit OULA API origin required");
  }
  if (!ctx.accessToken || !ctx.workspaceId ||
      !["PROPERTY_MANAGEMENT","PROPERTY_DECISION_SUPPORT"].includes(ctx.purpose)) {
    throw new Error("Authorized OULA property workspace required");
  }
  const byId = (id: string) => {
    if (!/^[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}$/i.test(id)) {
      throw new Error("Valid property or listing UUID required");
    }
    return encodeURIComponent(id);
  };
  async function request<T>(path: string, method: "GET" | "POST", key?: string, body?: object): Promise<T> {
    if (method === "POST" && (!key || !key.trim())) throw new Error("Idempotency-Key required");
    const response = await fetch(new URL(path, origin), {
      method, cache: "no-store",
      headers: {
        Authorization: "Bearer " + ctx.accessToken,
        "X-OULA-Workspace-ID": ctx.workspaceId,
        "X-OULA-Purpose": ctx.purpose,
        Accept: "application/json",
        ...(key ? { "Idempotency-Key": key } : {}),
        ...(body ? { "Content-Type": "application/json" } : {}),
      },
      ...(body ? { body: JSON.stringify(body) } : {}),
    });
    if (!response.ok) throw new Error("OULA property API rejected request: " + response.status);
    return response.json() as Promise<T>;
  }
  return {
    createProperty: (body: PropertyInput, key: string) =>
      request<DeclaredProperty>("/v1/properties", "POST", key, body),
    readProperty: (id: string) =>
      request<DeclaredProperty>("/v1/properties/" + byId(id), "GET"),
    createListing: (id: string, type: "SALE" | "RENT", price: number, key: string) =>
      request<DraftListing>("/v1/properties/" + byId(id) + "/listings", "POST", key,
        { transactionType: type, askingPrice: price }),
    readListing: (id: string) =>
      request<DraftListing>("/v1/listings/" + byId(id), "GET"),
    searchPrivateDrafts: (district?: string) =>
      request<readonly DraftListing[]>("/v1/listings" +
        (district ? "?district=" + encodeURIComponent(district) : ""), "GET"),
  };
}
