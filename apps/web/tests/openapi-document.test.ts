import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";
import { parseDocument } from "yaml";

describe("canonical OpenAPI document", () => {
  it("parses with unique keys and resolves every internal schema reference", () => {
    const text = readFileSync(
      new URL("../../../contracts/openapi.yaml", import.meta.url),
      "utf8",
    );
    const document = parseDocument(text, { uniqueKeys: true });
    expect(document.errors.slice(0, 3).map((error) => error.message)).toEqual(
      [],
    );
    const data = document.toJS();
    expect(data.openapi).toBe("3.1.0");
    for (const match of text.matchAll(/\$ref:\s*['"]?(#\/[A-Za-z0-9_/-]+)/g)) {
      const path = match[1]!.slice(2).split("/");
      let node: unknown = data;
      for (const part of path) node = (node as Record<string, unknown>)?.[part];
      expect(node, `unresolved reference: ${match[1]}`).toBeDefined();
    }
  });
});
