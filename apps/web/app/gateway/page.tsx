"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import {
  OULA_WORLDS,
  getWorld,
  suggestWorld,
  type OulaLanguage,
  type OulaWorldId,
} from "../../lib/gateway";
import "./gateway.css";

const copy = {
  ar: {
    skip: "تجاوز إلى الفروع",
    studio: "بوابة عُلا",
    subtitle: "طبقة التشغيل والذكاء للعالم المبني",
    hero: "عالمٌ مبني يفهم الإنسان.",
    hint: "خمسة عوالم متخصصة، وعُلا واحد.",
    enter: "استكشف",
    choose: "اختر العالم الذي يناسبك",
    preview: "تصور تفاعلي — بيانات تجريبية",
    guide: "دليلك إلى عالم عُلا",
    assistant: "أخبرني بما تحتاج إليه وسأرشدك إلى الفرع المناسب",
    placeholder: "مثلاً: أريد إدارة أملاكي",
    suggest: "وجّهني",
    agentDemo: "إرشاد تجريبي محلي، وليس وكيلًا متصلًا ببياناتك.",
    notFound: "اختر أحد الفروع، أو صف حاجتك بكلمات أخرى.",
    selected: "الفرع المقترح لك:",
    works: "ماذا ستجد هنا؟",
    journey: "استكشف رحلة عُلا التجريبية",
    future: "هذه مساحة تصور حاليًا؛ الخدمات الحقيقية تخضع لإتاحة النظام والصلاحيات.",
    back: "العودة إلى الاختيار",
    change: "التبديل إلى الإنجليزية",
    footer: "المعلومة الموثقة ليست استنتاجًا، والتوصية ليست قرارًا ملزمًا.",
  },
  en: {
    skip: "Skip to the five worlds",
    studio: "OULA Gateway",
    subtitle: "Built World Intelligence & Operating Layer",
    hero: "A built world that understands people.",
    hint: "Five focused worlds. One OULA.",
    enter: "Explore",
    choose: "Choose the experience that fits you",
    preview: "Interactive concept — demonstration only",
    guide: "Your guide across OULA",
    assistant: "Tell me what you need, and I'll suggest the right world",
    placeholder: "For example: manage my property",
    suggest: "Find my world",
    agentDemo: "Local demonstration routing, not a live agent connected to your data.",
    notFound: "Select a world, or describe your need differently.",
    selected: "Suggested world:",
    works: "Inside this world",
    journey: "Explore the existing OULA demo journey",
    future: "This is a preview workspace. Real actions require availability and permissions.",
    back: "Choose another",
    change: "التبديل إلى العربية",
    footer: "Verified facts are not AI inferences; recommendations are not binding decisions.",
  },
};

function OulaMark({ small = false }: { small?: boolean }) {
  return (
    <span className={"og-mark" + (small ? " og-mark-small" : "")} aria-hidden="true">
      <span className="og-mark-ring" />
      <span className="og-mark-shine" />
    </span>
  );
}

function WorldGlyph({ id }: { id: OulaWorldId }) {
  const common = { fill: "none", stroke: "currentColor", strokeWidth: 1.7, strokeLinecap: "round" as const, strokeLinejoin: "round" as const };
  const paths: Record<OulaWorldId, React.ReactNode> = {
    pulse: <path d="M2 15h6l4-9 5 17 5-11 4 3h6" />,
    habitat: <><path d="M4 16 17 5l13 11M8 13v17h18V13M14 30V19h7v11" /></>,
    nexus: <><path d="M4 14 11 8l7 5 7-5 5 6-9 9-6-3-5 2-6-8Z" /><path d="m12 17 8 7m-11-4 8 7m2-15-6 4" /></>,
    forge: <><path d="m17 4 12 7v14l-12 7-12-7V11L17 4Z" /><path d="m5 11 12 7 12-7M17 18v14" /></>,
    civic: <><path d="M3 31h29M6 30V14h7v16M16 30V6h8v24M27 30V19h4v11M9 19h1m-1 5h1m10-13h1m-1 6h1m-1 6h1" /></>,
  };
  return <svg className="og-glyph" viewBox="0 0 34 34" aria-hidden="true"><g {...common}>{paths[id]}</g></svg>;
}

function Skyline() {
  const heights = [34, 52, 44, 67, 51, 77, 38, 58, 89, 49, 71, 44, 98, 60, 75, 53, 92, 66, 41, 83, 55, 74, 47, 63];
  return (
    <div className="og-skyline" aria-hidden="true">
      {heights.map((height, index) => (
        <span className="og-building" key={index} style={{ height: height + "%", opacity: 0.45 + (index % 4) * 0.12 }} />
      ))}
    </div>
  );
}

