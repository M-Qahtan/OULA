/** Synthetic, loopback-only OIDC + API contract harness. NOT Java/PostgreSQL. */
import { createServer } from "node:http";
import { createHash, randomUUID } from "node:crypto";
import { generateKeyPair, exportJWK, SignJWT, jwtVerify } from "jose";

const issuer = "http://127.0.0.1:8099";
const origin = "http://127.0.0.1:3000";
const workspace = "123e4567-e89b-42d3-a456-426614174001";
const actor = "123e4567-e89b-42d3-a456-426614174002";
const property = "123e4567-e89b-42d3-a456-426614174003";
const intentId = "123e4567-e89b-42d3-a456-426614174004";
const runId = "123e4567-e89b-42d3-a456-426614174005";
const recommendationId = "123e4567-e89b-42d3-a456-426614174006";
const decisionId = "123e4567-e89b-42d3-a456-426614174007";
const transactionId = "123e4567-e89b-42d3-a456-426614174008";
const {privateKey, publicKey} = await generateKeyPair("RS256");
const jwk = {...await exportJWK(publicKey), kid: "contract-harness-key", use: "sig", alg: "RS256"};
const codes = new Map();
const intents = new Map();
const replay = new Map();
let requests = [];
let scenario = "normal";
let transaction = {id: transactionId, workspaceId: workspace, stage: "VIEWING", version: 2};
let viewings = []; let offers = [];
const now = () => new Date().toISOString();

const body = async request => {let result = ""; for await (const part of request) result += part; return result;};
function send(response, status, value, headers = {}) {response.writeHead(status,{"Content-Type":"application/json","Cache-Control":"no-store",...headers}); response.end(JSON.stringify(value));}
async function token(payload, audience) {return new SignJWT(payload).setProtectedHeader({alg:"RS256",kid:jwk.kid}).setSubject("contract-test-user").setIssuer(issuer).setAudience(audience).setIssuedAt().setExpirationTime("15m").sign(privateKey);}

