"use client";

import { useEffect, useRef, useState, type ReactNode } from "react";
import { MotionConfig, motion, useReducedMotion } from "framer-motion";
import type { AgentWorld } from "../lib/personal-agent";
import type { WorkspaceView } from "../lib/workspaces";
import { spatialTone, WORLD_TONES } from "../lib/spatial-theme";
import { MotherMark } from "./gateway-art";
import { Icon, type Language } from "./workspace-primitives";
import "../styles/spatial.css";

type Props = {
  world: AgentWorld;
  view?: WorkspaceView;
  language: Language;
  title: string;
  navigation: ReactNode;
  assistant: ReactNode;
  children: ReactNode;
  controls?: ReactNode;
  className?: string;
};

/** One persistent shell; only the drawer mode changes across breakpoints. */
export function SpatialShell({
  world,
  view,
  language,
  title,
  navigation,
  assistant,
  children,
  controls,
  className = "",
}: Props) {
  const w = (ar: string, en: string) => (language === "ar" ? ar : en);
  const dock = useRef<HTMLDialogElement>(null);
  const trigger = useRef<HTMLButtonElement>(null);
  const [compact, setCompact] = useState(false);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const reducedMotion = useReducedMotion();
  useEffect(() => {
    const media = window.matchMedia("(max-width: 1279px)");
    const sync = () => {
      if (media.matches || dock.current?.matches(":modal"))
        dock.current?.close();
      setDrawerOpen(false);
      setCompact(media.matches);
    };
    sync();
    media.addEventListener("change", sync);
    return () => media.removeEventListener("change", sync);
  }, []);
  useEffect(() => {
    if (!compact || !drawerOpen) return;
    const original = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.body.style.overflow = original;
    };
  }, [compact, drawerOpen]);
  function openAssistant() {
    dock.current?.showModal();
    setDrawerOpen(true);
  }
  function closeAssistant() {
    dock.current?.close();
    setDrawerOpen(false);
    trigger.current?.focus();
  }
  return (
    <MotionConfig reducedMotion="user">
      <div
        className={`oula-workspace spatial-app ${className}`}
        data-world={world}
        data-tone={spatialTone(world, view)}
        data-view={view}
        dir={language === "ar" ? "rtl" : "ltr"}
        lang={language}
      >
        <a className="workspace-skip" href="#workspace-main">
          {w("تجاوز إلى المحتوى", "Skip to content")}
        </a>
        <header className="spatial-header">
          <a
            className="spatial-brand"
            href={`/gateway?lang=${language}`}
            aria-label={w("العودة إلى بوابة عُلا", "Return to OULA gateway")}
          >
            <MotherMark />
            <span>
              <strong>
                O U L A <em>{world === "PRISM" ? "DECISION" : world}</em>
              </strong>
              <small>
                {w(
                  "عوالم مختلفة. ذكاء واحد.",
                  "MANY WORLDS. ONE INTELLIGENCE.",
                )}
              </small>
            </span>
          </a>
          <div className="spatial-header-context">
            <span className="spatial-live-dot" />
            {title}
            <small>{w("مساحتك، على طريقتك", "Your space, your way")}</small>
          </div>
          <div className="spatial-header-actions">
            {controls}
            <button
              ref={trigger}
              className="spatial-assistant-toggle glass-button"
              aria-controls="oula-assistant-dock"
              aria-haspopup="dialog"
              aria-label={w("افتح مساعد عُلا", "Open OULA assistant")}
              aria-expanded={drawerOpen}
              onClick={openAssistant}
            >
              <Icon name="intent" size={19} />
              <span>{w("عُلا معك", "OULA assistant")}</span>
            </button>
          </div>
        </header>
        <div className="spatial-layout">
          <aside
            className="workspace-rail spatial-navigation"
            dir={language === "ar" ? "rtl" : "ltr"}
            aria-label={w("التنقل الرئيسي", "Main navigation")}
          >
            <div className="spatial-nav-caption">
              <span className="spatial-live-dot" />
              {w("مساحات عالمك", "YOUR WORLD")}
            </div>
            {navigation}
            <a
              className="spatial-gateway-link"
              href={`/gateway?lang=${language}`}
            >
              <Icon name="overview" size={18} />
              {w("بوابة العوالم", "World gateway")}
              <span>↗</span>
            </a>
          </aside>
          <motion.main
            id="workspace-main"
            className="workspace-main spatial-main"
            dir={language === "ar" ? "rtl" : "ltr"}
            tabIndex={-1}
            initial={reducedMotion ? false : { opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.35 }}
          >
            {children}
          </motion.main>
          <dialog
            id="oula-assistant-dock"
            ref={dock}
            className="spatial-assistant-dock"
            open={!compact}
            aria-label={w("لوحة مساعد عُلا", "OULA assistant panel")}
            onKeyDown={(event) => {
              if (!compact || event.key !== "Tab") return;
              const targets = Array.from(
                event.currentTarget.querySelectorAll<HTMLElement>(
                  'button:not(:disabled), a[href], input:not(:disabled), select:not(:disabled), textarea:not(:disabled), [tabindex="0"]',
                ),
              ).filter((node) => node.getClientRects().length > 0);
              const first = targets[0];
              const last = targets[targets.length - 1];
              if (!first || !last) return;
              if (event.shiftKey && document.activeElement === first) {
                event.preventDefault();
                last.focus();
              } else if (!event.shiftKey && document.activeElement === last) {
                event.preventDefault();
                first.focus();
              }
            }}
            onCancel={() => {
              setDrawerOpen(false);
              trigger.current?.focus();
            }}
            onClose={() => {
              if (compact) {
                setDrawerOpen(false);
                trigger.current?.focus();
              }
            }}
          >
            <div className="spatial-drawer-top">
              <strong>{w("عُلا معك", "OULA at your side")}</strong>
              <button
                className="glass-button"
                onClick={closeAssistant}
                aria-label={w("إغلاق مساعد عُلا", "Close OULA assistant")}
              >
                ×
              </button>
            </div>
            <aside
              className="workspace-assistant"
              aria-label={w("مساعد عُلا", "OULA assistant")}
              dir={language === "ar" ? "rtl" : "ltr"}
            >
              {assistant}
            </aside>
          </dialog>
        </div>
        <div
          className={`spatial-world-glow ${WORLD_TONES[world]}`}
          aria-hidden="true"
        />
      </div>
    </MotionConfig>
  );
}
