"use client";

import { motion } from "framer-motion";
import type { ReactNode } from "react";
import type { Language } from "./workspace-primitives";
import { Icon } from "./workspace-primitives";
import { SpatialEnvironment } from "./spatial-environment";
import { useSpatialExperience } from "./spatial-experience";
import { AnimatedNumber } from "./animated-number";

export function SpatialScene({
  language,
  title,
  subtitle,
  children,
  compact = false,
}: {
  language: Language;
  title: string;
  subtitle: string;
  children?: ReactNode;
  compact?: boolean;
}) {
  return (
    <section
      className={`spatial-scene ${compact ? "spatial-scene-compact" : ""}`}
      aria-label={title}
    >
      <SpatialEnvironment language={language} />
      <div className="spatial-scene-top">
        <span className="spatial-scene-badge">
          <Icon name="location" size={15} />
          {language === "ar"
            ? "الرياض · بداية الحكاية"
            : "RIYADH · THE BEGINNING"}
        </span>
        <span className="spatial-scene-label">
          {language === "ar" ? "مشهد تصوري" : "ILLUSTRATIVE SCENE"}
        </span>
      </div>
      <div className="spatial-scene-copy">
        <span className="spatial-kicker">OULA · BUILT WORLD INTELLIGENCE</span>
        <h2>{title}</h2>
        <p>{subtitle}</p>
        {children}
      </div>
      <div className="spatial-scene-foot" aria-hidden="true">
        <span>HUMAN · PLACE · POSSIBILITY</span>
        <span>OULA / RIYADH</span>
      </div>
    </section>
  );
}

export function GlassPanel({
  children,
  className = "",
}: {
  children: ReactNode;
  className?: string;
}) {
  return (
    <section
      className={`spatial-glass backdrop-blur-2xl bg-neutral-950/40 border border-white/10 shadow-[inset_0_1px_3px_rgba(255,255,255,0.06),_0_25px_50px_-12px_rgba(0,0,0,0.8)] ${className}`}
    >
      {children}
    </section>
  );
}

export function SpatialMetric({
  label,
  value,
  detail,
  icon = "passport",
}: {
  label: string;
  value: ReactNode;
  detail?: string;
  icon?: string;
}) {
  const { language } = useSpatialExperience();
  return (
    <div className="spatial-metric">
      <span className="spatial-metric-icon">
        <Icon name={icon} size={22} />
      </span>
      <span className="spatial-metric-label">{label}</span>
      <motion.strong layout="position">
        {typeof value === "number" ? (
          <AnimatedNumber value={value} language={language} />
        ) : (
          value
        )}
      </motion.strong>
      {detail && <small>{detail}</small>}
    </div>
  );
}

export function SpatialSkeleton({
  language,
  label,
  cards = 4,
}: {
  language: Language;
  label?: string;
  cards?: number;
}) {
  return (
    <div
      className="spatial-loading"
      role="status"
      aria-label={
        label ?? (language === "ar" ? "جارٍ قراءة السجل" : "Reading record")
      }
    >
      <span>
        {label ??
          (language === "ar"
            ? "جارٍ قراءة السجل من مساحتك…"
            : "Reading your workspace record…")}
      </span>
      <div
        className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-3"
        aria-hidden="true"
      >
        {Array.from({ length: cards }, (_, i) => (
          <div key={i} className="spatial-skeleton">
            <i />
            <i />
            <i />
          </div>
        ))}
      </div>
    </div>
  );
}
