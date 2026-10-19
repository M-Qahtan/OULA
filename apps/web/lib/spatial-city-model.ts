/**
 * Deterministic artwork, not surveyed geometry, a property boundary or GIS data.
 * No property facts, identifiers or coordinates are uploaded to a renderer service.
 */
export type CityBuilding = Readonly<{
  x: number;
  z: number;
  width: number;
  depth: number;
  height: number;
  warm: boolean;
}>;

export function createIllustrativeDistrict(): readonly CityBuilding[] {
  let seed = 170319;
  const random = () => {
    seed = (Math.imul(seed, 1664525) + 1013904223) >>> 0;
    return seed / 4294967296;
  };
  const buildings: CityBuilding[] = [];
  for (let row = -6; row <= 6; row++) {
    for (let column = -6; column <= 6; column++) {
      if (row === 0 || column === 0 || (row === -3 && column > -3)) continue;
      const distance = Math.hypot(column, row);
      if (distance > 7.2) continue;
      const height = 0.45 + random() * (distance < 4 ? 5.1 : 1.8);
      buildings.push({
        x: column * 1.65,
        z: row * 1.65,
        width: 0.62 + random() * 0.49,
        depth: 0.62 + random() * 0.49,
        height,
        warm: random() > 0.72,
      });
    }
  }
  return buildings;
}

export function spatialRenderBudget(width: number, pixelRatio: number) {
  const compact = width < 768;
  return {
    compact,
    // A single canvas, instanced buildings, bounded dust and no postprocessing chain.
    particles: compact ? 2200 : 5600,
    fps: compact ? 24 : 30,
    dpr: Math.min(Math.max(pixelRatio || 1, 1), compact ? 1.25 : 1.5),
  };
}
