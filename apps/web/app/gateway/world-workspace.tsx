"use client";
import { useEffect, useRef } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { getWorld, OULA_WORLDS, type OulaWorldId } from "../../lib/gateway";
import {
  FAMILY_SECTIONS,
  familyHref,
  readFamilyContext,
  workspaceHref,
  type FamilyContext,
} from "../../lib/family-catalog";
import { roles } from "../../lib/workspaces";
import { SpatialShell } from "../../components/spatial-shell";
import { SpatialScene } from "../../components/spatial-primitives";
import { Icon, Wave } from "../../components/workspace-primitives";
import type { AgentWorld } from "../../lib/personal-agent";
import { MotherMark, WorldArt } from "../../components/gateway-art";

export default function WorldWorkspace({ worldId }: { worldId: OulaWorldId }) {
  const params = useSearchParams();
  const router = useRouter();
  const context = readFamilyContext(
    new URLSearchParams(params.toString()),
    worldId,
  );
  const world = getWorld(worldId);
  const sections = FAMILY_SECTIONS[worldId];
  const section =
    sections.find((s) => s.id === context.section) ??
    (!params.has("section") ? sections[0] : undefined);
  const branch =
    section?.branches.find((b) => b.id === context.branch) ??
    (!params.has("branch") ? section?.branches[0] : undefined);
  const current = { ...context, section: section?.id, branch: branch?.id };
  const href = workspaceHref(current);
  const lang = context.lang;
  const w = (ar: string, en: string) => (lang === "ar" ? ar : en);
  const detail = useRef<HTMLElement>(null);
  const previous = useRef("");
  const branchKey = (section?.id ?? "") + ":" + (branch?.id ?? "");
  useEffect(() => {
    document.documentElement.lang = lang;
    document.documentElement.dir = lang === "ar" ? "rtl" : "ltr";
    document.title = world.englishName + " · " + (branch?.name[lang] ?? "OULA");
  }, [lang, world, branch]);
  useEffect(() => {
    if (previous.current && previous.current !== branchKey)
      detail.current?.focus({ preventScroll: true });
    previous.current = branchKey;
  }, [branchKey]);
  function navigate(update: Partial<FamilyContext>) {
    router.push(familyHref({ ...current, ...update }), { scroll: false });
  }
  return (
    <SpatialShell
      world={worldId.toUpperCase() as AgentWorld}
      language={lang}
      title={world.purpose[lang]}
      className="ow-root"
      controls={
        <button
          className="glass-button"
          aria-label={w("Change to English", "التحويل إلى العربية")}
          onClick={() => navigate({ lang: lang === "ar" ? "en" : "ar" })}
        >
          {lang === "ar" ? "EN" : "عربي"}
        </button>
      }
      navigation={
        <nav
          className="workspace-nav"
          aria-label={w("عوالم عُلا", "OULA worlds")}
        >
          {OULA_WORLDS.map((item) => (
            <Link
              key={item.id}
              className={item.id === worldId ? "selected" : ""}
              aria-current={item.id === worldId ? "page" : undefined}
              href={familyHref({ world: item.id, role: context.role, lang })}
            >
              <WorldArt world={item.id} seal />
              <span>{lang === "ar" ? item.arabicName : item.englishName}</span>
            </Link>
          ))}
        </nav>
      }
      assistant={
        <>
          <div className="assistant-heading">
            <MotherMark />
            <div>
              <strong>{w("عُلا معك", "OULA at your side")}</strong>
              <small>
                {w("دليلك إلى مساحة تناسبك", "Find your path, your way")}
              </small>
            </div>
          </div>
          <div className="assistant-orb" aria-hidden="true">
            <div />
            <WorldArt world={worldId} seal />
          </div>
          <div className="assistant-wave">
            <Wave active={false} />
          </div>
          <p className="assistant-intro">
            {w(
              "عالمك واسع. ابدأ بما يهمك الآن، وسنكمل من حيث تختار.",
              "A world of possibilities. Start with what matters to you, then follow your own path.",
            )}
          </p>
          <div className="assistant-context">
            <span>{w("مسارك الآن", "YOUR PATH")}</span>
            <strong>{section?.name[lang] ?? world.englishName}</strong>
            <small>
              {branch?.name[lang] ??
                w("اختر قسمًا للبدء", "Choose a section to begin")}
            </small>
          </div>
          <div className="spatial-assistant-summary">
            <h3>{w("قبل خطوتك التالية", "Before your next step")}</h3>
            <p>
              {section?.requires[lang] ??
                w(
                  "تُعرض الخدمات بحسب صلاحيات حسابك.",
                  "Services follow your account permissions.",
                )}
            </p>
            <Link className="glass-button" href={"/gateway?lang=" + lang}>
              {w("اسأل عُلا عن المسار", "Find a path with OULA")}
              <Icon name="arrow" size={16} />
            </Link>
          </div>
          <div className="assistant-boundary">
            <Icon name="guardian" size={17} />
            <p>
              {w(
                "دليل تنقل داخل عُلا. الإجراءات والبيانات تحتاج جلسة مصرحًا بها، والتوصية لا تنفذ قرارًا عنك.",
                "Local navigation guidance. Data and actions require an authorized session. Suggestions never execute your decisions.",
              )}
            </p>
          </div>
        </>
      }
    >
      <div className="spatial-hero-heading">
        <div>
          <span className="spatial-kicker">OULA / {world.englishName}</span>
          <h1>
            {world.arabicName} <small dir="ltr">{world.englishName}</small>
          </h1>
          <p>{world.purpose[lang]}</p>
        </div>
        <div className="spatial-world-seal">
          <WorldArt world={worldId} seal />
        </div>
      </div>
      <SpatialScene
        language={lang}
        title={world.purpose[lang]}
        subtitle={w(
          "كل ما تحتاجه في عالم واحد، بخطوة واضحة تبدأ منك.",
          "Everything you need in one world, with a clear next step that starts with you.",
        )}
      >
        <a className="glass-button" href="#ow-sections-title">
          {w("اكتشف مساحتك", "Explore your space")}
          <Icon name="arrow" size={17} />
        </a>
      </SpatialScene>
      <div className="spatial-world-role">
        <label htmlFor="world-role">
          {w("أستكشف بصفتي", "I am exploring as")}
        </label>
        <select
          id="world-role"
          value={context.role}
          onChange={(e) => navigate({ role: e.target.value })}
        >
          {roles.map((role) => (
            <option key={role.id} value={role.id}>
              {role.name[lang]}
            </option>
          ))}
        </select>
      </div>
      <p className="spatial-stage-note">
        {w(
          "دورك يخصص العرض. الوصول إلى البيانات تحدده صلاحيات حسابك.",
          "Your role tailors the view. Account permissions determine data access.",
        )}
      </p>
      <section
        className="spatial-world-sections"
        aria-labelledby="ow-sections-title"
      >
        <div className="spatial-section-title">
          <h2 id="ow-sections-title">
            {w("مساحات عالمك", "Spaces in your world")}
          </h2>
          <small>{w("اختر ما تحتاجه", "Choose what you need")}</small>
        </div>
        <div className="spatial-world-section-tabs">
          {sections.map((item) => (
            <button
              key={item.id}
              className={
                "ow-task" + (item.id === section?.id ? " ow-task-active" : "")
              }
              aria-pressed={item.id === section?.id}
              aria-controls="ow-task-details"
              onClick={() =>
                navigate({ section: item.id, branch: item.branches[0]?.id })
              }
            >
              <strong>{item.name[lang]}</strong>
              <small>
                {item.branches.some((b) => b.target)
                  ? w("خدمات مساحة العمل", "Workspace services")
                  : w("ضمن خطة التطوير", "Planned capability")}
              </small>
            </button>
          ))}
        </div>
      </section>
      {!section && (
        <p className="ow-invalid" role="status">
          {w(
            "القسم المطلوب غير موجود في هذا العالم. اختر قسمًا من القائمة.",
            "This section does not belong to this world. Choose one from the list.",
          )}
        </p>
      )}
      {section && (
        <section
          className="ow-section-detail"
          aria-labelledby="ow-active-section-title"
        >
          <h2
            className="spatial-world-section-title"
            id="ow-active-section-title"
          >
            {section.name[lang]}
          </h2>
          <p className="spatial-world-section-copy">
            {section.description[lang]}
          </p>
          <div
            className="ow-branch-grid"
            aria-label={w("فروع القسم", "Section branches")}
          >
            {section.branches.map((item) => (
              <Link
                key={item.id}
                className={
                  "ow-branch" +
                  (item.id === branch?.id ? " ow-branch-active" : "")
                }
                href={familyHref({
                  ...context,
                  section: section.id,
                  branch: item.id,
                })}
                scroll={false}
                aria-current={item.id === branch?.id ? "page" : undefined}
              >
                <Icon name={item.target?.view ?? "studio"} size={22} />
                <span>
                  <strong>{item.name[lang]}</strong>
                  <small>
                    {item.target
                      ? w("افتح المسار المتصل", "Connected workspace path")
                      : w(
                          "استكشف النطاق المستهدف",
                          "Explore the planned scope",
                        )}
                  </small>
                </span>
              </Link>
            ))}
          </div>
          <article
            id="ow-task-details"
            className="ow-task-detail"
            ref={detail}
            tabIndex={-1}
          >
            {branch ? (
              <>
                <div className="ow-detail-heading">
                  <span
                    className={
                      "ow-status" + (branch.target ? " ow-connected" : "")
                    }
                  >
                    {branch.target
                      ? w("خدمات مساحة العمل", "Workspace services")
                      : w("قدرة مستقبلية", "Planned capability")}
                  </span>
                  <small>
                    {section.name[lang]} / {branch.name[lang]}
                  </small>
                </div>
                <h3>{branch.name[lang]}</h3>
                <dl className="ow-requirements">
                  <div>
                    <dt>{w("المسار الذي ستبدأه", "Your next task")}</dt>
                    <dd>{section.description[lang]}</dd>
                  </div>
                  <div>
                    <dt>{w("قبل أن تبدأ", "Before you start")}</dt>
                    <dd>{section.requires[lang]}</dd>
                  </div>
                </dl>
                {href ? (
                  <div className="ow-action">
                    <Link className="ow-primary" href={href}>
                      {w(
                        "افتح المسار في مساحة العمل",
                        "Open path in workspace",
                      )}
                      <Icon name="arrow" size={16} />
                    </Link>
                    <small>
                      {w(
                        "سيطلب النظام الدخول عند الحاجة، ثم يعرض ما يسمح به حسابك.",
                        "Sign in when needed, then use only what your account permits.",
                      )}
                    </small>
                  </div>
                ) : (
                  <div className="ow-planned">
                    <strong>
                      {w("النطاق متاح للاستكشاف", "Scope available to explore")}
                    </strong>
                    <p>
                      {w(
                        "يشرح هذا الفرع المهمة ومتطلباتها. خدمته التشغيلية غير مفعلة في هذه النسخة.",
                        "This branch explains the task and requirements. Its operational service is not enabled in this version.",
                      )}
                    </p>
                  </div>
                )}
              </>
            ) : (
              <p role="status">
                {w(
                  "الفرع المطلوب غير موجود في هذا القسم.",
                  "The requested branch does not belong to this section.",
                )}
              </p>
            )}
          </article>
        </section>
      )}
      <footer className="workspace-footer">
        <MotherMark />
        <span>
          {w(
            "المعلومة بمصدرها. التوصية بحدودها. القرار بإرادتك.",
            "Facts with sources. Recommendations with limits. Decisions under your control.",
          )}
        </span>
      </footer>
    </SpatialShell>
  );
}
