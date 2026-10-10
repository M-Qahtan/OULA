import { describe, expect, it } from "vitest";
import { demoProperties, previewMatch } from "../lib/model";
import { createOulaApi } from "../lib/api";

const intent = { budgetSar: 1200000, minBedrooms: 3, minAreaSqm: 155, preferredDistrict: "الملقا" };
describe("OULA preview boundary", () => {
  it("denies candidates violating mandatory constraints", () => {
    expect(previewMatch(intent, demoProperties[1]!).blocked).toBe(true);
    expect(previewMatch(intent, demoProperties[2]!).blocked).toBe(true);
  });
  it("exposes a deterministic transparent demo score", () => {
    const result = previewMatch(intent, demoProperties[0]!);
    expect(result.blocked).toBe(false);
    expect(result.score).toBeGreaterThan(0);
    expect(result.reasons.some((x) => x.includes("توضيحي"))).toBe(true);
  });
  it("does not allow an unauthenticated API to masquerade as live", () => {
    expect(() => createOulaApi("http://localhost:8080", { accessToken: "", workspaceId: "demo", purpose: "PROPERTY_MANAGEMENT" })).toThrow();
  });
  it("declares demo properties only, never verified inventory", () => {
    expect(demoProperties.every((p) => p.id.startsWith("demo-") && p.evidence !== "EXPIRED")).toBe(true);
  });
});
