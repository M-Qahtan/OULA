/**
 * OULA Personal Agent P0 — deterministic, browser-only experience seam.
 * This is NOT an LLM, an authenticated agent, a tool executor, or a financial/real-estate service.
 * Canonical facts/authorities remain in the OULA Java domain organs.
 */
export type AgentStage = "home" | "intent" | "matching" | "passport" | "decision" | "deal" | "guardian" | "intelligence";
export type AgentWorld = "PULSE" | "HABITAT" | "PRISM" | "NEXUS" | "FORGE" | "CIVIC";
export type AgentLanguage = "ar" | "en";
export type AgentOutcome = "SANDBOX" | "FUTURE" | "DENIED" | "CLARIFY";
export type Localized = Readonly<{ ar: string; en: string }>;

export type WorldDefinition = Readonly<{
  id: AgentWorld;
  title: Localized;
  description: Localized;
  organ: string;
  stage: AgentStage | null;
  status: "DEMO" | "FUTURE";
}>;

/** Experience labels sit ABOVE the canonical bounded contexts, never replace them. */
export const OULA_WORLDS: readonly WorldDefinition[] = [
  { id: "PULSE", title: {ar:"نبض الحياة",en:"Life & Place"}, description:{ar:"الاحتياج والمطابقة",en:"Intent and matching"},organ:"people · intent · matching",stage:"intent",status:"DEMO" },
  { id: "HABITAT", title: {ar:"رعاية الأصل",en:"Asset Care"},description:{ar:"إدارة العقار وما بعد التملك",en:"Property lifecycle"},organ:"operations · vitals · advisory",stage:"guardian",status:"DEMO" },
  { id: "PRISM", title: {ar:"بصيرة القرار",en:"Decision Insight"},description:{ar:"التحليل والمخاطر",en:"Analysis and risk"},organ:"market · intelligence · decision",stage:"decision",status:"DEMO" },
  { id: "NEXUS", title: {ar:"رحلة الصفقة",en:"Transaction Journey"},description:{ar:"خطوات وموافقات",en:"Steps and approvals"},organ:"transaction · compliance · documents",stage:"deal",status:"DEMO" },
  { id: "FORGE", title: {ar:"عالم التطوير",en:"Development Studio"},description:{ar:"التصميم والهندسة — مستقبلي",en:"Design and engineering — future"},organ:"future bounded contexts; no live engine",stage:null,status:"FUTURE" },
  { id: "CIVIC", title: {ar:"ذكاء المدينة",en:"City Intelligence"},description:{ar:"المكان الحضري — مستقبلي",en:"Urban context — future"},organ:"spatial · future city intelligence",stage:null,status:"FUTURE" },
] as const;

export type AgentPlan = Readonly<{
  world: AgentWorld | null;
  stage: AgentStage | null;
  outcome: AgentOutcome;
  requestedText: string;
  summary: Localized;
  steps: readonly Localized[];
  organ: string;
  humanReview: boolean;
}>;

const sensitiveAction = /(?:وق[ّ]?ع|توقيع (?:عقد|اتفاق)|ادفع|دفع (?:الآن|المبلغ)|حو[ّ]?ل (?:المبلغ|المال|الدفعة)|انقل (?:الملكية|الصك)|نف[ّ]?ذ (?:الصفقة|الدفع)|sign (?:a |the )?(?:contract|agreement)|transfer (?:money|funds|ownership)|pay (?:now|the)|submit (?:an? )?offer)/iu;
const topic = {
  NEXUS: /(?:صفق|عرض شراء|تفاوض|موعد معاين|عقد|شراء العقار|اتمام البيع|deal|offer|viewing|negotia|contract|transaction)/iu,
  HABITAT: /(?:صيان|أجر|إيجار|تأجير|مستأجر|ملّاك|ملاك|إدارة عقار|تشغيل|maintenance|tenant|rent|lease|manage my property)/iu,
  PRISM: /(?:استثمار|عائد|سعر السوق|جدوى|مخاطر|مقارن|تمويل|قسط|investment|yield|return|compare|finance|mortgage|risk)/iu,
  FORGE: /(?:تطوير مشروع|هندس|بناء مجمع|مخطط معماري|تصميم مبن|develop|architect|construction|structural|design a building)/iu,
  CIVIC: /(?:المدينة|عمران|تخطيط حضري|الحي السكني|بنية تحتية|urban|city|municipal|zoning)/iu,
  PULSE: /(?:منزل|بيت|شقة|فيلا|عقار|سكن|عائل|احتاج|أريد|ابحث|أبحث|home|house|apartment|villa|property|family|find)/iu,
} satisfies Record<AgentWorld, RegExp>;
const sequence: readonly AgentWorld[] = ["NEXUS","HABITAT","PRISM","FORGE","CIVIC","PULSE"];

