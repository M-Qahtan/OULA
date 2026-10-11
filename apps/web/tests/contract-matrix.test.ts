import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";

type MatrixRow = [
  id: string,
  screen: string,
  domain: string,
  contractStatus: string,
  currentScreenMode: string,
  method: string | null,
  path: string | null,
  operationId: string | null,
  requestSchema: string | null,
  responseSchema: string | null,
  purposes: string[],
  scopes: string[],
  workspaceHeader: boolean,
  idempotency: boolean,
  frontendAdapter: string | null,
  blocker: string | null,
];

type Matrix = {
  columns: string[];
  entries: MatrixRow[];
};

const matrix = JSON.parse(
  readFileSync(new URL("../../../docs/delivery/frontend-backend-contract-matrix.json", import.meta.url), "utf8"),
) as Matrix;

const openapi = readFileSync(new URL("../../../contracts/openapi.yaml", import.meta.url), "utf8");
const apiSource = readFileSync(new URL("../lib/api.ts", import.meta.url), "utf8");

const javaSources = [
  "IntentController.java",
  "MatchingController.java",
  "PropertyPassportController.java",
  "TransactionController.java",
  "DecisionIntelligenceController.java",
  "RealityScienceController.java",
  "PropertyManagementController.java",
  "PropertyVitalsController.java",
  "OperationalAdvisoryController.java",
].map((name) =>
  readFileSync(new URL(`../../../src/main/java/com/oula/api/${name}`, import.meta.url), "utf8"),
).join("\n");

const asRecord = (row: MatrixRow) =>
  Object.fromEntries(matrix.columns.map((column, index) => [column, row[index]])) as {
    id: string;
    screen: string;
    domain: string;
    contractStatus: string;
    currentScreenMode: string;
    method: string | null;
    path: string | null;
    operationId: string | null;
    requestSchema: string | null;
    responseSchema: string | null;
    purposes: string[];
    scopes: string[];
    workspaceHeader: boolean;
    idempotency: boolean;
    frontendAdapter: string | null;
    blocker: string | null;
  };

const entries = matrix.entries.map(asRecord);

function pathBlock(path: string): string {
  const marker = `  ${path}:\n`;
  const start = openapi.indexOf(marker);
  if (start < 0) return "";
  const rest = openapi.slice(start + marker.length);
  const nextPath = rest.search(/^  \/v1\//m);
  return marker + (nextPath < 0 ? rest : rest.slice(0, nextPath));
}

describe("frontend/backend contract matrix", () => {
  it("has one stable schema for every machine-readable row", () => {
    expect(matrix.columns).toEqual([
      "id",
      "screen",
      "domain",
      "contractStatus",
      "currentScreenMode",
      "method",
      "path",
      "operationId",
      "requestSchema",
      "responseSchema",
      "purposes",
      "scopes",
      "workspaceHeader",
      "idempotency",
      "frontendAdapter",
      "blocker",
    ]);
    expect(new Set(entries.map((entry) => entry.id)).size).toBe(entries.length);
  });

  it("pins every contracted journey operation to the current OpenAPI", () => {
    for (const entry of entries.filter((item) => item.contractStatus.startsWith("CONTRACTED_"))) {
      expect(entry.path, entry.id).toBeTruthy();
      expect(entry.method, entry.id).toBeTruthy();
      expect(entry.operationId, entry.id).toBeTruthy();

      const block = pathBlock(entry.path!);
      expect(block, `${entry.id}: missing path ${entry.path}`).not.toBe("");
      expect(block, `${entry.id}: missing method ${entry.method}`).toContain(
        `    ${entry.method!.toLowerCase()}:`,
      );
      expect(block, `${entry.id}: operation drift`).toContain(
        `operationId: ${entry.operationId}`,
      );

      if (entry.workspaceHeader) {
        expect(block, `${entry.id}: workspace boundary missing`).toContain(
          "#/components/parameters/WorkspaceId",
        );
        expect(block, `${entry.id}: purpose boundary missing`).toContain(
          "#/components/parameters/Purpose",
        );
      }
      if (entry.idempotency) {
        expect(block, `${entry.id}: Idempotency-Key contract missing`).toContain(
          "#/components/parameters/IdempotencyKey",
        );
      }
      if (entry.requestSchema) {
        expect(openapi, `${entry.id}: request schema drift`).toContain(
          `    ${entry.requestSchema}:`,
        );
      }
      if (entry.responseSchema) {
        expect(openapi, `${entry.id}: response schema drift`).toContain(
          `    ${entry.responseSchema}:`,
        );
      }
    }
  });

  it("keeps typed matrix rows backed by real frontend adapter methods", () => {
    for (const entry of entries.filter((item) => item.contractStatus === "CONTRACTED_TYPED")) {
      expect(entry.frontendAdapter, entry.id).toBeTruthy();
      expect(apiSource, `${entry.id}: typed adapter drift`).toContain(
        `${entry.frontendAdapter}:`,
      );
    }
  });

  it("pins declared authorization scopes to actual Java API sources", () => {
    for (const entry of entries.filter((item) => item.contractStatus.startsWith("CONTRACTED_"))) {
      expect(entry.scopes.length, `${entry.id}: contracted endpoint without scope`).toBeGreaterThan(0);
      for (const scope of entry.scopes) {
        expect(javaSources, `${entry.id}: scope ${scope} not found in Java API boundary`).toContain(scope);
      }
    }
  });

  it("keeps explicitly missing Deal Room commands absent until backend adds them intentionally", () => {
    const missing = entries.filter((item) => item.contractStatus === "MISSING");
    expect(missing.map((item) => item.id)).toEqual([
      "transaction-create",
      "viewing-request",
      "offer-submit",
    ]);
    for (const entry of missing) {
      expect(entry.path, entry.id).toBeTruthy();
      expect(pathBlock(entry.path!), `${entry.id} unexpectedly appeared; update matrix and frontend contract`).toBe("");
      expect(entry.blocker, entry.id).toBeTruthy();
    }
  });

  it("does not label any current browser journey as LIVE before authenticated integration proof", () => {
    expect(entries.filter((entry) => entry.currentScreenMode === "LIVE")).toEqual([]);
  });

  it("makes current Guardian OpenAPI schema gaps explicit instead of inventing DTOs", () => {
    const gaps = entries.filter((entry) => entry.contractStatus === "CONTRACTED_SCHEMA_GAP");
    expect(gaps.map((entry) => entry.id)).toEqual(["guardian-overview", "guardian-assess"]);
    expect(gaps.every((entry) => entry.responseSchema === null && entry.frontendAdapter === null)).toBe(true);
  });
});
