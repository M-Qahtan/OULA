"use client";
import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { OULA_WORLDS, type OulaLanguage } from "../../lib/gateway";
import {
  familyHref,
  readFamilyContext,
  searchFamilies,
} from "../../lib/family-catalog";
import {
  GATEWAY_ART,
  MotherMark,
  WorldArt,
  portalGeometry,
} from "../../components/gateway-art";
import { useOulaSession } from "../../lib/use-oula-session";
import { sessionContextKey } from "../../lib/session-state";
import { OulaExperienceLensProof } from "../../components/oula-experience-lens-proof";
import "./gateway.css";

export default function OulaGateway() {
  const [lang, setLang] = useState<OulaLanguage>("ar");
  const { session } = useOulaSession();
  const key = sessionContextKey(session);
  const [fields, setFields] = useState({
    context: key,
    request: "",
    search: "",
  });
  const request = fields.context === key ? fields.request : "";
  const query = fields.context === key ? fields.search : "";
  const dialog = useRef<HTMLDialogElement>(null);
  const searchInput = useRef<HTMLInputElement>(null);
  const opener = useRef<HTMLElement | null>(null);
  const w = (ar: string, en: string) => (lang === "ar" ? ar : en);
  const results = searchFamilies(query);
  useEffect(() => {
    setFields({ context: key, request: "", search: "" });
    dialog.current?.close();
  }, [key]);
  useEffect(() => {
    setLang(
      new URLSearchParams(location.search).get("lang") === "en" ? "en" : "ar",
    );
  }, []);
  useEffect(() => {
    document.documentElement.lang = lang;
    document.documentElement.dir = lang === "ar" ? "rtl" : "ltr";
  }, [lang]);
  function field(name: "request" | "search", value: string) {
    setFields((previous) => ({
      context: key,
      request: previous.context === key ? previous.request : "",
      search: previous.context === key ? previous.search : "",
      [name]: value,
    }));
  }
  function openSearch(value = "") {
    opener.current =
      document.activeElement instanceof HTMLElement
        ? document.activeElement
        : null;
    field("search", value);
    dialog.current?.showModal();
    searchInput.current?.focus();
  }
  function changeLanguage() {
    const next = lang === "ar" ? "en" : "ar";
    setLang(next);
    const params = new URLSearchParams(location.search);
    params.set("lang", next);
    history.replaceState(null, "", "/gateway?" + params);
  }
  return (
    <div
      className="og-root"
      dir={lang === "ar" ? "rtl" : "ltr"}
      data-session-status={session.status}
    >
      <a className="og-skip" href="#oula-worlds">
        {w("تجاوز إلى الفروع", "Skip to the five worlds")}
      </a>
      <h1 className="og-visually-hidden">
        عُلا | OULA — {w("بوابة العوالم الخمسة", "Five connected worlds")}
      </h1>
      <main className="og-reference-board">
        <img
          className="og-reference-image"
          src={GATEWAY_ART}
          alt=""
          width="1536"
          height="864"
          fetchPriority="high"
        />
        <header className="og-top-actions">
          <button
            className="og-icon"
            aria-label={w("ابحث في عُلا", "Search OULA")}
            onClick={() => openSearch()}
          >
            ⌕
          </button>
          <button
            className="og-lang"
            aria-label={w("التبديل إلى الإنجليزية", "التبديل إلى العربية")}
            onClick={changeLanguage}
          >
            {lang === "ar" ? "EN" : "عربي"}
          </button>
          <Link
            className="og-account"
            href={"/workspace?connection=server&lang=" + lang}
          >
            {w("مساحة العمل", "My workspace")} <span aria-hidden="true">↗</span>
          </Link>
        </header>
        <section className="og-mobile-intro">
          <MotherMark className="og-mobile-mark" />
          <p className="og-overline">
            O U L A <span>عُلا</span>
          </p>
          <h2>
            {w("عوالم مختلفة. ذكاء واحد.", "Many worlds. One intelligence.")}
          </h2>
          <p>
            {w(
              "اختر ما تحتاجه، وليبدأ مسارك.",
              "Choose what you need and start your path.",
            )}
          </p>
        </section>
        <nav
          id="oula-worlds"
          className="og-worlds"
          aria-label={w("اختر العالم الذي يناسبك", "Choose your world")}
        >
          {OULA_WORLDS.map((world) => (
            <Link
              key={world.id}
              className={"og-portal og-" + world.id}
              href={"/gateway/" + world.id + "?lang=" + lang}
              style={
                {
                  ...portalGeometry[world.id],
                  "--world-color": world.color,
                } as React.CSSProperties
              }
              aria-label={
                world.arabicName +
                " / " +
                world.englishName +
                " — " +
                world.purpose[lang]
              }
            >
              <WorldArt world={world.id} seal className="og-mobile-world-art" />
              <span className="og-mobile-world-name">
                <strong>
                  {lang === "ar" ? world.arabicName : world.englishName}
                </strong>
                <small>{world.purpose[lang]}</small>
              </span>
              <span className="og-portal-action">
                {w("استكشف", "Enter")}{" "}
                {lang === "ar" ? world.arabicName : world.englishName}{" "}
                <span aria-hidden="true">→</span>
              </span>
            </Link>
          ))}
        </nav>
        <form
          noValidate
          className="og-agent-form"
          onSubmit={(event) => {
            event.preventDefault();
            openSearch(request);
          }}
        >
          <span className="og-orb" aria-hidden="true">
            ◉
          </span>
          <label className="og-visually-hidden" htmlFor="og-request">
            {w(
              "أخبرني بما تحتاج إليه داخل عُلا",
              "Tell me what you need inside OULA",
            )}
          </label>
          <input
            id="og-request"
            value={request}
            maxLength={240}
            autoComplete="off"
            onChange={(event) => field("request", event.target.value)}
            placeholder={w(
              "اسأل عُلا… دليلك إلى عالمك",
              "Ask OULA… find your world",
            )}
          />
          <button aria-label={w("وجّهني", "Find my world")}>➤</button>
        </form>
        <span className="og-art-caption">
          {w(
            "بوابة عُلا · الخدمات حسب إتاحة النظام وصلاحيات حسابك",
            "OULA gateway · service availability and account permissions apply",
          )}
        </span>
      </main>
      <dialog
        className="og-search-dialog"
        ref={dialog}
        aria-labelledby="og-search-title"
        onClick={(event) => {
          if (event.target === dialog.current) dialog.current.close();
        }}
        onClose={() => requestAnimationFrame(() => opener.current?.focus())}
      >
        <div className="og-search-top">
          <div>
            <p className="og-overline">OULA · {w("دليلك", "YOUR GUIDE")}</p>
            <h2 id="og-search-title">
              {w("إلى أين تريد أن تصل؟", "Where would you like to go?")}
            </h2>
          </div>
          <button
            className="og-icon"
            aria-label={w("إغلاق البحث", "Close search")}
            onClick={() => dialog.current?.close()}
          >
            ×
          </button>
        </div>
        <label htmlFor="og-search">
          {w("ابحث عن عالم أو قسم أو مهمة", "Search worlds, sections or tasks")}
        </label>
        <input
          id="og-search"
          ref={searchInput}
          value={query}
          maxLength={240}
          autoComplete="off"
          onChange={(event) => field("search", event.target.value)}
          placeholder={w(
            "صيانة، جواز عقار، وصل…",
            "Maintenance, passport, Nexus…",
          )}
        />
        <p className="og-search-note">
          {w(
            "إرشاد محلي لاختيار المسار. لا تُرسل عبارتك إلى نموذج خارجي.",
            "Local route guidance. Your query is not sent to an external model.",
          )}
        </p>
        <div className="og-search-results" aria-live="polite">
          {results.map((item) => (
            <Link
              key={item.world.id + item.section.id + item.branch.id}
              href={familyHref({
                ...readFamilyContext(
                  new URLSearchParams({ lang }),
                  item.world.id,
                ),
                section: item.section.id,
                branch: item.branch.id,
              })}
              style={
                { "--world-color": item.world.color } as React.CSSProperties
              }
            >
              <small>
                {lang === "ar" ? item.world.arabicName : item.world.englishName}{" "}
                / {item.section.name[lang]}
              </small>
              <strong>{item.branch.name[lang]}</strong>
              <span>
                {item.branch.target
                  ? w("خدمات مساحة العمل", "Workspace services")
                  : w("ضمن خطة التطوير", "Planned capability")}
              </span>
            </Link>
          ))}
          {query.trim() && !results.length && (
            <p>
              {w(
                "لم نجد مسارًا مطابقًا. جرّب وصفًا آخر أو اختر عالمًا.",
                "No matching path. Try another description or choose a world.",
              )}
            </p>
          )}
        </div>
      </dialog>
      {process.env.NEXT_PUBLIC_OULA_DEV_AUTH === "true" &&
        process.env.NEXT_PUBLIC_OULA_API_BASE_URL && (
          <OulaExperienceLensProof
            language={lang}
            apiBaseUrl={process.env.NEXT_PUBLIC_OULA_API_BASE_URL}
          />
        )}
    </div>
  );
}
