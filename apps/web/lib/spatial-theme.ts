import type { AgentWorld } from "./personal-agent";
import type { WorkspaceView } from "./workspaces";

/** Palette values are owned by spatial.css; these static recipes are Tailwind-scannable. */
export const WORLD_TONES = {
  PULSE: "text-cyan-400 border-cyan-500/20",
  HABITAT: "text-teal-400 border-teal-500/20",
  NEXUS: "text-amber-400 border-amber-500/20",
  PRISM: "text-amber-400 border-amber-500/20",
  FORGE: "text-blue-400 border-blue-500/20",
  CIVIC: "text-purple-400 border-purple-500/20",
} as const satisfies Record<AgentWorld, string>;

export function spatialTone(world: AgentWorld, view?: WorkspaceView) {
  return view === "deal" || view === "decision" ? "AMBER" : world;
}
