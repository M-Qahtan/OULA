"use client";

import { Children, useId, useRef, useState, type ReactNode } from "react";
import { useSpatialExperience } from "./spatial-experience";

/** Native touch scrolling with visible, keyboard-operated alternatives to swiping. */
export function SpatialMetricStrip({ children }: { children: ReactNode }) {
  const { language, motionEnabled } = useSpatialExperience();
  const items = Children.toArray(children);
  const rail = useRef<HTMLDivElement>(null);
  const id = useId();
  const [current, setCurrent] = useState(0);
  const w = (ar: string, en: string) => (language === "ar" ? ar : en);
  function move(direction: number) {
    const index = Math.max(0, Math.min(items.length - 1, current + direction));
    rail.current?.children[index]?.scrollIntoView({
      behavior: motionEnabled ? "smooth" : "instant",
      inline: "center",
      block: "nearest",
    });
    setCurrent(index);
  }
  return (
    <section
      className="spatial-metric-strip"
      aria-label={w("بيانات العقار", "Property metrics")}
    >
      <div
        ref={rail}
        id={id}
        className="spatial-metric-rail grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-3"
        tabIndex={0}
        role="group"
        aria-label={w("بطاقات مؤشرات العقار", "Property metric cards")}
        onScroll={() => {
          if (!rail.current) return;
          const center = rail.current.getBoundingClientRect();
          const middle = center.x + center.width / 2;
          const distances = Array.from(rail.current.children).map((element) => {
            const box = element.getBoundingClientRect();
            return Math.abs(box.x + box.width / 2 - middle);
          });
          setCurrent(distances.indexOf(Math.min(...distances)));
        }}
      >
        {items}
      </div>
      <div className="spatial-metric-controls">
        <button
          type="button"
          aria-controls={id}
          aria-label={w("المؤشر السابق", "Previous metric")}
          onClick={() => move(-1)}
          disabled={current === 0}
        >
          {language === "ar" ? "→" : "←"}
        </button>
        <span aria-hidden="true">
          {current + 1} / {items.length}
        </span>
        <button
          type="button"
          aria-controls={id}
          aria-label={w("المؤشر التالي", "Next metric")}
          onClick={() => move(1)}
          disabled={current >= items.length - 1}
        >
          {language === "ar" ? "←" : "→"}
        </button>
      </div>
    </section>
  );
}
