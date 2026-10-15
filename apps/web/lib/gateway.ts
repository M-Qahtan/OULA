/**
 * OULA gateway discovery vocabulary.
 * This is presentation/navigation metadata only: authorization and live
 * capability availability are solely decided by the canonical backend.
 */
export type OulaWorldId = "pulse" | "habitat" | "nexus" | "forge" | "civic";
export type OulaLanguage = "ar" | "en";
export type Localized = Readonly<{ ar: string; en: string }>;
export type WorldDefinition = Readonly<{
  id: OulaWorldId;
  color: string;
  englishName: string;
  arabicName: string;
  purpose: Localized;
  capabilities: readonly Localized[];
  maturity: "PREVIEW";
}>;

export const OULA_WORLDS: readonly WorldDefinition[] = [
  {
    id: "pulse",
    color: "#398cff",
    englishName: "PULSE",
    arabicName: "نبض",
    purpose: { ar: "اكتشف المكان الأنسب لحياتك", en: "Discover the place that fits your life" },
    capabilities: [
      { ar: "احتياجات السكن", en: "Housing needs" },
      { ar: "مقارنة العقارات", en: "Property comparison" },
      { ar: "جواز العقار", en: "Property passport" },
    ],
    maturity: "PREVIEW",
  },
  {
    id: "habitat",
    color: "#36c9ad",
    englishName: "HABITAT",
    arabicName: "موئل",
    purpose: { ar: "أدر أملاكك وأصولك بذكاء", en: "Manage property and assets with clarity" },
    capabilities: [
      { ar: "متابعة الأصول", en: "Asset overview" },
      { ar: "المستأجرون والعقود", en: "Tenants and leases" },
      { ar: "الصيانة والالتزامات", en: "Maintenance and obligations" },
    ],
    maturity: "PREVIEW",
  },
  {
    id: "nexus",
    color: "#e6a15b",
    englishName: "NEXUS",
    arabicName: "وَصْل",
    purpose: { ar: "أنجز أعمالك وصفقاتك بثقة", en: "Coordinate deals and professional work" },
    capabilities: [
      { ar: "غرفة الصفقات", en: "Deal room" },
      { ar: "الوسطاء والشركاء", en: "Brokers and partners" },
      { ar: "التمويل والتكامل", en: "Finance and integrations" },
    ],
    maturity: "PREVIEW",
  },
  {
    id: "forge",
    color: "#40c5f3",
    englishName: "FORGE",
    arabicName: "هندسة",
    purpose: { ar: "صمّم وحاكِ وشيّد المستقبل", en: "Design, simulate and build responsibly" },
    capabilities: [
      { ar: "النماذج الهندسية", en: "Engineering models" },
      { ar: "المحاكاة والتحليل", en: "Simulation and analysis" },
      { ar: "مراجعة المخططات", en: "Design review" },
    ],
    maturity: "PREVIEW",
  },
  {
    id: "civic",
    color: "#b483f3",
    englishName: "CIVIC",
    arabicName: "مدن",
    purpose: { ar: "خطّط مدنًا أذكى لحياة أفضل", en: "Plan smarter cities for better living" },
    capabilities: [
      { ar: "المؤشرات المكانية", en: "Spatial indicators" },
      { ar: "السيناريوهات الحضرية", en: "Urban scenarios" },
      { ar: "دعم القرار", en: "Decision support" },
    ],
    maturity: "PREVIEW",
  },
] as const;

/** Deterministic preview routing; not an LLM, an authenticated agent or a user profile. */
export function suggestWorld(text: string): OulaWorldId | null {
  const q = text.trim().toLocaleLowerCase();
  if (!q) return null;
  const rules: readonly [OulaWorldId, readonly string[]][] = [
    ["habitat", ["أملاكي", "عقاراتي", "إيجاراتي", "مستأجر", "تحصيل", "صيانة", "عقودي", "my property", "rent collection", "maintenance"]],
    ["nexus", ["وسيط", "صفقة", "عمولة", "بنك", "تأمين", "تمويل", "عقد بيع", "broker", "deal", "insurance", "mortgage"]],
    ["forge", ["مهندس", "هندسة", "مخطط", "تصميم", "محاكاة", "بناء", "bim", "architect", "engineer", "simulation"]],
    ["civic", ["مدينة", "مدن", "تخطيط حضري", "بنية تحتية", "سياسة", "بلدية", "urban", "infrastructure", "city planning"]],
    ["pulse", ["بيت", "منزل", "شقة", "فيلا", "سكن", "بحث", "إيجار", "شراء", "home", "apartment", "house", "buy", "rent"]],
  ];
  for (const [world, words] of rules) if (words.some(word => q.includes(word))) return world;
  return null;
}

export function getWorld(id: OulaWorldId): WorldDefinition {
  const world = OULA_WORLDS.find(item => item.id === id);
  if (!world) throw new Error("Unknown OULA world: " + id);
  return world;
}
