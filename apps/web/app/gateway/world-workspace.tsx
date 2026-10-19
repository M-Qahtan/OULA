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
    <div
      className="ow-root"
      dir={lang === "ar" ? "rtl" : "ltr"}
      data-world={worldId}
      style={{ "--ow-accent": world.color } as React.CSSProperties}
    >
      <a className="ow-skip" href="#ow-content">
        {w("تجاوز إلى المهام", "Skip to tasks")}
      </a>
      <div className="ow-ambient" aria-hidden="true" />
      <header className="ow-header">
        <Link
          className="ow-master"
          href={"/gateway?lang=" + lang}
          aria-label={w("العودة إلى بوابة عُلا", "Return to OULA gateway")}
        >
          <MotherMark />
          <span>
            OULA<small>عُلا</small>
          </span>
        </Link>
        <nav
          className="ow-breadcrumb"
          aria-label={w("مسارك في عُلا", "Your OULA path")}
        >
          <Link href={"/gateway?lang=" + lang}>
            {w("العوالم الخمسة", "Five worlds")}
          </Link>
          <span aria-hidden="true">/</span>
          <strong>
            {lang === "ar" ? world.arabicName : world.englishName}
          </strong>
        </nav>
        <button
          className="ow-lang"
          aria-label={w("Change to English", "التحويل إلى العربية")}
          onClick={() => navigate({ lang: lang === "ar" ? "en" : "ar" })}
        >
          {lang === "ar" ? "EN" : "عربي"}
        </button>
      </header>
      <main id="ow-content" className="ow-main">
        <section className="ow-hero" aria-labelledby="ow-heading">
          <div>
            <p className="ow-overline">OULA {world.englishName}</p>
            <h1 id="ow-heading">
              {world.arabicName}
              <span dir="ltr">{world.englishName}</span>
            </h1>
            <h2>{world.purpose[lang]}</h2>
            <p>
              {w(
                "اختر القسم الذي تحتاجه، ثم انتقل إلى مهمة واضحة بخطواتها ومتطلباتها.",
                "Choose a section, then follow a clear task with its requirements and next step.",
              )}
            </p>
            <div className="ow-role">
              <label htmlFor="world-role">
                {w("أستكشف بصفتي", "I am exploring as")}
              </label>
              <select
                id="world-role"
                value={context.role}
                onChange={(event) => navigate({ role: event.target.value })}
              >
                {roles.map((role) => (
                  <option key={role.id} value={role.id}>
                    {role.name[lang]}
                  </option>
                ))}
              </select>
              <small>
                {w(
                  "دورك يخصص العرض. الوصول إلى البيانات تحدده صلاحيات حسابك.",
                  "Your role tailors the view. Account permissions determine data access.",
                )}
              </small>
            </div>
          </div>
          <figure className="ow-portal-art">
            <WorldArt world={worldId} />
            <figcaption>
              {w("من بوابة عُلا إلى عالمك", "Your world, connected to OULA")}
            </figcaption>
          </figure>
        </section>
        <section className="ow-tasks" aria-labelledby="ow-sections-title">
          <div className="ow-section-header">
            <div>
              <p className="ow-overline">
                {w("مساحات تناسب احتياجك", "SPACES FOR YOUR NEEDS")}
              </p>
              <h2 id="ow-sections-title">
                {w("اختر قسمك", "Choose your section")}
              </h2>
            </div>
            <span>{w("٦ أقسام لاستكشاف عالمك", "6 sections to explore")}</span>
          </div>
          <div className="ow-task-grid">
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
                <span aria-hidden="true">↗</span>
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
            <p className="ow-overline">
              {world.englishName} / {section.id}
            </p>
            <h2 id="ow-active-section-title">{section.name[lang]}</h2>
            <p className="ow-copy">{section.description[lang]}</p>
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
                  <strong>{item.name[lang]}</strong>
                  <small>
                    {item.target
                      ? w("افتح المسار المتصل", "Connected workspace path")
                      : w(
                          "استكشف النطاق المستهدف",
                          "Explore the planned scope",
                        )}
                  </small>
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
                        )}{" "}
                        ↗
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
                        {w(
                          "النطاق متاح للاستكشاف",
                          "Scope available to explore",
                        )}
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
        <aside className="ow-guide">
          <MotherMark />
          <div>
            <h2>
              {w("عُلا معك، عبر العوالم", "OULA at your side, across worlds")}
            </h2>
            <p>
              {w(
                "عد إلى البوابة لاختيار مسار آخر، مع بقاء بياناتك محكومة بصلاحيات حسابك.",
                "Return to the gateway for another path. Your records remain subject to account permissions.",
              )}
            </p>
          </div>
          <Link href={"/gateway?lang=" + lang}>
            {w("اسأل عُلا عن المسار", "Find a path with OULA")} ↗
          </Link>
        </aside>
        <nav
          className="ow-family-nav"
          aria-label={w("عوالم عُلا الأخرى", "Other OULA worlds")}
        >
          <h2>{w("عوالم مختلفة. منظومة واحدة.", "Many worlds. One OULA.")}</h2>
          <div>
            {OULA_WORLDS.filter((item) => item.id !== worldId).map((item) => (
              <Link
                key={item.id}
                href={familyHref({ world: item.id, role: context.role, lang })}
              >
                <i style={{ background: item.color }} aria-hidden="true" />
                {lang === "ar" ? item.arabicName : item.englishName}
              </Link>
            ))}
          </div>
        </nav>
      </main>
      <footer className="ow-footer">
        <Link href={"/gateway?lang=" + lang}>
          {w("العودة إلى البوابة", "Return to gateway")}
        </Link>
        <p>
          {w(
            "المعلومة بمصدرها. التوصية بحدودها. القرار بإرادتك.",
            "Facts with sources. Recommendations with limits. Decisions under your control.",
          )}
        </p>
      </footer>
    </div>
  );
}
