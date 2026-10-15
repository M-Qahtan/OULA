import { describe, expect, it } from "vitest";
import { getWorld, OULA_WORLDS, suggestWorld } from "../lib/gateway";

describe("OULA five-family gateway v1", () => {
  it("has exactly the five approved public gateway families with unique identifiers", () => {
    expect(OULA_WORLDS.map(world => world.id)).toEqual(["pulse", "habitat", "nexus", "forge", "civic"]);
    expect(new Set(OULA_WORLDS.map(world => world.color)).size).toBe(5);
    for (const world of OULA_WORLDS) {
      expect(world.arabicName).toBeTruthy();
      expect(world.englishName).toBeTruthy();
      expect(world.purpose.ar).toBeTruthy();
      expect(world.purpose.en).toBeTruthy();
      expect(world.maturity).toBe("PREVIEW");
    }
  });

  it("routes only to a preview world and never returns executable commands or permissions", () => {
    expect(suggestWorld("أريد بيتًا لعائلتي")).toBe("pulse");
    expect(suggestWorld("أحتاج صيانة أملاكي")).toBe("habitat");
    expect(suggestWorld("عندي صفقة عقارية مع وسيط")).toBe("nexus");
    expect(suggestWorld("مهندس يريد محاكاة تصميم")).toBe("forge");
    expect(suggestWorld("تخطيط حضري للمدينة")).toBe("civic");
    expect(suggestWorld("my property maintenance")).toBe("habitat");
    expect(suggestWorld("")).toBeNull();
    expect(suggestWorld("طلب مبهم")).toBeNull();
    expect(Object.keys(getWorld("pulse"))).not.toContain("authorization");
  });

  it("exposes no real-world or backend truth states as gateway content", () => {
    for (const world of OULA_WORLDS) {
      expect(world.maturity).toBe("PREVIEW");
      expect(world.capabilities).toHaveLength(3);
      expect([world.purpose.ar, world.purpose.en].join(" ")).not.toMatch(/VERIFIED|LIVE/);
    }
  });
});