export default function OulaGateway() {
  const [lang, setLang] = useState<OulaLanguage>("ar");
  const [selected, setSelected] = useState<OulaWorldId | null>(null);
  const [request, setRequest] = useState("");
  const [message, setMessage] = useState("");
  const detailRef = useRef<HTMLDivElement>(null);
  const t = copy[lang];
  const world = selected ? getWorld(selected) : null;

  useEffect(() => {
    const root = document.documentElement;
    root.lang = lang;
    root.dir = lang === "ar" ? "rtl" : "ltr";
    return () => { root.lang = "ar"; root.dir = "rtl"; };
  }, [lang]);

  function selectWorld(id: OulaWorldId) {
    setSelected(id);
    setMessage("");
    // Selection stays lightweight and deliberate; no scrolling animation on mobile.
    requestAnimationFrame(() => detailRef.current?.focus({ preventScroll: true }));
  }

  function guide(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const match = suggestWorld(request);
    if (!match) {
      setMessage(t.notFound);
      setSelected(null);
      return;
    }
    setSelected(match);
    setMessage(t.selected + " " + getWorld(match).arabicName);
    requestAnimationFrame(() => detailRef.current?.focus({ preventScroll: true }));
  }

  return (
    <div className="og-root" dir={lang === "ar" ? "rtl" : "ltr"}>
      <a className="og-skip" href="#oula-worlds">{t.skip}</a>
      <div className="og-horizon" aria-hidden="true" />
      <Skyline />
      <header className="og-top">
        <span className="og-smallbrand">{t.studio} <span className="og-trademark">OULA</span></span>
        <div className="og-top-actions">
          <span className="og-preview">{t.preview}</span>
          <button className="og-lang" type="button" onClick={() => setLang(lang === "ar" ? "en" : "ar")} aria-label={t.change}>{lang === "ar" ? "EN" : "عربي"}</button>
          <Link className="og-return" href="/">{lang === "ar" ? "الرحلة التجريبية" : "Existing demo"}</Link>
        </div>
      </header>

      <main className="og-main">
        <section className="og-hero" aria-labelledby="og-title">
          <div className="og-celestial" aria-hidden="true"><i /><i /><i /></div>
          <OulaMark />
          <h1 id="og-title" className="og-title"><span>عُلا</span><span className="og-latin">OULA</span></h1>
          <p className="og-subtitle">{t.subtitle}</p>
          <p className="og-headline">{t.hero}</p>
          <p className="og-tagline">{t.hint}</p>
        </section>

        <section id="oula-worlds" className="og-worlds" aria-label={t.choose}>
          <h2 className="og-visually-hidden">{t.choose}</h2>
          {OULA_WORLDS.map((item) => (
            <button
              key={item.id}
              type="button"
              className={"og-portal og-" + item.id + (selected === item.id ? " og-active" : "")}
              onClick={() => selectWorld(item.id)}
              aria-pressed={selected === item.id}
              aria-label={item.arabicName + " / " + item.englishName + " — " + item.purpose[lang]}
              style={{ "--world-color": item.color } as React.CSSProperties}
            >
              <span className="og-portal-arch" aria-hidden="true"><span className="og-arch-halo" /></span>
              <span className="og-portal-inside">
                <span className="og-world-logo"><WorldGlyph id={item.id} /></span>
                <span className="og-world-name">{item.arabicName}</span>
                <span className="og-world-latin">{item.englishName}</span>
                <span className="og-world-summary">{item.purpose[lang]}</span>
                <span className="og-portal-action">{t.enter} <span aria-hidden="true">←</span></span>
              </span>
            </button>
          ))}
        </section>

        <section className="og-bottom" aria-label={t.guide}>
          <div className="og-agent">
            <div className="og-agent-orb" aria-hidden="true"><OulaMark small /></div>
            <div className="og-agent-label"><strong>{t.guide}</strong><small>{t.agentDemo}</small></div>
            <form className="og-agent-form" onSubmit={guide}>
              <label className="og-visually-hidden" htmlFor="og-agent-input">{t.assistant}</label>
              <input id="og-agent-input" value={request} onChange={event => setRequest(event.target.value)} placeholder={t.placeholder} maxLength={240} />
              <button type="submit">{t.suggest}</button>
            </form>
          </div>
          <div ref={detailRef} tabIndex={-1} className="og-selection" aria-live="polite">
            {world ? (
              <div className="og-selection-card" style={{ "--world-color": world.color } as React.CSSProperties}>
                <div className="og-selection-heading">
                  <span className="og-selection-icon"><WorldGlyph id={world.id} /></span>
                  <div><strong>{world.arabicName} <span dir="ltr">| {world.englishName}</span></strong><p>{world.purpose[lang]}</p></div>
                  <button type="button" onClick={() => setSelected(null)}>{t.back}</button>
                </div>
                <p className="og-selection-caption">{t.works}</p>
                <div className="og-capabilities">{world.capabilities.map((c, i) => <span key={i}>{c[lang]}</span>)}</div>
                <div className="og-selection-footer"><p>{t.future}</p><Link href="/">{t.journey} <span aria-hidden="true">↗</span></Link></div>
              </div>
            ) : (
              <p className="og-selection-empty">{message || t.assistant}</p>
            )}
          </div>
        </section>
      </main>
      <footer className="og-footer">{t.footer}</footer>
    </div>
  );
}
