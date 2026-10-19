import type { OulaWorldId } from "../lib/gateway";
export const GATEWAY_ART = "/visuals/reference/gateway-original.png";
export const portalGeometry: Record<
  OulaWorldId,
  { left: string; top: string; width: string; height: string }
> = {
  pulse: { left: "5.1%", top: "25.8%", width: "16.1%", height: "47.4%" },
  habitat: { left: "23.6%", top: "30.6%", width: "15%", height: "44.3%" },
  nexus: { left: "41.8%", top: "32.9%", width: "16.5%", height: "42.1%" },
  forge: { left: "60.8%", top: "31%", width: "15.5%", height: "43.7%" },
  civic: { left: "77.9%", top: "26.1%", width: "16.5%", height: "47.1%" },
};
const portalBox: Record<OulaWorldId, string> = {
  pulse: "91 242 226 328",
  habitat: "378 279 207 292",
  nexus: "656 298 226 273",
  forge: "948 283 213 289",
  civic: "1211 242 226 329",
};
const sealBox: Record<OulaWorldId, string> = {
  pulse: "160 274 85 80",
  habitat: "446 302 80 70",
  nexus: "725 303 82 74",
  forge: "997 300 88 73",
  civic: "1272 275 90 80",
};
export function MotherMark({ className = "" }: { className?: string }) {
  return (
    <svg className={className} viewBox="675 14 171 164" aria-hidden="true">
      <image href={GATEWAY_ART} width="1536" height="864" />
    </svg>
  );
}
export function WorldArt({
  world,
  seal = false,
  className = "",
}: {
  world: OulaWorldId;
  seal?: boolean;
  className?: string;
}) {
  return (
    <svg
      className={className}
      viewBox={seal ? sealBox[world] : portalBox[world]}
      aria-hidden="true"
    >
      <image href={GATEWAY_ART} width="1536" height="864" />
    </svg>
  );
}
