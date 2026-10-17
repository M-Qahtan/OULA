import type { AgentWorld, Localized } from "./personal-agent";

export type WorkspaceView =
  | "overview"
  | "intake"
  | "intent"
  | "matching"
  | "passport"
  | "decision"
  | "deal"
  | "guardian"
  | "studio";
export const worlds: readonly {
  id: AgentWorld;
  name: Localized;
  line: Localized;
  accent: string;
  image: string;
  view: WorkspaceView;
  future?: boolean;
}[] = [
  {
    id: "PULSE",
    name: { ar: "نبض", en: "Pulse" },
    line: {
      ar: "اكتشف المكان الذي يشبه حياتك",
      en: "Find the place that fits your life",
    },
    accent: "#61bbff",
    image: "pulse",
    view: "intent",
  },
  {
    id: "HABITAT",
    name: { ar: "موئل", en: "Habitat" },
    line: {
      ar: "رعاية الأصول، وراحة السكان",
      en: "Care for assets. Enrich living.",
    },
    accent: "#40d8c3",
    image: "habitat",
    view: "intake",
  },
  {
    id: "FORGE",
    name: { ar: "هندسة", en: "Forge" },
    line: { ar: "من الفكرة إلى المكان", en: "From an idea to a place" },
    accent: "#40d6f7",
    image: "forge",
    view: "studio",
    future: true,
  },
  {
    id: "CIVIC",
    name: { ar: "مدن", en: "Civic" },
    line: {
      ar: "فهم المكان، وتحسين الحياة",
      en: "Understand place. Improve life.",
    },
    accent: "#be8aff",
    image: "civic",
    view: "studio",
    future: true,
  },
  {
    id: "NEXUS",
    name: { ar: "وصل", en: "Nexus" },
    line: { ar: "وضوح في كل خطوة", en: "Clarity at every step" },
    accent: "#d4a17b",
    image: "habitat",
    view: "deal",
  },
];
export const roles: readonly {
  id: string;
  name: Localized;
  world: AgentWorld;
  tools: readonly Localized[];
}[] = [
  {
    id: "individual",
    name: { ar: "فرد / باحث عن سكن", en: "Home seeker" },
    world: "PULSE",
    tools: [
      { ar: "منزل يناسب عائلتي", en: "A home for my family" },
      { ar: "مقارنة الخيارات", en: "Compare my options" },
    ],
  },
  {
    id: "tenant",
    name: { ar: "مستأجر", en: "Tenant" },
    world: "PULSE",
    tools: [
      { ar: "احتياج الإيجار", en: "Rental needs" },
      { ar: "استكشاف المكان", en: "Explore places" },
    ],
  },
  {
    id: "owner",
    name: { ar: "مالك عقار", en: "Property owner" },
    world: "HABITAT",
    tools: [
      { ar: "حالة عقاري", en: "My property condition" },
      { ar: "الالتزامات والصيانة", en: "Obligations and maintenance" },
    ],
  },
  {
    id: "manager",
    name: { ar: "مدير أملاك", en: "Property manager" },
    world: "HABITAT",
    tools: [
      { ar: "حالة الأصول", en: "Asset condition" },
      { ar: "مراجعة التوصيات التشغيلية", en: "Review operational advice" },
    ],
  },
  {
    id: "broker",
    name: { ar: "وسيط / مكتب عقاري", en: "Broker / agency" },
    world: "NEXUS",
    tools: [
      { ar: "رحلة العميل", en: "Client journey" },
      { ar: "متابعة الصفقة", en: "Track a transaction" },
    ],
  },
  {
    id: "investor",
    name: { ar: "مستثمر / صندوق", en: "Investor / fund" },
    world: "NEXUS",
    tools: [
      { ar: "بصيرة العقار", en: "Property insight" },
      { ar: "مخاطر القرار", en: "Decision risks" },
    ],
  },
  {
    id: "funder",
    name: { ar: "ممّول", en: "Funder" },
    world: "NEXUS",
    tools: [
      { ar: "مراجعة الأصل", en: "Review an asset" },
      { ar: "الأدلة والافتراضات", en: "Evidence and assumptions" },
    ],
  },
  {
    id: "bank",
    name: { ar: "بنك / جهة تمويل", en: "Bank / lender" },
    world: "NEXUS",
    tools: [
      { ar: "جواز العقار", en: "Property passport" },
      { ar: "دعم قرار التمويل", en: "Financing decision support" },
    ],
  },
  {
    id: "engineer",
    name: { ar: "مهندس", en: "Engineer" },
    world: "FORGE",
    tools: [
      { ar: "طبقات النموذج", en: "Model layers" },
      { ar: "الدراسة الشمسية", en: "Solar study" },
    ],
  },
  {
    id: "lead-engineer",
    name: { ar: "استشاري / كبير مهندسين", en: "Consultant / lead engineer" },
    world: "FORGE",
    tools: [
      { ar: "مراجعة التصميم", en: "Design review" },
      { ar: "القيود والاشتراطات", en: "Constraints and requirements" },
    ],
  },
  {
    id: "developer",
    name: { ar: "مطور عقاري", en: "Developer" },
    world: "FORGE",
    tools: [
      { ar: "مفهوم التطوير", en: "Development concept" },
      { ar: "مراجعة المشروع", en: "Project review" },
    ],
  },
  {
    id: "government",
    name: { ar: "حكومة / مدينة", en: "Government / city" },
    world: "CIVIC",
    tools: [
      { ar: "طبقات المكان", en: "Place layers" },
      { ar: "سيناريوهات المدينة", en: "City scenarios" },
    ],
  },
];
export const views: Readonly<Record<WorkspaceView, Localized>> = {
  intake: { ar: "إضافة عقار", en: "Add a property" },
  overview: { ar: "الرئيسية", en: "Overview" },
  intent: { ar: "احتياجي", en: "My intent" },
  matching: { ar: "استكشاف", en: "Explore" },
  passport: { ar: "جواز العقار", en: "Property passport" },
  decision: { ar: "دعم القرار", en: "Decision support" },
  deal: { ar: "غرفة الصفقة", en: "Deal room" },
  guardian: { ar: "إدارة الأملاك", en: "Property care" },
  studio: { ar: "استوديو الرؤية", en: "Concept studio" },
};
export function worldNavigation(id: AgentWorld): readonly WorkspaceView[] {
  if (id === "HABITAT")
    return ["overview", "intake", "guardian", "passport", "deal"];
  if (id === "FORGE" || id === "CIVIC")
    return ["overview", "studio", "passport"];
  if (id === "PRISM") return ["overview", "decision", "passport", "matching"];
  if (id === "NEXUS") return ["overview", "deal", "intent", "passport"];
  return ["overview", "intent", "matching", "passport", "decision", "deal"];
}
