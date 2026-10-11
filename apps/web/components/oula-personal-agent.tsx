"use client";

import { useState } from "react";
import { OULA_WORLDS, planAgentRequest, planWorld, type AgentLanguage, type AgentPlan, type AgentStage, type AgentWorld } from "../lib/personal-agent";

type Props = { language: AgentLanguage; onNavigate: (stage: AgentStage) => void };
const t = (language: AgentLanguage, words: { ar: string; en: string }) => words[language];

/**
 * Human-directed, session-memory-only navigation preview.
 * Never invokes the backend, stores a profile, signs, pays, or changes canonical truth.
 */
export function OulaPersonalAgent({ language, onNavigate }: Props) {
  const [input, setInput] = useState("");
  const [plan, setPlan] = useState<AgentPlan | null>(null);
  const [paused, setPaused] = useState(false);
  const [reviewed, setReviewed] = useState(false);
  const [opened, setOpened] = useState(false);
  const [expanded, setExpanded] = useState(true);
  const [events, setEvents] = useState<string[]>([]);

  const begin = (nextPlan: AgentPlan) => {
    setPlan(nextPlan);
    setPaused(false);
    setReviewed(false);
    setOpened(false);
    setEvents([t(language, {ar:"فُسّر الطلب محليًا. لم تُستدعَ أي خدمة خارجية.",en:"Request classified locally. No external service invoked."})]);
  };

  const clear = () => {
    setInput("");
    setPlan(null);
    setPaused(false);
    setReviewed(false);
    setOpened(false);
    setEvents([]);
  };

  const navigate = () => {
    // Even a demo deal needs an explicit acknowledgment; this is NOT legal approval.
    if (paused || !plan || plan.outcome !== "SANDBOX" || !plan.stage || (plan.humanReview && !reviewed)) return;
    onNavigate(plan.stage);
    setOpened(true);
    setEvents((previous) => [...previous, t(language, {ar:"تم فتح القسم التجريبي فقط. لا تغييرات في الخادم.",en:"Demo view opened. No backend state changed."})]);
  };

  return <section className="personal-agent" aria-label={t(language, {ar:"عُلا — الوكيل الشخصي التجريبي",en:"OULA — personal agent preview"})}>
    <header className="personal-agent-header">
      <div className="agent-identity">
        <span className="agent-halo" data-state={paused ? "paused" : plan?.outcome ?? "idle"} aria-hidden="true"><span>◈</span></span>
        <div>
          <strong>{t(language, {ar:"عُلا معك",en:"OULA at your side"})} <small>PERSONAL AGENT / P0</small></strong>
          <p>{t(language, {ar:"مساعد توجيه محلي — ليس نموذج AI حيًا ولا وكيلًا معتمدًا للتنفيذ",en:"Local routing assistant — not a live AI model or an authorized executor"})}</p>
        </div>
      </div>
      <button type="button" className="agent-toggle" aria-expanded={expanded} onClick={() => setExpanded((old) => !old)}>
        {expanded ? t(language,{ar:"طي الوكيل",en:"Collapse"}):t(language,{ar:"افتح الوكيل",en:"Expand"})}
      </button>
    </header>
    {expanded && <div className="agent-body">
      <div className="agent-worlds" role="group" aria-label={t(language,{ar:"عوالم عُلا الستة",en:"Six OULA worlds"})}>
        {OULA_WORLDS.map((world) => <button
          type="button" key={world.id} className={"agent-world "+(plan?.world === world.id ? "selected":"")}
          aria-pressed={plan?.world === world.id} onClick={() => begin(planWorld(world.id))}
          title={t(language,world.description)}
        ><span className="agent-world-name">{world.id}</span><strong>{t(language,world.title)}</strong>
          <small>{world.status === "FUTURE" ? t(language,{ar:"رؤية مستقبلية",en:"Future concept"}):t(language,{ar:"تجربة توضيحية",en:"Demo only"})}</small></button>)}
      </div>
      <form className="agent-request" onSubmit={(event) => {event.preventDefault();begin(planAgentRequest(input));}}>
        <label htmlFor="oula-agent-goal">{t(language,{ar:"ما الهدف الذي تريد تحقيقه؟",en:"What would you like to accomplish?"})}</label>
        <div className="agent-request-controls">
          <input id="oula-agent-goal" type="text" maxLength={400} autoComplete="off" value={input} onChange={(event)=>setInput(event.target.value)}
            placeholder={t(language,{ar:"مثال: أريد منزلًا يناسب عائلتي في الرياض",en:"e.g. Find a home that fits my family in Riyadh"})}/>
          <button type="submit" className="primary-button">{t(language,{ar:"افهم طلبي",en:"Explore"})} ↗</button>
        </div>
      </form>
      <p className="agent-safety">{t(language,{ar:"لا يحفظ الطلب على الخادم، ولا يرسل معلومات إلى نموذج ذكاء، ولا ينفّذ أي صفقة. جميع بيانات القسم تجريبية.",en:"No server storage, model submission or transaction execution. This is an explicitly fictional preview."})}</p>
      {plan && <div className="agent-plan" aria-live="polite">
        <div className="agent-plan-heading">
          <strong>{plan.world ?? "OULA"} / {t(language,{ar:"خطة الوكيل",en:"Agent plan"})}</strong>
          <span className="agent-plan-state">{plan.outcome}{paused ? " · PAUSED":""}</span>
        </div>
        <p>{t(language,plan.summary)}</p>
        {plan.organ !== "none" && <small className="agent-organs">{t(language,{ar:"الأعضاء المعنية:",en:"OULA organs:"})} {plan.organ}</small>}
        {plan.steps.length > 0 && <ol className="agent-trace">{plan.steps.map((step,index) =>
          <li key={index}><span aria-hidden="true">{String(index+1).padStart(2,"0")}</span>{t(language,step)}</li>)}</ol>}
        {plan.outcome === "SANDBOX" && <div className="agent-actions">
          {plan.humanReview && <label className="agent-consent"><input type="checkbox" checked={reviewed} onChange={(event)=>setReviewed(event.target.checked)} />
            {t(language,{ar:"أفهم أن غرفة الصفقة محاكاة، وأن فتحها لا يعني موافقة قانونية أو تنفيذًا حقيقيًا.",en:"I understand the Deal Room is a sandbox; opening it gives no legal authority or transaction approval."})}</label>}
          <div className="agent-buttons">
            <button type="button" className="minor-button" onClick={()=>setPaused((value)=>!value)}>{paused?t(language,{ar:"استئناف العرض",en:"Resume"}):t(language,{ar:"إيقاف مؤقت",en:"Pause"})}</button>
            <button type="button" className="primary-button" onClick={navigate} disabled={paused || opened || (plan.humanReview && !reviewed)}>
              {opened ? t(language,{ar:"فُتح القسم التجريبي",en:"Preview opened"}):t(language,{ar:"افتح القسم المقترح",en:"Open suggested view"})}
            </button>
          </div>
        </div>}
        {events.length > 0 && <div className="agent-event-log"><strong>{t(language,{ar:"سجل الجلسة المحلي",en:"Local session trace"})}</strong>
          <ul>{events.map((event,index)=><li key={index}>{event}</li>)}</ul></div>}
      </div>}
      <footer className="agent-footer"><span>{t(language,{ar:"الذاكرة: جلسة المتصفح فقط، دون تخزين دائم.",en:"Memory: this browser session only; no persistence."})}</span>
        <button type="button" className="agent-clear" onClick={clear}>{t(language,{ar:"مسح الطلب والخطوات",en:"Clear request & trace"})}</button></footer>
    </div>}
  </section>;
}
