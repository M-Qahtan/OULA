"use client";

import { animate } from "framer-motion";
import { useLayoutEffect, useMemo, useRef } from "react";
import type { Language } from "./workspace-primitives";
import { useSpatialExperience } from "./spatial-experience";

/** Announce the exact server value once; interpolate only its decorative visual copy. */
export function AnimatedNumber({
  value,
  language,
  maximumFractionDigits = 2,
}: {
  value: number;
  language: Language;
  maximumFractionDigits?: number;
}) {
  const { motionEnabled } = useSpatialExperience();
  const output = useRef<HTMLSpanElement>(null);
  const current = useRef(value);
  const formatter = useMemo(
    () =>
      new Intl.NumberFormat(language === "ar" ? "ar-SA" : "en-US", {
        maximumFractionDigits,
      }),
    [language, maximumFractionDigits],
  );
  const formatted = Number.isFinite(value) ? formatter.format(value) : "—";
  useLayoutEffect(() => {
    if (!Number.isFinite(value)) return;
    if (!motionEnabled || current.current === value) {
      current.current = value;
      if (output.current) output.current.textContent = formatter.format(value);
      return;
    }
    if (output.current)
      output.current.textContent = formatter.format(current.current);
    const animation = animate(current.current, value, {
      duration: 0.65,
      ease: [0.22, 1, 0.36, 1],
      onUpdate: (next) => {
        current.current = next;
        if (output.current) output.current.textContent = formatter.format(next);
      },
      onComplete: () => {
        current.current = value;
        if (output.current)
          output.current.textContent = formatter.format(value);
      },
    });
    return () => animation.stop();
  }, [value, formatter, motionEnabled]);
  return (
    <span className="spatial-number" data-value={value}>
      <span className="spatial-sr-only">{formatted}</span>
      <span ref={output} aria-hidden="true">
        {formatted}
      </span>
    </span>
  );
}