const server = createServer(async (request,response) => {
  try {
    const url = new URL(request.url,issuer);
    if (url.pathname === "/health") return send(response,200,{status:"synthetic-contract-harness"});
    if (url.pathname === "/_test/reset") {requests=[]; scenario="normal"; replay.clear(); intents.clear(); viewings=[]; offers=[]; transaction={id:transactionId,workspaceId:workspace,stage:"VIEWING",version:2}; return send(response,200,{});}
    if (url.pathname === "/_test/scenario") {scenario=JSON.parse(await body(request)).scenario; return send(response,200,{});}
    if (url.pathname === "/_test/requests") return send(response,200,{requests});
    if (url.pathname === "/.well-known/openid-configuration") return send(response,200,{issuer,authorization_endpoint:issuer+"/authorize",token_endpoint:issuer+"/token",jwks_uri:issuer+"/jwks",response_types_supported:["code"],subject_types_supported:["public"],id_token_signing_alg_values_supported:["RS256"],token_endpoint_auth_methods_supported:["none"],code_challenge_methods_supported:["S256"]});
    if (url.pathname === "/jwks") return send(response,200,{keys:[jwk]});
    if (url.pathname === "/authorize") {
      if (url.searchParams.get("redirect_uri") !== origin+"/api/auth/callback" || url.searchParams.get("client_id") !== "oula-web" || url.searchParams.get("code_challenge_method") !== "S256") return send(response,400,{error:"invalid_request"});
      const code = randomUUID(); codes.set(code,{nonce:url.searchParams.get("nonce"),challenge:url.searchParams.get("code_challenge"),scope:url.searchParams.get("scope")});
      const redirect = new URL("/api/auth/callback",origin); redirect.searchParams.set("code",code); redirect.searchParams.set("state",url.searchParams.get("state"));
      response.writeHead(302,{Location:redirect.href}); return response.end();
    }
    if (url.pathname === "/token") {
      const params = new URLSearchParams(await body(request)); const grant = codes.get(params.get("code")); codes.delete(params.get("code"));
      if (!grant || createHash("sha256").update(params.get("code_verifier") ?? "").digest("base64url") !== grant.challenge) return send(response,400,{error:"invalid_grant"});
      const access = await token({actor_id:actor,oula_workspace_ids:[workspace],oula_purposes:["PROPERTY_DECISION_SUPPORT","PROPERTY_MANAGEMENT","TRANSACTION_EXECUTION"],scope:grant.scope},"oula-api");
      const identity = await token({nonce:grant.nonce},"oula-web");
      return send(response,200,{access_token:access,id_token:identity,token_type:"Bearer",expires_in:900,scope:grant.scope});
    }
    if (!url.pathname.startsWith("/v1/")) return send(response,404,{});
    const bearer = request.headers.authorization?.replace(/^Bearer /,"");
    const {payload} = await jwtVerify(bearer,publicKey,{issuer,audience:"oula-api"});
    if (request.headers["x-oula-workspace-id"] !== workspace || !payload.oula_workspace_ids.includes(workspace)) return send(response,403,{});
    const expectedPurpose = url.pathname.startsWith("/v1/transactions") ? "TRANSACTION_EXECUTION" : url.pathname.includes("/vitals/") || url.pathname.endsWith("/operational-advice") ? "PROPERTY_MANAGEMENT" : "PROPERTY_DECISION_SUPPORT";
    if (request.headers["x-oula-purpose"] !== expectedPurpose) return send(response,403,{});
    const content = await body(request);
    const entry = {path:url.pathname,method:request.method,workspace:request.headers["x-oula-workspace-id"],purpose:request.headers["x-oula-purpose"],key:request.headers["idempotency-key"],hasBearer:!!bearer,body:content ? JSON.parse(content) : null}; requests.push(entry);
    if (["401","403","404","409","503"].includes(scenario)) return send(response,Number(scenario),{privateDetail:"not forwarded"});
    if (scenario === "malformed") return send(response,200,{unexpected:"shape"});
    if (scenario === "wrong-workspace") return send(response,200,{workspaceId:actor});
    if (request.method === "POST") {
      const key = entry.key;
      if (!key) return send(response,400,{});
      if (replay.has(key)) {
        const previous = replay.get(key);
        if (previous.path !== entry.path || previous.body !== content) return send(response,409,{});
        return send(response,previous.status,previous.value,{"Idempotency-Replayed":"true"});
      }
      let value;
      if (url.pathname === "/v1/consents/decisions") value={...entry.body,source:"USER_EXPLICIT",occurredAt:now()};
      else if (url.pathname === "/v1/transactions") {transaction={id:transactionId,workspaceId:workspace,stage:"DRAFT",version:0};value=transaction;}
      else if (url.pathname === `/v1/transactions/${transactionId}/transitions`) {transaction={...transaction,stage:entry.body.target,version:transaction.version+1};value=transaction;}
      else if (url.pathname === `/v1/transactions/${transactionId}/viewings`) {transaction={...transaction,stage:"VIEWING",version:transaction.version+1};const viewing={id:runId,transactionId,preferredAt:entry.body.preferredAt,note:null,status:"REQUESTED",requestedBy:actor,createdAt:now()};viewings.push(viewing);value={transaction,viewing};}
      else if (url.pathname === `/v1/transactions/${transactionId}/offers`) {transaction={...transaction,stage:"OFFERING",version:transaction.version+1};const offer={id:runId,transactionId,amount:entry.body.amount,currency:entry.body.currency,termsSummary:entry.body.termsSummary,status:"SUBMITTED",submittedBy:actor,createdAt:now()};offers.push(offer);value={transaction,offer};}
      else if (url.pathname === "/v1/intents") {value={...entry.body,id:intentId,workspaceId:workspace,status:"ACTIVE"}; intents.set(intentId,value);}
      else if (url.pathname === `/v1/intents/${intentId}/matches`) value={matchRunId:runId,matches:scenario === "empty" ? [] : [{propertyId:property,rank:1,score:84.5,confidence:.8,dimensions:{budget:90,district:80}}]};
      else if (url.pathname === "/v1/intelligence/recommendations") value={recommendationId,matchRunId:runId,recommendedPropertyId:property,lifeFitScore:84.5,confidence:.8,modelId:"CONTRACT_HARNESS",modelVersion:"test-only",evidenceIds:entry.body.evidenceIds,assumptionIds:[]};
      else if (url.pathname === `/v1/intelligence/recommendations/${recommendationId}/decisions`) value={decisionId,recommendationId,selectedPropertyId:property,acceptedRecommendation:true};
      else return send(response,404,{});
      replay.set(key,{path:entry.path,body:content,status:201,value}); return send(response,201,value,{"Idempotency-Replayed":"false"});
    }
    if (url.pathname === `/v1/intents/${intentId}`) return send(response,200,intents.get(intentId) ?? {id:intentId,workspaceId:workspace,intentType:"BUY",status:"ACTIVE",budgetMax:2000000,minimumBedrooms:3,preferredDistricts:["الملقا"]});
    if (url.pathname === `/v1/properties/${property}/passport`) return send(response,200,{propertyId:property,workspaceId:workspace,assetType:"CONTRACT_TEST_PROPERTY",district:"الملقا",bedrooms:3,askingPrice:1090000,facts:[{id:actor,key:"source",valueJson:'{"basis":"synthetic contract test"}',truthStatus:"DECLARED",sourceType:"CONTRACT_HARNESS"}],verifiedFactCoverage:0,generatedAt:now()});
    if (url.pathname === `/v1/transactions/${transactionId}/deal-room`) return send(response,200,{transaction,viewings,offers,availableCommands:transaction.stage === "DRAFT" ? ["QUALIFY"] : transaction.stage === "QUALIFIED" ? ["REQUEST_VIEWING"] : transaction.stage === "VIEWING" ? ["SUBMIT_OFFER"] : [],humanConfirmationRequired:true});
    if (url.pathname === `/v1/transactions/${transactionId}`) return send(response,200,{id:transactionId,workspaceId:workspace,stage:"VIEWING",version:2});
    if (url.pathname === `/v1/properties/${property}/vitals/latest`) return send(response,200,{id:actor,workspaceId:workspace,propertyId:property,policyKey:"TEST_ONLY",policyVersion:"1",overallStatus:"UNKNOWN",knownDimensionCount:0,overdueObligations:0,openWorkOrders:0,assessedAt:now()});
    if (url.pathname === `/v1/properties/${property}/operational-advice`) return send(response,200,{workspaceId:workspace,propertyId:property,sourceSnapshotId:actor,snapshotPolicyKey:"TEST_ONLY",snapshotPolicyVersion:"1",advisoryRulesVersion:"1",assessmentState:"LIMITED_EVIDENCE",observedTrend:"UNKNOWN",sourceAssessedAt:now(),generatedAt:now(),limitations:["Synthetic contract harness; no operational evidence."],recommendations:[]});
    if (url.pathname === `/v1/intelligence/recommendations/${recommendationId}/explain`) return send(response,200,{recommendationId,executiveSummary:"Synthetic recommendation for protocol testing only.",recommendedPropertyId:property,lifeFitScore:84.5,confidence:.8,confidenceBreakdown:{basis:"test"},uncertainty:{basis:"synthetic"},modelId:"CONTRACT_HARNESS",modelVersion:"test-only",modelType:"RULES",riskClass:"TEST",evidenceIds:[],assumptionIds:[],alternatives:[]});
    return send(response,404,{});
  } catch {send(response,401,{code:"unauthorized"});}
});
server.listen(8099,"127.0.0.1");
process.on("SIGTERM",()=>server.close());
