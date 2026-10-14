"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { getWorld, OULA_WORLDS, suggestWorld, type OulaLanguage, type OulaWorldId } from "../../lib/gateway";

type L10n = Readonly<{ ar: string; en: string }>;
type PreviewAction = Readonly<{
  key: string;
  label: L10n;
  detail: L10n;
  readiness: "DEMO" | "FUTURE";
}>;

const experiences: Readonly<Record<OulaWorldId, Readonly<{
  headline: L10n;
  description: L10n;
  firstQuestion: L10n;
  actions: readonly PreviewAction[];
}>>> = {
  pulse: {
    headline: { ar: "من حياتك، إلى المكان المناسب.", en: "From your life to the right place." },
    description: { ar: "ابدأ باحتياج واضح، افحص ما نعرفه عن العقار، وقارن البدائل قبل القرار.", en: "Start with your needs, examine property evidence and compare alternatives before deciding." },
    firstQuestion: { ar: "هل تريد شراء منزل أو استئجاره أو مقارنة الخيارين؟", en: "Are you buying, renting, or comparing the two?" },
    actions: [
      { key: "life", label: { ar: "احتياجي العقاري", en: "My housing needs" }, detail: { ar: "ابدأ بتحديد الميزانية والموقع والاحتياجات في الرحلة التجريبية القائمة.", en: "Explore needs, budget and location in the existing demo journey." }, readiness: "DEMO" },
      { key: "fit", label: { ar: "المطابقة والتوصيات", en: "Matching and recommendations" }, detail: { ar: "نتائج توضيحية مع أسباب المطابقة؛ ليست نتائج حية من قاعدة البيانات.", en: "Illustrative matches and reasons, not live database results." }, readiness: "DEMO" },
      { key: "passport", label: { ar: "جواز العقار", en: "Property passport" }, detail: { ar: "تمييز ما هو معلن عن البيانات المثبتة أو المستنتجة في المثال التعليمي.", en: "Distinguish declared information from verified or inferred facts in the educational example." }, readiness: "DEMO" },
      { key: "relocation", label: { ar: "الانتقال والتكاملات", en: "Moving and integrations" }, detail: { ar: "تنظيم الانتقال والخدمات الحكومية والتذكيرات بعد توافر التكاملات المعتمدة.", en: "Moving, public services and reminders require approved integrations." }, readiness: "FUTURE" },
    ],
  },
  habitat: {
    headline: { ar: "كل أصل، في سياقه الحقيقي.", en: "Every asset in its operational context." },
    description: { ar: "مساحة إدارة الملاك والمستأجرين ومديري الأملاك، من الالتزام إلى الصيانة والنتيجة.", en: "A workspace for owners, tenants and property managers, from obligations to maintenance and outcomes." },
    firstQuestion: { ar: "هل تدير منزلًا واحدًا أم محفظة أملاك؟", en: "Are you managing one home or an asset portfolio?" },
    actions: [
      { key: "assets", label: { ar: "ملفي العقاري", en: "My property portfolio" }, detail: { ar: "جواز الأصل وعلاقته بالوحدات والبيانات المصرح بها؛ إدارة المحفظة الحية قيد التكامل.", en: "Asset passports and units; a live portfolio is still being integrated." }, readiness: "DEMO" },
      { key: "guardian", label: { ar: "الحارس والصيانة", en: "Guardian and maintenance" }, detail: { ar: "اطلع على سيناريو Guardian التعليمي؛ لا توجد أوامر صيانة فعلية تُرسل.", en: "Explore the educational Guardian scenario; no live maintenance orders are issued." }, readiness: "DEMO" },
      { key: "leases", label: { ar: "العقود والإشغال", en: "Leases and occupancy" }, detail: { ar: "عرض تشغيلي محكوم بالصلاحيات يحتاج جلسات مستخدم واتصالًا بالبيانات المصرح بها.", en: "Authorized leases and occupancy views require sessions and real data integration." }, readiness: "FUTURE" },
      { key: "payments", label: { ar: "التحصيل والالتزامات", en: "Collections and obligations" }, detail: { ar: "سجلات المستندات والالتزامات لا تُعد تأكيدًا بنكيًا أو تحويلًا ماليًا.", en: "Documentary observations are not bank confirmations or money movements." }, readiness: "FUTURE" },
    ],
  },
  nexus: {
    headline: { ar: "الأطراف مترابطة، والقرار محكوم.", en: "Connected parties. Governed decisions." },
    description: { ar: "تنظيم أعمال الوساطة والصفقات والتعاون بين الأفراد والشركات والجهات المهنية.", en: "Coordinate brokerage, transactions and collaboration across people and professional organizations." },
    firstQuestion: { ar: "هل تريد إدارة صفقة، أم عملاء مكتبك، أم طلب تمويل؟", en: "Are you managing a deal, brokerage clients or a financing request?" },
    actions: [
      { key: "deal", label: { ar: "غرفة الصفقة", en: "Deal room" }, detail: { ar: "مراحل صفقة تجريبية، لا توقع عقودًا ولا تنقل ملكية أو أموالًا.", en: "A simulated deal lifecycle. No signatures, title transfer or payments." }, readiness: "DEMO" },
      { key: "crm", label: { ar: "المكتب والوسطاء", en: "Broker workspace" }, detail: { ar: "صلاحيات الموظفين والتفويضات والعملاء والعمولات تحتاج نواة هوية ومعاملات معتمدة.", en: "Staff rights, mandates, clients and commissions require governed identity and transaction services." }, readiness: "FUTURE" },
      { key: "finance", label: { ar: "التمويل والتأمين", en: "Finance and insurance" }, detail: { ar: "التكامل مع المؤسسات المرخصة يتطلب تعاقدات وموافقة وواجهات API رسمية.", en: "Licensed financial and insurance integrations require agreements, consent and approved APIs." }, readiness: "FUTURE" },
      { key: "intent", label: { ar: "الفرص الخاصة", en: "Private intent opportunities" }, detail: { ar: "تبادل الفرص بعد الموافقة المتبادلة والضوابط النظامية هو اتجاه مستقبلي.", en: "Consent-based private matching remains a future capability." }, readiness: "FUTURE" },
    ],
  },
  forge: {
    headline: { ar: "من تصور هندسي إلى قرار يمكن مراجعته.", en: "From design intent to reviewable engineering decisions." },
    description: { ar: "بيئة للمعماري والمدني والمطور والمقاول، تربط التصميم بالأدلة والتكلفة والتنفيذ.", en: "A planned environment for architects, engineers, developers and builders, connecting design to evidence, cost and delivery." },
    firstQuestion: { ar: "ما تخصص مشروعك: عمارة أم إنشاء أم بيئة أم تنفيذ؟", en: "Is your project architectural, structural, environmental or construction-oriented?" },
    actions: [
      { key: "bim", label: { ar: "النموذج الهندسي", en: "Engineering models" }, detail: { ar: "فهرسة إصدارات BIM والمستندات وربطها بالأصل المخطط، قيد البحث والتطوير.", en: "Versioned BIM, documents and planned-asset linkage are on the roadmap." }, readiness: "FUTURE" },
      { key: "sim", label: { ar: "المحاكاة العلمية", en: "Scientific simulation" }, detail: { ar: "تحليل الرياح والشمس والطاقة والإنشاء يحتاج محركات متخصصة وتحقيقًا هندسيًا.", en: "Wind, solar, energy and structural analysis require validated specialist engines." }, readiness: "FUTURE" },
      { key: "build", label: { ar: "التنفيذ والجداول", en: "Construction delivery" }, detail: { ar: "المهام والمخططات والتكلفة والاعتماد البشري تشكّل دورة التنفيذ المستهدفة.", en: "Tasks, drawings, cost and human approval define the planned delivery lifecycle." }, readiness: "FUTURE" },
      { key: "reality", label: { ar: "التوأم الرقمي", en: "Digital twin" }, detail: { ar: "التوأم الرقمي يحتاج مصادر بيانات موثوقة وربطًا مشروعًا بين التصميم والواقع.", en: "Digital twins require trusted observations and authorized real-world data." }, readiness: "FUTURE" },
    ],
  },
  civic: {
    headline: { ar: "المكان ليس خريطة؛ إنه حياة مترابطة.", en: "A city is more than a map. It is connected life." },
    description: { ar: "مساحة مستقبلية لدراسة استعمالات الأراضي والبنية التحتية وجودة الحياة والسيناريوهات الحضرية.", en: "A future workspace for land use, infrastructure, quality of life and urban scenarios." },
    firstQuestion: { ar: "هل تدرس حيًا أم خدمة عامة أم خطة مدينة؟", en: "Are you studying a district, public service or city plan?" },
    actions: [
      { key: "gis", label: { ar: "الطبقات المكانية", en: "Geospatial layers" }, detail: { ar: "طبقات GIS واستعمالات الأراضي والخدمات تحتاج ترخيص بيانات ومصادر موثوقة.", en: "GIS, land use and service layers require licensed, traceable data." }, readiness: "FUTURE" },
      { key: "scenario", label: { ar: "السيناريوهات الحضرية", en: "Urban scenarios" }, detail: { ar: "مقارنة الكثافة والوصول والموارد افتراضات بحثية إلى أن تُعاير النماذج.", en: "Density, accessibility and resource scenarios are research hypotheses pending model calibration." }, readiness: "FUTURE" },
      { key: "policy", label: { ar: "تحليل السياسات", en: "Policy analysis" }, detail: { ar: "المنظومة تدعم قرار المختص ولا تتخذ قرارًا حكوميًا أو تنظيميًا.", en: "The system could support experts; it does not make government or regulatory decisions." }, readiness: "FUTURE" },
      { key: "evidence", label: { ar: "القياس والأثر", en: "Evidence and outcomes" }, detail: { ar: "يفصل النظام المستهدف بين القياس الحقيقي والتوقع والأثر السببي غير المثبت.", en: "The intended system separates observations, projections and unproven causal claims." }, readiness: "FUTURE" },
    ],
  },
};

