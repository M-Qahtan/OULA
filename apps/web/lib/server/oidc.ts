import * as oidc from "openid-client";
import { createRemoteJWKSet, jwtVerify } from "jose";
import { getServerConfig, trustedUrl } from "./config";
import { sessionFromClaims } from "./session";

export async function oidcConfiguration() {
  const config = getServerConfig();
  const client = await oidc.discovery(
    config.issuer,
    config.clientId,
    undefined,
    config.clientSecret
      ? oidc.ClientSecretPost(config.clientSecret)
      : oidc.None(),
    config.allowLocalHttp
      ? { execute: [oidc.allowInsecureRequests] }
      : undefined,
  );
  for (const endpoint of [
    client.serverMetadata().authorization_endpoint,
    client.serverMetadata().token_endpoint,
    client.serverMetadata().jwks_uri,
  ]) {
    if (!endpoint) throw new Error("OIDC metadata is incomplete");
    trustedUrl(endpoint, config.allowLocalHttp);
  }
  // Explicitly validate signed ID tokens in addition to PKCE, state, nonce and TLS.
  oidc.enableNonRepudiationChecks(client);
  return client;
}
export async function startLogin() {
  const config = getServerConfig();
  const client = await oidcConfiguration();
  const verifier = oidc.randomPKCECodeVerifier();
  const state = oidc.randomState();
  const nonce = oidc.randomNonce();
  const url = oidc.buildAuthorizationUrl(client, {
    redirect_uri: new URL("/api/auth/callback", config.origin).href,
    scope: config.scopes,
    code_challenge: await oidc.calculatePKCECodeChallenge(verifier),
    code_challenge_method: "S256",
    state,
    nonce,
  });
  return { url, flow: { verifier, state, nonce } };
}
export async function finishLogin(
  currentUrl: URL,
  flow: { verifier: string; state: string; nonce: string },
) {
  const config = getServerConfig();
  const client = await oidcConfiguration();
  const tokens = await oidc.authorizationCodeGrant(client, currentUrl, {
    pkceCodeVerifier: flow.verifier,
    expectedState: flow.state,
    expectedNonce: flow.nonce,
    idTokenExpected: true,
  });
  const jwks = createRemoteJWKSet(new URL(client.serverMetadata().jwks_uri!), {
    timeoutDuration: 5000,
  });
  const { payload } = await jwtVerify(tokens.access_token, jwks, {
    issuer: client.serverMetadata().issuer,
    audience: config.audience,
    algorithms: ["RS256", "ES256"],
  });
  if (payload.sub !== tokens.claims()?.sub)
    throw new Error("Token subject mismatch");
  return sessionFromClaims(tokens.access_token, payload);
}
