import { describe, expect, it } from "vitest";
import {
  createIllustrativeDistrict,
  spatialRenderBudget,
} from "../lib/spatial-city-model";

describe("bounded procedural artwork", () => {
  it("generates repeatable, finite geometry without requiring a private record or network source", () => {
    const district = createIllustrativeDistrict();
    expect(district).toEqual(createIllustrativeDistrict());
    expect(district.length).toBeGreaterThan(50);
    expect(district.length).toBeLessThan(200);
    for (const building of district) {
      for (const n of [
        building.x,
        building.z,
        building.height,
        building.width,
        building.depth,
      ])
        expect(Number.isFinite(n)).toBe(true);
      expect(building.height).toBeGreaterThan(0);
      expect(building.width).toBeGreaterThan(0);
      expect(building.depth).toBeGreaterThan(0);
      expect(Math.hypot(building.x, building.z)).toBeLessThan(13);
    }
  });
  it("caps GPU resolution and ambient work even on high-DPR and wide-screen devices", () => {
    for (const width of [320, 390, 768, 1024, 1920, 3840]) {
      for (const ratio of [0, 1, 2, 4]) {
        const budget = spatialRenderBudget(width, ratio);
        expect(budget.dpr).toBeGreaterThanOrEqual(1);
        expect(budget.dpr).toBeLessThanOrEqual(width < 768 ? 1.25 : 1.5);
        expect(budget.particles).toBeLessThanOrEqual(5600);
        expect(budget.fps).toBeLessThanOrEqual(30);
      }
    }
  });
});
