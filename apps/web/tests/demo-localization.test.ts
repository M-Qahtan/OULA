import { describe, expect, it } from "vitest";
import { demoProperties, previewMatch } from "../lib/model";
import { demoLabel, demoReason, demoStory } from "../components/demo-localization";

const hasArabic = /[\u0600-\u06ff]/;

describe("fictional Riyadh fixture localization", () => {
  it("keeps Arabic source labels unchanged", () => {
    const property = demoProperties[0]!;
    expect(demoLabel("ar", property.district)).toBe(property.district);
    expect(demoStory("ar", property)).toBe(property.story);
    expect(demoReason("ar", "مطابقة الحي المفضل")).toBe("مطابقة الحي المفضل");
  });

  it("shows every fictional city, district, kind and story without Arabic glyphs in English", () => {
    for (const property of demoProperties) {
      for (const value of [property.city, property.district, property.kind]) {
        expect(demoLabel("en", value)).not.toMatch(hasArabic);
      }
      expect(demoStory("en", property)).not.toMatch(hasArabic);
    }
  });

  it("translates every demo match explanation without changing its deterministic demo score", () => {
    const intent = { budgetSar: 1200000, minBedrooms: 3, minAreaSqm: 155, preferredDistrict: "الملقا" };
    for (const property of demoProperties) {
      const result = previewMatch(intent, property);
      for (const reason of result.reasons) {
        expect(demoReason("en", reason)).not.toMatch(hasArabic);
      }
      expect(previewMatch(intent, property).score).toBe(result.score);
    }
    expect(demoReason("en", "هامش الميزانية الافتراضي: 9٪")).toBe("Illustrative budget headroom: 9%");
  });
});
