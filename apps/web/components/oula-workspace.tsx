"use client";
import "./workspace-origin.css";
import { SpatialShell } from "./spatial-shell";
import { MotherMark, WorldArt } from "./gateway-art";
import type { OulaWorldId } from "../lib/gateway";
import { useEffect, useRef, useState, type CSSProperties } from "react";
import {
  planAgentRequest,
  type AgentPlan,
  type AgentWorld,
} from "../lib/personal-agent";
import {
  worlds,
  roles,
  views,
  worldNavigation,
  type WorkspaceView,
} from "../lib/workspaces";
import { useOulaSession } from "../lib/use-oula-session";
import { sessionContextKey } from "../lib/session-state";
import {
  familyHref,
  getContextBranch,
  readFamilyContext,
  safeWorkspaceReturnTo,
  type FamilyContext,
} from "../lib/family-catalog";
import { OULA_WORLDS } from "../lib/gateway";
import { PropertyIntakeForm } from "./property-intake-form";
import { ConnectedJourney } from "./connected-journey";
import {
  Icon,
  OulaMark,
  StatusBanner,
  Wave,
  word,
  type Language,
} from "./workspace-primitives";

export function OulaWorkspace() {
  const [language, setLanguage] = useState<Language>("ar");
  const [worldId, setWorldId] = useState<AgentWorld>("PULSE");
  const [roleId, setRoleId] = useState("individual");
  const [view, setView] = useState<WorkspaceView>("overview");
  const [mode, setMode] = useState<"demo" | "server">("demo");
  const [sourceContext, setSourceContext] = useState<FamilyContext | null>(
    null,
  );
  const [notice, setNotice] = useState<string | null>(null);
  const [layers, setLayers] = useState({
    places: true,
    services: true,
    environment: false,
  });
  const [hour, setHour] = useState(18);
  const { session, reload, changeWorkspace, logout, loginLocal } =
    useOulaSession();
  const contextKey = sessionContextKey(session);
  const [assistant, setAssistant] = useState<{
    context: string;
    goal: string;
    plan: AgentPlan | null;
  }>({ context: contextKey, goal: "", plan: null });
  // Hide the old context immediately, before the cleanup effect runs.
  const goal = assistant.context === contextKey ? assistant.goal : "";
  const plan = assistant.context === contextKey ? assistant.plan : null;
  const setGoal = (value: string) =>
    setAssistant((previous) => ({
      context: contextKey,
      goal: value,
      plan: previous.context === contextKey ? previous.plan : null,
    }));
  const setPlan = (value: AgentPlan | null) =>
    setAssistant((previous) => ({
      context: contextKey,
      plan: value,
      goal: previous.context === contextKey ? previous.goal : "",
    }));
  useEffect(() => {
    setAssistant({ context: contextKey, goal: "", plan: null });
  }, [contextKey]);
  const heading = useRef<HTMLHeadingElement>(null);
  const goalInput = useRef<HTMLInputElement>(null);
  const mounted = useRef(false);
  const entryFocused = useRef("");
  const world = worlds.find((item) => item.id === worldId)!;
  const role = roles.find((item) => item.id === roleId)!;
  const w = (ar: string, en: string) => word(language, { ar, en });

  useEffect(() => {
    const restore = () => {
      const params = new URLSearchParams(window.location.search);
      const requestedWorld = params.get("world");
      const requestedRole = params.get("role");
      const requestedView = params.get("view");
      setWorldId(
        worlds.some((item) => item.id === requestedWorld)
          ? (requestedWorld as AgentWorld)
          : "PULSE",
      );
      setRoleId(
        roles.some((item) => item.id === requestedRole)
          ? requestedRole!
          : "individual",
      );
      setView(
        requestedView && Object.hasOwn(views, requestedView)
          ? (requestedView as WorkspaceView)
          : "overview",
      );
      setLanguage(params.get("lang") === "en" ? "en" : "ar");
      setMode(params.get("connection") === "server" ? "server" : "demo");
      setNotice(params.get("authError") ? "auth-error" : null);
      const family = OULA_WORLDS.find(
        (item) => item.id.toUpperCase() === requestedWorld,
      );
      const source = family ? readFamilyContext(params, family.id) : null;
      setSourceContext(source?.section && source.branch ? source : null);
    };
    restore();
    window.addEventListener("popstate", restore);
    return () => window.removeEventListener("popstate", restore);
  }, []);
  useEffect(() => {
    document.documentElement.lang = language;
    document.documentElement.dir = language === "ar" ? "rtl" : "ltr";
    document.title = `${word(language, views[view])} · OULA ${worldId} | عُلا`;
    if (mounted.current) heading.current?.focus();
    mounted.current = true;
  }, [language, view, worldId]);

  const source = sourceContext ? getContextBranch(sourceContext) : null;
  const entryTarget = source?.branch?.target;
  useEffect(() => {
    if (
      !entryTarget ||
      view !== entryTarget.view ||
      mode !== "server" ||
      session.status !== "authenticated"
    )
      return;
    const key =
      contextKey +
      ":" +
      sourceContext?.world +
      ":" +
      sourceContext?.section +
      ":" +
      sourceContext?.branch;
    if (entryFocused.current === key) return;
    const frame = requestAnimationFrame(() => {
      const field = document.getElementById(entryTarget.focus);
      if (field) {
        field.focus();
        entryFocused.current = key;
      }
    });
    return () => cancelAnimationFrame(frame);
  }, [entryTarget, view, mode, session.status, contextKey, sourceContext]);
  const returnParams = new URLSearchParams({
    world: worldId,
    role: roleId,
    view,
    lang: language,
    connection: "server",
  });
  if (sourceContext?.section)
    returnParams.set("section", sourceContext.section);
  if (sourceContext?.branch) returnParams.set("branch", sourceContext.branch);
  const loginHref =
    "/api/auth/login?returnTo=" +
    encodeURIComponent(safeWorkspaceReturnTo("/workspace?" + returnParams));

  function navigate(
    next: WorkspaceView,
    nextWorld: AgentWorld = worldId,
    nextRole = roleId,
  ) {
    setView(next);
    setWorldId(nextWorld);
    setRoleId(nextRole);
    setNotice(null);
    const params = new URLSearchParams(window.location.search);
    params.set("world", nextWorld);
    params.set("view", next);
    params.set("role", nextRole);
    params.set("lang", language);
    params.delete("authError");
    if (nextWorld !== worldId) {
      params.delete("section");
      params.delete("branch");
      setSourceContext(null);
    } else if (sourceContext)
      setSourceContext({ ...sourceContext, role: nextRole, lang: language });
    history.pushState(null, "", "/workspace?" + params.toString());
  }
  const selectRole = (id: string) => {
    const selected = roles.find((item) => item.id === id);
    if (selected) {
      setPlan(null);
      navigate("overview", selected.world, id);
    }
  };
  const changeLanguage = () => {
    const next = language === "ar" ? "en" : "ar";
    setLanguage(next);
    setSourceContext((previous) =>
      previous ? { ...previous, lang: next } : null,
    );
    const params = new URLSearchParams(window.location.search);
    params.set("lang", next);
    history.replaceState(null, "", "/workspace?" + params.toString());
  };
  const changeConnection = (next: "demo" | "server") => {
    setMode(next);
    const params = new URLSearchParams(window.location.search);
    params.set("connection", next);
    history.replaceState(null, "", "/workspace?" + params.toString());
  };

  const openPlan = () => {
    if (
      !plan ||
      plan.outcome === "DENIED" ||
      plan.outcome === "CLARIFY" ||
      !plan.world
    )
      return;
    const targetWorld = plan.world === "PRISM" ? "NEXUS" : plan.world;
    const nextWorld = worlds.find((item) => item.id === targetWorld)!;
    const nextView =
      plan.world === "PRISM"
        ? "decision"
        : plan.world === "NEXUS"
          ? "deal"
          : nextWorld.future
            ? "studio"
            : nextWorld.view;
    navigate(nextView, targetWorld);
  };
  const status =
    session.status === "authenticated"
      ? w("جلسة متصلة", "Connected session")
      : session.status === "unconfigured"
        ? w("الاتصال غير مهيأ", "Connection not configured")
        : session.status === "loading"
          ? w("التحقق من الجلسة…", "Checking session…")
          : session.status === "error"
            ? w("تعذر فحص الجلسة", "Session check unavailable")
            : w("زائر", "Guest");
  const pageTitle =
    view === "overview"
      ? worldId === "PULSE"
        ? w(
            "هنا يبدأ المكان الذي يشبهك.",
            "A place that feels like you starts here.",
          )
        : word(language, world.line)
      : word(language, views[view]);

  return (
    <SpatialShell
      world={worldId}
      view={view}
      language={language}
      title={word(language, world.line)}
      controls={
        <button
          className="glass-button language-button"
          aria-label={
            language === "ar" ? "Switch to English" : "التبديل إلى العربية"
          }
          onClick={changeLanguage}
        >
          {language === "ar" ? "EN" : "عربي"}
        </button>
      }
      navigation={
        <>
          <nav className="workspace-nav">
            {worldNavigation(worldId).map((item) => (
              <button
                key={item}
                className={view === item ? "selected" : ""}
                aria-current={view === item ? "page" : undefined}
                onClick={() => navigate(item)}
              >
                <Icon name={item} />
                <span>{word(language, views[item])}</span>
              </button>
            ))}
          </nav>
          <div className="rail-worlds">
            {worlds.map((item) => (
              <button
                key={item.id}
                aria-label={"OULA " + item.id}
                title={word(language, item.name)}
                aria-pressed={worldId === item.id}
                onClick={() => {
                  setPlan(null);
                  navigate("overview", item.id);
                }}
                style={{ "--item-accent": item.accent } as CSSProperties}
              >
                <WorldArt world={item.id.toLowerCase() as OulaWorldId} seal />
                <span>{word(language, item.name)}</span>
              </button>
            ))}
          </div>
          <a className="legacy-link" href="/journey">
            {w("الرحلة التوضيحية", "Demo journey")}{" "}
            <Icon name="arrow" size={15} />
          </a>
        </>
      }
      assistant={
        <>
          <div className="assistant-heading">
            <MotherMark />
            <div>
              <strong>{w("عُلا معك", "OULA at your side")}</strong>
              <small>
                {w("مساعد توجيه تجريبي", "Preview navigation assistant")}
              </small>
            </div>
            <span className="assistant-dot" />
          </div>
          <div className="assistant-orb" aria-hidden="true">
            <div />
            <WorldArt
              world={
                (worldId === "PRISM"
                  ? "nexus"
                  : worldId.toLowerCase()) as OulaWorldId
              }
              seal
            />
          </div>
          <div className="assistant-wave">
            <Wave active={!!plan && plan.outcome !== "DENIED"} />
          </div>
          <p className="assistant-intro">
            {w(
              "أخبرني ما تحتاجه، وسأقترح لك الطريق داخل عُلا.",
              "Tell me what you need. I will suggest your path inside OULA.",
            )}
          </p>
          <div className="assistant-context">
            <span>{w("سياقك الآن", "YOUR CONTEXT")}</span>
            <strong>OULA {worldId}</strong>
            <small>
              {word(language, role.name)} · {word(language, views[view])}
            </small>
          </div>
          <form
            noValidate
            className="workspace-agent-form"
            onSubmit={(event) => {
              event.preventDefault();
              setPlan(planAgentRequest(goal));
            }}
          >
            <label htmlFor="workspace-goal">
              {w("ما الذي ترغب بتحقيقه؟", "What would you like to accomplish?")}
            </label>
            <div>
              <input
                ref={goalInput}
                id="workspace-goal"
                value={goal}
                onChange={(event) => setGoal(event.target.value)}
                maxLength={400}
                autoComplete="off"
                placeholder={w(
                  "أريد منزلًا يناسب عائلتي…",
                  "Find a home for my family…",
                )}
              />
              {goal && (
                <button
                  type="button"
                  className="clear-goal"
                  aria-label={w("مسح الطلب", "Clear request")}
                  onClick={() => {
                    setGoal("");
                    setPlan(null);
                    goalInput.current?.focus();
                  }}
                >
                  ×
                </button>
              )}
              <button
                className="send-goal"
                aria-label={w("افهم طلبي", "Understand my request")}
                type="submit"
              >
                <Icon name="send" size={19} />
              </button>
            </div>
          </form>
          {plan ? (
            <div className={"assistant-plan " + plan.outcome} role="status">
              <span className="kicker">
                {w("الخطوة المقترحة", "SUGGESTED NEXT STEP")}
              </span>
              <p>{word(language, plan.summary)}</p>
              {plan.outcome !== "DENIED" && plan.outcome !== "CLARIFY" && (
                <button className="gold-button" onClick={openPlan}>
                  {w("افتح المساحة المناسبة", "Open the right workspace")}
                  <Icon name="arrow" size={16} />
                </button>
              )}
            </div>
          ) : (
            <div className="assistant-suggestions">
              <span className="kicker">
                {w("يمكنك البدء بـ", "YOU CAN START WITH")}
              </span>
              {[
                {
                  ar: "أريد منزلًا لعائلتي في الرياض",
                  en: "Find a family home in Riyadh",
                },
                {
                  ar: "أريد متابعة صيانة عقاري",
                  en: "Review my property maintenance",
                },
                {
                  ar: "أريد مقارنة خيارات الاستثمار",
                  en: "Compare investment options",
                },
              ].map((item) => (
                <button
                  key={item.en}
                  onClick={() => {
                    const request = word(language, item);
                    setGoal(request);
                    setPlan(planAgentRequest(request));
                  }}
                >
                  {word(language, item)}
                  <Icon name="arrow" size={14} />
                </button>
              ))}
            </div>
          )}
          <div className="assistant-boundary">
            <Icon name="guardian" size={17} />
            <p>
              {w(
                "يقترح التنقل فقط. لا يوقّع أو يدفع أو يغيّر السجلات، ولا يرسل طلبك إلى نموذج ذكاء خارجي.",
                "Suggests navigation only. No signing, payments, record changes or external AI prompt submission.",
              )}
            </p>
          </div>
          <div className="assistant-session">
            <span>{status}</span>
            {session.status === "authenticated" &&
              session.mode === "DEV_TEST_ONLY" && (
                <p>
                  DEV_TEST_ONLY ·{" "}
                  {w(
                    "بيانات خيالية · تشغيل محلي",
                    "Fictional data · local runtime",
                  )}
                </p>
              )}
            {session.status === "signed-out" ? (
              <>
                {!session.localDevAuth && (
                  <a className="glass-button" href={loginHref}>
                    {w("تسجيل الدخول", "Sign in")}
                  </a>
                )}
                {session.localDevAuth && (
                  <>
                    <p>
                      {w(
                        "جلسة محلية تجريبية ببيانات خيالية محفوظة في قاعدة البيانات.",
                        "Local test session with fictional, database-backed records.",
                      )}
                    </p>
                    <button
                      className="gold-button"
                      onClick={() =>
                        void loginLocal("buyer")
                          .then(() => changeConnection("server"))
                          .catch(() => setNotice("auth-error"))
                      }
                    >
                      {w("جرّب كمشترٍ محلي", "Try as local buyer")}
                    </button>
                    <button
                      className="glass-button"
                      onClick={() =>
                        void loginLocal("property-manager")
                          .then(() => {
                            changeConnection("server");
                            if (!sourceContext)
                              navigate("intake", "HABITAT", "manager");
                          })
                          .catch(() => setNotice("auth-error"))
                      }
                    >
                      {w("جرّب كمدير أملاك", "Try as property manager")}
                    </button>
                  </>
                )}
              </>
            ) : session.status === "unconfigured" ? (
              <p>
                {w(
                  "يمكن تشغيل مساحة العمل بعد تهيئة الهوية واتصال الخدمة محليًا.",
                  "The workspace can run after local identity and service configuration.",
                )}
              </p>
            ) : null}
          </div>
        </>
      }
    >
      <div className="spatial-control-deck">
        <div className="workspace-context">
          <div>
            <label htmlFor="workspace-role">
              {w("مساحتك حسب دورك", "Your role's workspace")}
            </label>
            <select
              id="workspace-role"
              value={roleId}
              onChange={(event) => selectRole(event.target.value)}
            >
              {roles.map((item) => (
                <option key={item.id} value={item.id}>
                  {word(language, item.name)}
                </option>
              ))}
            </select>
          </div>
          <div className="connection-control">
            <span className={"session-status " + session.status}>
              <i />
              {status}
            </span>
            <div
              className="mode-switch"
              role="group"
              aria-label={w("مصدر البيانات", "Data source")}
            >
              <button
                aria-pressed={mode === "demo"}
                onClick={() => changeConnection("demo")}
              >
                {w("عرض توضيحي", "Demo")}
              </button>
              <button
                aria-pressed={mode === "server"}
                onClick={() => changeConnection("server")}
              >
                {w("مساحة العمل", "Workspace")}
              </button>
            </div>
          </div>
        </div>
        {session.status === "authenticated" && (
          <div className="signed-in-context">
            <label htmlFor="authorized-workspace">
              {w("مساحة العمل المصرح بها", "Authorized workspace")}
            </label>
            <select
              id="authorized-workspace"
              value={session.workspaceId}
              onChange={(event) =>
                void changeWorkspace(event.target.value).catch(() =>
                  setNotice("workspace-error"),
                )
              }
            >
              {session.workspaceIds.map((id) => (
                <option value={id} key={id}>
                  {id}
                </option>
              ))}
            </select>
            <button
              className="text-button"
              onClick={() =>
                void logout().catch(() => setNotice("logout-error"))
              }
            >
              {w("تسجيل الخروج", "Sign out")}
            </button>
          </div>
        )}
      </div>
      {notice && (
        <StatusBanner tone="error">
          {notice === "auth-error"
            ? w(
                "لم يكتمل تسجيل الدخول. يمكنك إعادة المحاولة.",
                "Sign-in did not complete. You can try again.",
              )
            : w(
                "تعذر تنفيذ تغيير الجلسة. أعد المحاولة.",
                "The session change failed. Try again.",
              )}
        </StatusBanner>
      )}
      {session.status === "error" && (
        <button className="text-button" onClick={() => void reload()}>
          {w("أعد فحص الجلسة", "Retry session check")}
        </button>
      )}
      {sourceContext && source?.section && source.branch && (
        <aside
          className="workspace-origin"
          aria-label={w("مسار المهمة", "Task path")}
        >
          <div>
            <small>
              {w("مسارك من العالم إلى المهمة", "Your world-to-task path")}
            </small>
            <strong>
              {source.section.name[language]} / {source.branch.name[language]}
            </strong>
          </div>
          <a
            href={familyHref({
              ...sourceContext,
              role: roleId,
              lang: language,
            })}
          >
            {w("العودة إلى الفرع", "Return to branch")} ↗
          </a>
        </aside>
      )}
      <div className="workspace-page-title">
        <div>
          <span className="kicker">
            OULA {worldId} <span> / </span> {word(language, role.name)}
          </span>
          <h1 ref={heading} tabIndex={-1}>
            {pageTitle}
          </h1>
          <p>
            {view === "overview"
              ? w(
                  "اكتشف. افهم. قارن. وامضِ إلى الخطوة التي تناسبك.",
                  "Explore. Understand. Compare. Take the step that fits you.",
                )
              : word(language, world.line)}
          </p>
        </div>
        <span className={"capability-badge " + (world.future ? "future" : "")}>
          {world.future
            ? w("رؤية مستقبلية", "Future concept")
            : mode === "demo"
              ? w("تجربة توضيحية", "Demo experience")
              : w("خدمات مساحة العمل", "Workspace services")}
        </span>
      </div>
      {view === "overview" ? (
        <>
          <section
            className="city-stage"
            aria-label={w("تصور بصري للرياض", "Illustrative Riyadh city scene")}
          >
            <div
              className="city-image"
              style={{
                filter: `brightness(${worldId === "FORGE" ? 0.45 + hour / 40 : 0.83})`,
              }}
            />
            <div className="city-shading" />
            <div className="city-stage-top">
              <span className="map-context">
                <i />
                {w("الرياض · نقطة البداية", "Riyadh · the starting point")}
              </span>
              <span className="map-reference">
                {w(
                  "تصوّر بصري، وليس خريطة حية",
                  "Illustration, not a live map",
                )}
              </span>
            </div>
            <div className="city-pins" aria-hidden="true">
              <span className="city-pin pin-a">
                <Icon name="location" size={18} />
                {w("مكانك القادم", "Your next place")}
              </span>
              <span className="city-pin pin-b">
                <Icon name={world.view} size={18} />
                {word(language, world.name)}
              </span>
            </div>
            <div className="city-callout">
              <span className="kicker">
                {world.future
                  ? w("استكشف الرؤية", "Explore the concept")
                  : w("الإنسان أولًا", "HUMAN FIRST")}
              </span>
              <h2>{word(language, world.line)}</h2>
              <p>
                {world.future
                  ? w(
                      "طبقات تفاعلية لفهم التجربة المستهدفة. المحركات الهندسية والحضرية ضمن خارطة التطوير.",
                      "Interactive layers show the intended experience. Engineering and city engines remain on the roadmap.",
                    )
                  : w(
                      "مساحة واحدة تجمع احتياجك ومعلومات العقار والخطوة التالية.",
                      "One workspace for your needs, property information and the next step.",
                    )}
              </p>
              <button
                className="gold-button"
                onClick={() => navigate(world.view)}
              >
                {world.future
                  ? w("افتح الاستوديو", "Open studio")
                  : w("ابدأ من هنا", "Start here")}
                <Icon name="arrow" />
              </button>
            </div>
            <div className="city-coordinate" aria-hidden="true">
              24.7136° N <span>46.6753° E</span>
            </div>
          </section>
          <div className="role-shortcuts">
            {role.tools.map((tool, i) => (
              <button
                className="glass-button"
                key={tool.en}
                onClick={() =>
                  navigate(
                    i === 0
                      ? world.view
                      : world.future
                        ? "studio"
                        : worldId === "PULSE"
                          ? "matching"
                          : worldId === "HABITAT"
                            ? "guardian"
                            : "passport",
                  )
                }
              >
                <Icon name={i === 0 ? world.view : "decision"} />
                {word(language, tool)}
                <Icon name="arrow" size={16} />
              </button>
            ))}
          </div>
          <section
            className="world-catalog"
            aria-labelledby="world-catalog-title"
          >
            <div className="section-heading">
              <h2 id="world-catalog-title">
                {w(
                  "عالم واحد. مساحات تناسبك.",
                  "One world. Spaces that fit you.",
                )}
              </h2>
              <span>
                {w(
                  "هوية متصلة · تجربة حسب الدور",
                  "Connected identity · role-shaped experience",
                )}
              </span>
            </div>
            <div className="world-cards">
              {worlds.map((item) => (
                <button
                  className={
                    "world-card " + (worldId === item.id ? "active" : "")
                  }
                  key={item.id}
                  onClick={() => {
                    setPlan(null);
                    navigate("overview", item.id);
                  }}
                  style={{ "--card-accent": item.accent } as CSSProperties}
                >
                  <div className="world-card-image">
                    <img
                      src={`/visuals/${item.image}.png`}
                      alt=""
                      loading="lazy"
                      width="1536"
                      height="864"
                    />
                    <span>
                      {item.future
                        ? w("رؤية", "Concept")
                        : w("عرض توضيحي", "Demo")}
                    </span>
                  </div>
                  <div className="world-card-caption">
                    <span>
                      <small>OULA {item.id}</small>
                      <strong>{word(language, item.name)}</strong>
                    </span>
                    <Icon name="arrow" size={18} />
                  </div>
                </button>
              ))}
            </div>
          </section>
          <div className="trust-strip">
            <Icon name="guardian" />
            <p>
              {w(
                "كل معلومة بمصدرها. كل توصية بحدودها. كل قرار بإرادتك.",
                "Every fact with its source. Every recommendation with its limits. Every decision under your control.",
              )}
            </p>
          </div>
        </>
      ) : view === "studio" ? (
        <section className="concept-studio">
          <div
            className="studio-canvas"
            data-places={layers.places}
            data-services={layers.services}
            data-environment={layers.environment}
            style={{ "--scene-brightness": 0.4 + hour / 30 } as CSSProperties}
          >
            <img
              src="/visuals/spatial/riyadh-canvas.webp"
              alt={w(
                "تصميم مرجعي لتجربة عُلا المستقبلية",
                "Reference design for OULA's future experience",
              )}
              width={1536}
              height={864}
            />
            <div className="studio-layer places" />
            <div className="studio-layer services" />
            <div className="studio-layer environment" />
            <span className="studio-caption">
              {w(
                "تصوّر تفاعلي · لا نتائج محاكاة علمية أو بيانات حكومية حية",
                "Interactive concept · no scientific simulation results or live government data",
              )}
            </span>
          </div>
          <div className="studio-controls">
            <h2>{w("طبقات العرض", "Display layers")}</h2>
            {(["places", "services", "environment"] as const).map((key, i) => (
              <label key={key}>
                <input
                  type="checkbox"
                  checked={layers[key]}
                  onChange={(event) =>
                    setLayers({ ...layers, [key]: event.target.checked })
                  }
                />
                {word(
                  language,
                  [
                    { ar: "الأماكن والأصول", en: "Places and assets" },
                    { ar: "الخدمات والسياق", en: "Services and context" },
                    {
                      ar: "البيئة والمساحات",
                      en: "Environment and spaces",
                    },
                  ][i]!,
                )}
              </label>
            ))}
            <label htmlFor="scene-hour">
              {w("إضاءة العرض", "Scene lighting")}: {hour}:00
            </label>
            <input
              id="scene-hour"
              type="range"
              min={6}
              max={22}
              value={hour}
              onChange={(event) => setHour(Number(event.target.value))}
            />
            <StatusBanner>
              {w(
                "تغيّر هذه الأدوات العرض البصري فقط. لا تحسب رياحًا أو ظلالًا أو امتثالًا هندسيًا.",
                "These controls change the visual display only. They do not calculate wind, shadows or engineering compliance.",
              )}
            </StatusBanner>
          </div>
        </section>
      ) : view === "intake" ? (
        <PropertyIntakeForm
          key={`${mode}:${worldId}:${roleId}:${contextKey}`}
          language={language}
          mode={mode}
          session={session}
        />
      ) : (
        <ConnectedJourney
          key={`${mode}:${worldId}:${roleId}:${contextKey}`}
          language={language}
          mode={mode}
          view={view}
          session={session}
          onNavigate={navigate}
          loginHref={loginHref}
        />
      )}
      <footer className="workspace-footer">
        <OulaMark size={24} />
        <span>
          OULA · {w("لعالم مبني أكثر إنسانية", "A more human built world")}
        </span>
        <small>
          {w("الرياض أولًا · رؤية عالمية", "Riyadh first · global vision")}
        </small>
      </footer>
    </SpatialShell>
  );
}