const labels = {
  ar: { home: "عُلا", all: "العائلات الخمس", workspace: "مساحة العمل", preview: "تصور تشغيلي — ليس خدمة حية", question: "ابدأ من هنا", explore: "استكشف المهمة", detail: "تفاصيل المهمة", lifecycle: "ما الذي تحتاجه هذه القدرة؟", available: "محاكاة تعليمية", future: "قدرة مستقبلية", close: "إغلاق التفاصيل", tryDemo: "افتح الرحلة التجريبية القائمة", message: "تُعرض القدرات هنا للشرح. الخدمات الفعلية تتطلب هوية وصلاحيات وبيانات وعقودًا معتمدة.", other: "انتقل بين العائلات", assist: "مساعد عُلا للتوجيه", assistHint: "التوجيه هنا محلي وتجريبي، وليس وكيلًا متصلًا بحساب أو خدمات.", placeholder: "اكتب ما تريد إنجازه", route: "اقترح عالمًا", noMatch: "لم يتضح مجال طلبك. اختر أحد العوالم.", recommended: "اقتراح مناسب:", open: "الانتقال إلى الفرع", back: "العودة إلى البوابة", switchLang: "Change to English" },
  en: { home: "OULA", all: "Five worlds", workspace: "Workspace", preview: "Operational concept — not a live service", question: "Start here", explore: "Explore task", detail: "Task details", lifecycle: "What this requires", available: "Educational demo", future: "Future capability", close: "Close details", tryDemo: "Open existing demonstration journey", message: "These are informative previews. Real services require authorized identity, permissions, data and contracts.", other: "Explore other worlds", assist: "OULA routing guide", assistHint: "This is local demonstration routing, not an agent connected to your accounts or services.", placeholder: "Describe your task", route: "Suggest a world", noMatch: "Unable to identify a suitable world. Choose one below.", recommended: "Suggested world:", open: "Go to workspace", back: "Return to gateway", switchLang: "التحويل إلى العربية" },
} as const;

