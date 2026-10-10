/** Presentation-only structures. Canonical operational truth stays in the Java backend. */
export type TruthStatus = "DECLARED" | "OBSERVED" | "CALCULATED" | "ESTIMATED" | "AI_INFERRED" | "DISPUTED" | "EXPIRED";
export type EvidenceLevel = "DEMO" | "SANDBOX" | "LIVE";

export type DemoProperty = Readonly<{
  id: string;
  district: string;
  city: string;
  kind: string;
  priceSar: number;
  areaSqm: number;
  bedrooms: number;
  yearlyRentSar?: number;
  evidence: TruthStatus;
  story: string;
  traits: readonly string[];
}>;

/** Entirely fictional example data for UI exploration. Never display as official verified inventory. */
export const demoProperties: readonly DemoProperty[] = [
  { id: "demo-r01", district: "الملقا", city: "الرياض", kind: "شقة", priceSar: 1090000, areaSqm: 172, bedrooms: 3, yearlyRentSar: 75000, evidence: "DECLARED", story: "مساحات مرنة لعائلة تبحث عن الاستقرار والراحة.", traits: ["قرب الخدمات", "مساحات عائلية", "خصوصية"] },
  { id: "demo-r02", district: "حطين", city: "الرياض", kind: "شقة", priceSar: 1260000, areaSqm: 185, bedrooms: 3, yearlyRentSar: 87000, evidence: "DECLARED", story: "إيقاع حي متوازن ومساحة تعزز جودة الحياة.", traits: ["مساحة أكبر", "خدمات", "تنقل"] },
  { id: "demo-r03", district: "الصحافة", city: "الرياض", kind: "شقة", priceSar: 975000, areaSqm: 150, bedrooms: 2, yearlyRentSar: 66000, evidence: "DECLARED", story: "خيار اقتصادي نسبيًا وفق البيانات الافتراضية فقط.", traits: ["ميزانية", "مرونة", "قرب العمل"] },
];

export type IntentInput = Readonly<{ budgetSar: number; minBedrooms: number; minAreaSqm: number; preferredDistrict?: string }>;
export type MatchExplanation = Readonly<{ property: DemoProperty; score: number; reasons: readonly string[]; blocked: boolean }>;

/** Deterministic DEMO score, not the canonical LifeFit model. Never pass to a live decision without backend. */
export function previewMatch(intent: IntentInput, property: DemoProperty): MatchExplanation {
  const blocked = property.priceSar > intent.budgetSar || property.bedrooms < intent.minBedrooms || property.areaSqm < intent.minAreaSqm;
  if (blocked) return { property, score: 0, reasons: ["لا يستوفي أحد الاشتراطات الإلزامية"], blocked: true };
  const budgetHeadroom = Math.max(0, (intent.budgetSar - property.priceSar) / intent.budgetSar);
  const extraArea = Math.min(1, (property.areaSqm - intent.minAreaSqm) / Math.max(1, intent.minAreaSqm));
  const areaFactor = Math.round(extraArea * 12);
  const locationFactor = property.district === intent.preferredDistrict ? 16 : 0;
  const score = Math.min(100, Math.round(65 + budgetHeadroom * 30 + areaFactor + locationFactor));
  const reasons = [`يلبي عدد الغرف والمساحة المطلوبة`, `هامش الميزانية الافتراضي: ${Math.round(budgetHeadroom * 100)}٪`, `نموذج توضيحي، لا يمثل تقييم LifeFit الحقيقي`];
  if (locationFactor > 0) reasons.unshift("مطابقة الحي المفضل");
  return { property, score, reasons, blocked: false };
}
