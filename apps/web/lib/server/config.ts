import { createHash } from "node:crypto";

type ServerConfig = {
  backend: URL;
  origin: URL;
  issuer: URL;
  clientId: string;
  clientSecret?: string;
  audience: string;
  scopes: string;
  key: Uint8Array;
  allowLocalHttp: boolean;
};
export function trustedUrl(value: string, allowLocalHttp = false): URL {
  const url = new URL(value);
  const loopback = ["localhost", "127.0.0.1", "[::1]"].includes(url.hostname);
  if (
    url.username ||
    url.password ||
    url.search ||
    url.hash ||
    (url.protocol !== "https:" &&
      !(allowLocalHttp && loopback && url.protocol === "http:"))
  )
    throw new Error("Untrusted service URL");
  return url;
}
/** Configuration is server-only. Nothing here accepts a destination from the browser. */
export function getServerConfig(): ServerConfig {
  const env = process.env;
  const secret = env.OULA_SESSION_SECRET;
  if (
    !secret ||
    secret.length < 32 ||
    !env.OULA_BACKEND_URL ||
    !env.OULA_APP_ORIGIN ||
    !env.OULA_OIDC_ISSUER ||
    !env.OULA_OIDC_CLIENT_ID
  )
    throw new Error("Integration is not configured");
  const allowLocalHttp = env.OULA_ALLOW_HTTP_LOCALHOST === "true";
  const backend = trustedUrl(env.OULA_BACKEND_URL, allowLocalHttp);
  const origin = trustedUrl(env.OULA_APP_ORIGIN, allowLocalHttp);
  if (backend.pathname !== "/" || origin.pathname !== "/")
    throw new Error("Backend and app must be origins");
  return {
    backend,
    origin,
    issuer: trustedUrl(env.OULA_OIDC_ISSUER, allowLocalHttp),
    clientId: env.OULA_OIDC_CLIENT_ID,
    clientSecret: env.OULA_OIDC_CLIENT_SECRET,
    audience: env.OULA_OIDC_AUDIENCE ?? "oula-api",
    scopes:
      env.OULA_OIDC_SCOPES ??
      "openid oula.property.write oula.property.read oula.listing.write oula.listing.read oula.consent.read oula.consent.write oula.transaction.create oula.transaction.viewing.request oula.transaction.offer.submit oula.intent.read oula.intent.write oula.matching.run oula.property.passport.read oula.intelligence.recommend oula.intelligence.read oula.intelligence.decide oula.transaction.read oula.transaction.advance oula.property.vitals.read oula.property.advisory.read",
    key: createHash("sha256").update(secret).digest(),
    allowLocalHttp,
  };
}
export function localDevAuthEnabled(): boolean {
  try {
    const config = getServerConfig();
    return (
      process.env.OULA_LOCAL_DEV_AUTH === "true" &&
      config.allowLocalHttp &&
      [config.origin, config.backend, config.issuer].every(
        (url) =>
          url.protocol === "http:" &&
          ["localhost", "127.0.0.1", "[::1]"].includes(url.hostname),
      )
    );
  } catch {
    return false;
  }
}
export function isConfigured(): boolean {
  try {
    getServerConfig();
    return true;
  } catch {
    return false;
  }
}