function WorldGlyph({ id }: { id: OulaWorldId }) {
  const paths: Record<OulaWorldId, React.ReactNode> = {
    pulse: <path d="M4 18h7l4-10 6 19 5-12 5 3h6" />,
    habitat: <><path d="M7 17 20 6l13 11M10 15v22h20V15M16 37V25h8v12" /></>,
    nexus: <><path d="M6 18 14 10l7 5 8-5 6 8-10 11-9-4-4 2-6-9Z" /><path d="m16 20 10 10m-13-6 10 9" /></>,
    forge: <><path d="m20 5 15 9v18l-15 8-15-8V14L20 5Z" /><path d="m5 14 15 9 15-9M20 23v17" /></>,
    civic: <><path d="M4 37h32M8 36V17h9v19M19 36V7h9v29M30 36V22h6v14M12 21h1m-1 5h1m11-13h1m-1 6h1m-1 7h1" /></>,
  };
  return <svg aria-hidden="true" viewBox="0 0 42 44" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">{paths[id]}</svg>;
}

export default function WorldWorkspace({ worldId }: { worldId: OulaWorldId }) {
  const world = getWorld(worldId);
  const experience = experiences[worldId];
  const [lang, setLang] = useState<OulaLanguage>("ar");
  const [active, setActive] = useState<string | null>(null);
  const [prompt, setPrompt] = useState("");
  const [guideResult, setGuideResult] = useState<OulaWorldId | "unknown" | null>(null);
  const t = labels[lang];

  useEffect(() => {
    document.documentElement.lang = lang;
    document.documentElement.dir = lang === "ar" ? "rtl" : "ltr";
    return () => { document.documentElement.lang = "ar"; document.documentElement.dir = "rtl"; };
  }, [lang]);

  const selected = experience.actions.find(item => item.key === active);
  function guide(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setGuideResult(suggestWorld(prompt) ?? "unknown");
  }

  return <div className="ow-root" dir={lang === "ar" ? "rtl" : "ltr"} style={{ "--ow-accent": world.color } as React.CSSProperties}>
    <a href="#ow-content" className="ow-skip">{lang === "ar" ? "تجاوز إلى المهام" : "Skip to tasks"}</a>
    <header className="ow-header">
      <Link href="/gateway" className="ow-master" aria-label={t.back}><span className="ow-master-orbit" aria-hidden="true" />{t.home}<small>OULA</small></Link>
      <div className="ow-breadcrumb"><span>{t.all}</span><span aria-hidden="true">/</span><strong>{world.arabicName} | {world.englishName}</strong></div>
      <div className="ow-header-actions"><span className="ow-preview">{t.preview}</span><button type="button" className="ow-lang" aria-label={t.switchLang} onClick={() => setLang(lang === "ar" ? "en" : "ar")}>{lang === "ar" ? "EN" : "عربي"}</button></div>
    </header>

    <main id="ow-content" className="ow-main">
      <section className="ow-hero" aria-labelledby="ow-heading">
        <div className="ow-emblem" aria-hidden="true"><span className="ow-emblem-inner"><WorldGlyph id={worldId}/></span></div>
        <div className="ow-hero-copy"><p className="ow-overline">OULA {world.englishName} / {t.workspace}</p><h1 id="ow-heading">{world.arabicName}<span dir="ltr">{world.englishName}</span></h1><h2>{experience.headline[lang]}</h2><p>{experience.description[lang]}</p></div>
        <div className="ow-question"><span>{t.question}</span><strong>{experience.firstQuestion[lang]}</strong></div>
      </section>

      <section className="ow-tasks" aria-label={t.explore}>
        <div className="ow-section-header"><h2>{t.explore}</h2><p>{t.message}</p></div>
        <div className="ow-task-grid">
          {experience.actions.map((action, index) => <button type="button" key={action.key} className={"ow-task" + (active === action.key ? " ow-task-active" : "")} aria-expanded={active === action.key} aria-controls="ow-task-details" onClick={() => setActive(action.key === active ? null : action.key)}>
            <span className="ow-task-number">{String(index + 1).padStart(2, "0")}</span>
            <span className="ow-task-body"><strong>{action.label[lang]}</strong><small>{action.readiness === "DEMO" ? t.available : t.future}</small></span>
            <span aria-hidden="true" className="ow-task-chevron">↗</span>
          </button>)}
        </div>
        <div className="ow-task-detail" id="ow-task-details" aria-live="polite">
          {selected ? <><div className="ow-detail-heading"><strong>{selected.label[lang]}</strong><button type="button" onClick={() => setActive(null)}>{t.close}</button></div><p>{selected.detail[lang]}</p><p className="ow-detail-status">{selected.readiness === "DEMO" ? t.available : t.future} · {t.lifecycle}</p>{selected.readiness === "DEMO" ? <Link href="/">{t.tryDemo} ↗</Link> : <span className="ow-future-message">{t.message}</span>}</>
          : <p>{experience.firstQuestion[lang]}</p>}
        </div>
      </section>

      <section className="ow-guide" aria-labelledby="ow-guide-label">
        <div><h2 id="ow-guide-label">{t.assist}</h2><p>{t.assistHint}</p></div>
        <form onSubmit={guide}><label htmlFor="ow-prompt" className="ow-visually-hidden">{t.placeholder}</label><input id="ow-prompt" maxLength={240} value={prompt} placeholder={t.placeholder} onChange={event => { setPrompt(event.target.value); setGuideResult(null); }}/><button type="submit">{t.route}</button></form>
        <div className="ow-guide-result" aria-live="polite">{guideResult === "unknown" ? t.noMatch : guideResult ? <p>{t.recommended} <strong>{getWorld(guideResult).arabicName}</strong> · <Link href={"/gateway/" + guideResult}>{t.open} ↗</Link></p> : null}</div>
      </section>

      <nav className="ow-family-nav" aria-label={t.other}><h2>{t.other}</h2><div>{OULA_WORLDS.filter(w => w.id !== worldId).map(w => <Link href={"/gateway/" + w.id} key={w.id} style={{ "--ow-link-accent": w.color } as React.CSSProperties}><span aria-hidden="true" className="ow-family-dot"/>{lang === "ar" ? w.arabicName : w.englishName}</Link>)}</div></nav>
    </main>
    <footer className="ow-footer"><Link href="/gateway">← {t.back}</Link><span>{t.preview} · {t.message}</span></footer>
  </div>;
}
