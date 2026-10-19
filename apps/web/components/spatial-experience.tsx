"use client";

import { createContext, useContext, useState, type ReactNode } from "react";
import { MotionConfig, useReducedMotion } from "framer-motion";
import type { Language } from "./workspace-primitives";

type Experience = {
  language: Language;
  tone: string;
  motionEnabled: boolean;
  paused: boolean;
  toggleMotion: () => void;
};

const SpatialExperience = createContext<Experience>({
  language: "en",
  tone: "PULSE",
  motionEnabled: false,
  paused: false,
  toggleMotion: () => {},
});

/** Presentation preferences never enter the authority key or private data cache. */
export function SpatialExperienceProvider({
  language,
  tone,
  children,
}: {
  language: Language;
  tone: string;
  children: ReactNode;
}) {
  const reduced = useReducedMotion();
  const [paused, setPaused] = useState(false);
  return (
    <SpatialExperience.Provider
      value={{
        language,
        tone,
        paused,
        motionEnabled: reduced === false && !paused,
        toggleMotion: () => setPaused((value) => !value),
      }}
    >
      <MotionConfig reducedMotion={paused ? "always" : "user"}>
        {children}
      </MotionConfig>
    </SpatialExperience.Provider>
  );
}

export const useSpatialExperience = () => useContext(SpatialExperience);

export function SpatialMotionControl() {
  const { language, paused, toggleMotion } = useSpatialExperience();
  const label =
    language === "ar"
      ? paused
        ? "استئناف الحركة المحيطية"
        : "إيقاف الحركة المحيطية"
      : paused
        ? "Resume ambient motion"
        : "Pause ambient motion";
  return (
    <button
      className="glass-button spatial-motion-control"
      type="button"
      aria-label={label}
      aria-pressed={paused}
      title={label}
      onClick={toggleMotion}
    >
      <svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
        {paused ? (
          <path d="m9 5 11 7-11 7z" fill="currentColor" />
        ) : (
          <path d="M7 5h3v14H7zm7 0h3v14h-3z" fill="currentColor" />
        )}
      </svg>
    </button>
  );
}

export function SpatialAmbience() {
  const { motionEnabled } = useSpatialExperience();
  return (
    <div
      className="spatial-ambience"
      data-animated={motionEnabled}
      aria-hidden="true"
    >
      <i className="spatial-ambient-orb" />
      <i className="spatial-ambient-orb" />
      <i className="spatial-ambient-orb" />
      <div className="spatial-floor-reflection" />
    </div>
  );
}
