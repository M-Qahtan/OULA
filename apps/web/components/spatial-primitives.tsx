"use client";

import { motion, useReducedMotion } from "framer-motion";
import type { ReactNode } from "react";
import type { Language } from "./workspace-primitives";
import { Icon } from "./workspace-primitives";

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
  const reduced = useReducedMotion();
  return (
    <section
      className={`spatial-scene ${compact ? "spatial-scene-compact" : ""}`}
      aria-label={title}
    >
      <motion.div
        className="spatial-scene-image"
        aria-hidden="true"
        initial={false}
        animate={{ scale: reduced ? 1 : 1.035 }}
        transition={{ duration: 12, ease: "easeOut" }}
      />
      <div className="spatial-scene-shade" aria-hidden="true" />
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
      <div className="spatial-scene-orbit" aria-hidden="true">
        <i />
        <i />
        <i />
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
      className={`spatial-glass backdrop-blur-xl bg-slate-950/40 border border-white/10 shadow-[inset_0_1px_1px_rgba(255,255,255,0.05)] ${className}`}
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
  return (
    <div className="spatial-metric">
      <span className="spatial-metric-icon">
        <Icon name={icon} size={22} />
      </span>
      <span className="spatial-metric-label">{label}</span>
      <strong>{value}</strong>
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
