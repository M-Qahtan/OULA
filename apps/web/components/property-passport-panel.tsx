"use client";

import { useId, useState } from "react";
import { motion } from "framer-motion";
import type { PropertyPassport } from "../lib/api";
import { GlassPanel, SpatialMetric, SpatialScene } from "./spatial-primitives";
import { Icon, type Language } from "./workspace-primitives";
import { SpatialMetricStrip } from "./spatial-metric-strip";
import { useSpatialExperience } from "./spatial-experience";

export function PropertyPassportPanel({
  passport,
  language,
  onDecision,
}: {
  passport: PropertyPassport;
  language: Language;
  onDecision: () => void;
}) {
  const [visible, setVisible] = useState(6);
  const ringId = useId();
  const { motionEnabled } = useSpatialExperience();
  const w = (ar: string, en: string) => (language === "ar" ? ar : en);
  const number = (n: number) =>
    new Intl.NumberFormat(language === "ar" ? "ar-SA" : "en-US", {
      maximumFractionDigits: 2,
    }).format(n);
  const date = (s: string) =>
    !Number.isFinite(new Date(s).getTime())
      ? w("تاريخ غير متاح", "Date unavailable")
      : new Intl.DateTimeFormat(language === "ar" ? "ar-SA" : "en-GB", {
          dateStyle: "medium",
          timeStyle: "short",
          timeZone: "Asia/Riyadh",
        }).format(new Date(s));
  const verified = passport.facts.filter(
    (f) => f.truthStatus === "VERIFIED",
  ).length;
  const percent = passport.verifiedFactCoverage * 100;
  return (
    <div className="spatial-passport" data-testid="spatial-passport">
      <div className="spatial-passport-head">
        <div>
          <span className="spatial-kicker">PROPERTY PASSPORT</span>
          <h2>
            {w("هوية المكان. ذاكرة الأصل.", "Place identity. Asset memory.")}
          </h2>
          <p className="record-reference" dir="ltr">
            {passport.propertyId}
          </p>
        </div>
        <div className="spatial-trust-ring">
          <svg
            viewBox="0 0 80 80"
            role="img"
            aria-label={w(
              `تغطية المعلومات الموسومة محققة ${number(percent)} بالمئة`,
              `Coverage of facts marked verified: ${number(percent)} percent`,
            )}
          >
            <defs>
              <linearGradient id={ringId} x1="0" x2="1" y1="0" y2="1">
                <stop stopColor="var(--world-accent)" />
                <stop offset="1" stopColor="var(--os-gold)" />
              </linearGradient>
            </defs>
            <circle
              cx="40"
              cy="40"
              r="33"
              fill="none"
              stroke="var(--os-line)"
              strokeWidth="5"
            />
            <motion.circle
              cx="40"
              cy="40"
              r="33"
              fill="none"
              stroke={`url(#${ringId})`}
              strokeWidth="5"
              pathLength="100"
              strokeDasharray={`${percent} 100`}
              initial={false}
              animate={{ strokeDasharray: `${percent} 100` }}
              transition={{ duration: motionEnabled ? 0.65 : 0 }}
              strokeLinecap="round"
              transform="rotate(-90 40 40)"
            />
            <text
              x="40"
              y="45"
              textAnchor="middle"
              fill="currentColor"
              fontSize="16"
              fontFamily="DM Sans, sans-serif"
            >
              {number(percent)}%
            </text>
          </svg>
          <span>
            <strong>{w("تغطية التحقق", "Verified coverage")}</strong>
            {w(
              "وسم المعلومات في المصدر، وليس إثبات ملكية",
              "Source truth labels, not proof of title",
            )}
          </span>
        </div>
      </div>
      <SpatialMetricStrip>
        <SpatialMetric
          label={w("نوع الأصل", "Asset type")}
          value={passport.assetType ?? w("غير مسجل", "Not recorded")}
          icon="intake"
        />
        <SpatialMetric
          label={w("الحي", "District")}
          value={passport.district ?? "—"}
          icon="location"
        />
        <SpatialMetric
          label={w("غرف النوم", "Bedrooms")}
          value={passport.bedrooms ?? "—"}
          icon="overview"
        />
        <SpatialMetric
          label={w("السعر المعلن", "Asking price")}
          value={passport.askingPrice ?? "—"}
          detail={w(
            "العملة غير محددة في السجل",
            "Currency not specified in this record",
          )}
          icon="deal"
        />
      </SpatialMetricStrip>
      <SpatialScene
        language={language}
        title={
          passport.district ?? w("عقارك، بصورة أوضح", "Your property, in focus")
        }
        subtitle={w(
          "استكشف المشهد. وافهم المعلومات من مصادرها.",
          "Explore the scene. Understand every fact at its source.",
        )}
      />
      <div className="spatial-passport-grid">
        <GlassPanel>
          <div className="spatial-section-title">
            <h3>{w("المعلومات والأدلة", "Facts and evidence")}</h3>
            <small>
              {number(passport.facts.length)}{" "}
              {w("معلومة مصدرية", "source-linked facts")}
            </small>
          </div>
          {passport.facts.length ? (
            <>
              <div className="fact-list">
                {passport.facts.slice(0, visible).map((fact) => (
                  <article key={fact.id}>
                    <div>
                      <strong>{fact.key}</strong>
                      <span className="truth-badge">{fact.truthStatus}</span>
                    </div>
                    <code>{fact.valueJson}</code>
                    <small>
                      {w("المصدر", "Source")}: {fact.sourceType}
                    </small>
                    {fact.validFrom && (
                      <small>
                        {w("ساري من", "Valid from")}: {date(fact.validFrom)}
                      </small>
                    )}
                  </article>
                ))}
              </div>
              {visible < passport.facts.length && (
                <button
                  className="glass-button"
                  onClick={() => setVisible((n) => n + 6)}
                >
                  {w("عرض معلومات إضافية", "Show more facts")}
                </button>
              )}
            </>
          ) : (
            <p className="spatial-stage-note">
              {w(
                "لا توجد معلومات مصدرية مسجلة بعد.",
                "No source-linked facts are recorded yet.",
              )}
            </p>
          )}
        </GlassPanel>
        <GlassPanel>
          <div className="spatial-section-title">
            <h3>{w("سجل المعرفة", "Knowledge record")}</h3>
            <Icon name="guardian" size={19} />
          </div>
          <ul className="spatial-ledger">
            <li>
              <span>
                <Icon name="passport" size={16} />
              </span>
              <div>
                <strong>
                  {w("معلومات موسومة محققة", "Facts marked verified")}
                </strong>
                <p>
                  {number(verified)} / {number(passport.facts.length)} ·{" "}
                  {w("بحسب حالة المصدر", "from source truth statuses")}
                </p>
              </div>
            </li>
            <li>
              <span>
                <Icon name="location" size={16} />
              </span>
              <div>
                <strong>{w("آخر قراءة للجواز", "Passport generated")}</strong>
                <p>
                  {date(passport.generatedAt)} ·{" "}
                  {w("توقيت الرياض", "Riyadh time")}
                </p>
              </div>
            </li>
            <li>
              <span>
                <Icon name="guardian" size={16} />
              </span>
              <div>
                <strong>{w("حالة الأصل", "Asset snapshot")}</strong>
                <p>
                  {passport.latestState
                    ? `${passport.latestState.stateBasis} · ${w("نسخة", "Version")} ${number(passport.latestState.version)}`
                    : w(
                        "لا توجد لقطة حالة مسجلة",
                        "No asset snapshot recorded",
                      )}
                </p>
              </div>
            </li>
          </ul>
          <p className="spatial-stage-note">
            {w(
              "نسبة التغطية ليست إثبات ملكية أو تقييمًا معتمدًا. حالات المعرفة من المصدر.",
              "Coverage is not proof of title or a certified valuation. Truth statuses come from the source.",
            )}
          </p>
          <button className="gold-button" onClick={onDecision}>
            {w("انتقل لدعم القرار", "Continue to decision support")}
            <Icon name="arrow" size={17} />
          </button>
        </GlassPanel>
      </div>
    </div>
  );
}