const message = (ar: string, en: string): Localized => ({ar,en});

/** No free-form text becomes a privileged tool instruction. */
export function planAgentRequest(raw: string): AgentPlan {
  const text = raw.trim().slice(0, 400);
  if (!text) return {world:null,stage:null,outcome:"CLARIFY",requestedText:text,summary:message("اكتب هدفًا عقاريًا واضحًا أولًا.","Please enter a specific real-estate goal."),steps:[],organ:"none",humanReview:false};
  if (sensitiveAction.test(text)) return {
    world:"NEXUS",stage:null,outcome:"DENIED",requestedText:text,
    summary:message("هذه عملية ملزمة؛ الوكيل التجريبي لا يوقّع أو يدفع أو يقدّم عروضًا. تتطلب هوية وصلاحية وموافقة واعتمادًا في الخادم.","This is a consequential action. The preview agent cannot sign, pay or submit offers; server authorization and explicit human approval are required."),
    steps:[message("وقف التنفيذ عند بوابة السلطة البشرية.","Stopped at the human-authority boundary.")],
    organ:"iam · compliance · transaction",humanReview:true
  };
  const worldId = sequence.find((world) => topic[world].test(text));
  if (!worldId) return {
    world:null,stage:null,outcome:"CLARIFY",requestedText:text,
    summary:message("لم أحدد هدفًا عقاريًا بأمان. صِف السكن أو العقار أو الاستثمار أو الإدارة الذي تقصده.","I cannot safely classify the request. Describe a housing, property, investment or management goal."),
    steps:[message("لا تفترض النية أو البيانات الشخصية.","Do not infer personal details or intent.")],
    organ:"intent",humanReview:false
  };
  return planWorld(worldId, text);
}

export function planWorld(worldId: AgentWorld, requestedText = ""): AgentPlan {
  const w = OULA_WORLDS.find((candidate) => candidate.id === worldId);
  if (!w) throw new Error("Unsupported OULA world");
  if (!w.stage) return {
    world:worldId,stage:null,outcome:"FUTURE",requestedText,
    summary:message("عالم "+w.title.ar+" ضمن الرؤية، ولم يُربط بمحرك تنفيذي معتمد بعد.",w.title.en+" is a future capability with no approved execution engine."),
    steps:[message("عرض الفكرة فقط؛ لا تحليل هندسي أو حضري حقيقي.","Concept only — no real engineering or city analysis.")],
    organ:w.organ,humanReview:false
  };
  return {
    world:worldId,stage:w.stage,outcome:"SANDBOX",requestedText,
    summary:message("سأفتح مساحة "+w.title.ar+" التجريبية. هذه خطة تنقّل لا تنفيذ عقاري.", "I can open the "+w.title.en+" preview. This is navigation, not a property action."),
    steps:[
      message("فهم الهدف بصورة محدودة دون استنتاج معلومات شخصية.","Classify the goal without inferring personal facts."),
      message("ربط الطلب بأعضاء جسم عُلا: "+w.organ,"Map the request to OULA organs: "+w.organ),
      message("عرض القسم التجريبي — لا استدعاءات API أو كتابة على الخادم.","Open the demo section — no API calls or server mutations.")
    ],organ:w.organ,humanReview:worldId==="NEXUS"
  };
}
