import type { DemoProperty } from "../lib/model";

type Language = "ar" | "en";

/**
 * UI-only translations for explicitly fictional Riyadh fixtures.
 * Never use this map to translate or infer authoritative backend evidence.
 */
const demoLabels: Readonly<Record<string, string>> = {
  "الرياض": "Riyadh",
  "الملقا": "Al Malqa",
  "حطين": "Hittin",
  "الصحافة": "As Sahafah",
  "شقة": "Apartment",
};

const demoStories: Readonly<Record<string, string>> = {
  "demo-r01": "Flexible spaces for a family seeking stability and everyday comfort.",
  "demo-r02": "A balanced neighborhood setting with room to support everyday quality of life.",
  "demo-r03": "A relatively budget-conscious option, based on fictional data only.",
};

const demoReasons: Readonly<Record<string, string>> = {
  "لا يستوفي أحد الاشتراطات الإلزامية": "Does not meet one or more mandatory constraints",
  "مطابقة الحي المفضل": "Preferred district matches",
  "يلبي عدد الغرف والمساحة المطلوبة": "Meets the required bedroom count and floor area",
  "نموذج توضيحي، لا يمثل تقييم LifeFit الحقيقي": "Illustrative model only — not a real LifeFit assessment",
};

export function demoLabel(lang: Language, value: string): string {
  return lang === "ar" ? value : (demoLabels[value] ?? value);
}

export function demoStory(lang: Language, property: DemoProperty): string {
  return lang === "ar" ? property.story : (demoStories[property.id] ?? property.story);
}

/** Translate existing demo explanation text without recomputing or changing the demo score. */
export function demoReason(lang: Language, reason: string): string {
  if (lang === "ar") return reason;
  const budget = /^هامش الميزانية الافتراضي: ([0-9]+)٪$/.exec(reason);
  if (budget) return `Illustrative budget headroom: ${budget[1]}%`;
  return demoReasons[reason] ?? reason;
}
