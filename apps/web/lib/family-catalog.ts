import {
  OULA_WORLDS,
  type Localized,
  type OulaLanguage,
  type OulaWorldId,
} from "./gateway";
import { roles, views, type WorkspaceView } from "./workspaces";

export type Branch = Readonly<{
  id: string;
  name: Localized;
  target?: Readonly<{ view: WorkspaceView; focus: string }>;
}>;
export type FamilySection = Readonly<{
  id: string;
  name: Localized;
  description: Localized;
  requires: Localized;
  branches: readonly Branch[];
}>;
type Entry = readonly [string, string, string, WorkspaceView?, string?];
const text = (ar: string, en: string): Localized => ({ ar, en });
function section(
  id: string,
  ar: string,
  en: string,
  description: Localized,
  requires: Localized,
  entries: readonly Entry[],
): FamilySection {
  return {
    id,
    name: text(ar, en),
    description,
    requires,
    branches: entries.map(([id, ar, en, view, focus]) => ({
      id,
      name: text(ar, en),
      ...(view && focus ? { target: { view, focus } } : {}),
    })),
  };
}
const needs = text(
  "حدد احتياجك وراجع الميزانية والحي والغرف قبل حفظ الطلب والمطابقة.",
  "Review your needs, budget, district and bedrooms before saving and matching.",
);
const needsGate = text(
  "جلسة مصرح بها وموافقة معالجة الاحتياجات عند الحفظ.",
  "An authorized session and needs-processing consent before saving.",
);
const passport = text(
  "اقرأ هوية الأصل ومصادر معلوماته، وميّز المعلن عن المحقق والناقص.",
  "Read the asset's identity and sources; distinguish declared, verified and missing facts.",
);
const passportGate = text(
  "معرف العقار وصلاحية قراءة جوازه داخل مساحة العمل.",
  "A property ID and passport-read permission in this workspace.",
);
const care = text(
  "سجّل الالتزام ومصدره، وقيّم مواعيده، ثم راجع الإجراء ووثّق المعالجة بإقرارك.",
  "Record a declared obligation, assess its deadline, and personally review and document resolution.",
);
const careGate = text(
  "عقار مسجل في المتابعة وصلاحيات الإدارة والتقييم والتوثيق المناسبة. المعالجة إقرار وليست شهادة فحص.",
  "An enrolled property and management/assessment/completion scopes. Resolution is a declaration, not inspection certification.",
);
const deal = text(
  "اقرأ الصفقة ومرحلتها، ثم راجع المعاينة والعرض قبل التأكيد البشري.",
  "Read the recorded deal and stage, then review viewing and offer actions before personal confirmation.",
);
const dealGate = text(
  "صفقة مصرح بها ومرحلة مناسبة وصلاحيات الأطراف. التسجيل لا يعني قبول الطرف الآخر أو نقل ملكية.",
  "An authorized deal, appropriate stage and party permissions. Recording does not mean counterparty acceptance or title transfer.",
);
const financeGate = text(
  "نموذج مالي مُراجع وبيانات بموافقة أصحابها وتكامل جهة مرخصة قبل التنفيذ.",
  "A reviewed financial model, consented data and a licensed-provider integration before execution.",
);
const engineeringGate = text(
  "محركات متخصصة متحققة ومدخلات موثوقة ومراجعة واعتماد مهني قبل استخدام أي نتيجة.",
  "Validated specialist engines, trusted inputs and professional review before relying on a result.",
);
const cityGate = text(
  "بيانات مكانية مرخصة ومؤرخة وصلاحيات الجهة ومراجعة أثر السيناريو.",
  "Licensed, dated spatial data, institutional authority and scenario-impact review.",
);

/** Public task discovery only. Backend permissions, facts and results remain authoritative. */
export const FAMILY_SECTIONS: Readonly<
  Record<OulaWorldId, readonly FamilySection[]>
> = {
  pulse: [
    section("needs", "احتياجي العقاري", "My property needs", needs, needsGate, [
      [
        "buy",
        "أبحث عن منزل للشراء",
        "Find a home to buy",
        "intent",
        "intent-type",
      ],
      [
        "rent",
        "أبحث عن منزل للإيجار",
        "Find a home to rent",
        "intent",
        "intent-type",
      ],
      [
        "move",
        "أنتقل إلى مكان أنسب",
        "Plan my next move",
        "intent",
        "intent-district",
      ],
    ]),
    section(
      "search",
      "البحث والمقارنة",
      "Search and compare",
      needs,
      needsGate,
      [
        [
          "budget",
          "خيارات تناسب الميزانية",
          "Match my budget",
          "intent",
          "intent-budget",
        ],
        [
          "district",
          "ابحث في الحي المناسب",
          "Find the right district",
          "intent",
          "intent-district",
        ],
        [
          "family",
          "احتياجات الأسرة",
          "Household requirements",
          "intent",
          "intent-bedrooms",
        ],
      ],
    ),
    section(
      "passport",
      "جواز العقار",
      "Property passport",
      passport,
      passportGate,
      [
        ["identity", "هوية العقار", "Asset identity", "passport", "record-id"],
        [
          "evidence",
          "المصادر والموثوقية",
          "Sources and trust",
          "passport",
          "record-id",
        ],
        [
          "gaps",
          "ما الذي نحتاج معرفته؟",
          "Identify evidence gaps",
          "passport",
          "record-id",
        ],
      ],
    ),
    section(
      "decision",
      "القرار والمعاينة",
      "Decision and viewing",
      deal,
      dealGate,
      [
        [
          "review",
          "راجع التوصية",
          "Review a recommendation",
          "intent",
          "intent-budget",
        ],
        ["visit", "رتّب المعاينة", "Request a viewing", "deal", "deal-record"],
        [
          "offer",
          "قدّم عرضًا بعد المراجعة",
          "Review an offer",
          "deal",
          "deal-record",
        ],
      ],
    ),
    section(
      "financing",
      "القدرة المالية والتمويل",
      "Affordability and financing",
      text(
        "افهم الافتراضات المالية والتزاماتك قبل مخاطبة جهة التمويل.",
        "Understand financial assumptions and obligations before approaching a lender.",
      ),
      financeGate,
      [
        ["affordability", "الميزانية والالتزامات", "Budget and obligations"],
        ["lenders", "مقارنة جهات التمويل", "Compare lenders"],
        ["application", "تجهيز طلب التمويل", "Prepare an application"],
      ],
    ),
    section(
      "life",
      "الحياة حول المكان",
      "Life around a place",
      text(
        "قرب المدرسة والعمل والخدمات جزء من اختيار المكان.",
        "Schools, work and nearby services are part of choosing a place.",
      ),
      text(
        "مصادر مكانية مرخصة وتفضيلات اختيارية بموافقتك.",
        "Licensed place data and optional, consented preferences.",
      ),
      [
        ["services", "المدرسة والخدمات", "Schools and services"],
        ["commute", "التنقل اليومي", "Daily journeys"],
        ["preferences", "ذاكرة تفضيلاتي", "My preference memory"],
      ],
    ),
  ],
  habitat: [
    section(
      "assets",
      "عقاراتي وأصولي",
      "My properties and assets",
      text(
        "سجّل الأصل ثم أعد قراءته، وافصل بيانات العقار عن مسودة الإعلان الخاصة.",
        "Record and reopen the asset, keeping property facts separate from its private listing draft.",
      ),
      text(
        "جلسة مالك أو مدير مخوّل وصلاحيات إنشاء العقار وقراءته.",
        "An authorized owner/manager session with property create/read scopes.",
      ),
      [
        [
          "register",
          "تسجيل عقار",
          "Register a property",
          "intake",
          "property-kind",
        ],
        [
          "open",
          "فتح عقار محفوظ",
          "Open a saved property",
          "intake",
          "saved-property",
        ],
        [
          "listing",
          "مسودة إعلان خاصة",
          "Private listing draft",
          "intake",
          "saved-property",
        ],
      ],
    ),
    section(
      "guardian",
      "الحارس والصيانة",
      "Guardian and maintenance",
      care,
      careGate,
      [
        [
          "condition",
          "حالة العقار",
          "Property condition",
          "guardian",
          "record-id",
        ],
        [
          "priorities",
          "الأولويات والتوصيات",
          "Priorities and advice",
          "guardian",
          "record-id",
        ],
        [
          "resolution",
          "توثيق المعالجة",
          "Record resolution",
          "guardian",
          "record-id",
        ],
      ],
    ),
    section(
      "obligations",
      "الالتزامات والمواعيد",
      "Obligations and deadlines",
      care,
      careGate,
      [
        ["add", "إضافة التزام", "Add an obligation", "guardian", "record-id"],
        [
          "due",
          "المواعيد والتنبيهات",
          "Deadlines and signals",
          "guardian",
          "record-id",
        ],
        [
          "history",
          "سجل المعالجة",
          "Resolution history",
          "guardian",
          "record-id",
        ],
      ],
    ),
    section(
      "leases",
      "العقود والإشغال",
      "Leases and occupancy",
      text(
        "اربط عقد الإيجار بالأطراف والوحدة وحالته وتواريخه.",
        "Link the lease to parties, unit, status and effective dates.",
      ),
      text(
        "عقود وموافقات وصلاحيات قراءة وتحديث مع فصل المشاهدة عن التصرف.",
        "Authorized lease records, consent and scoped read/update operations.",
      ),
      [
        ["contracts", "عقودي الحالية", "Current leases"],
        ["occupancy", "الإشغال والوحدات", "Occupancy and units"],
        ["renewal", "التجديد والإنهاء", "Renewal and termination"],
      ],
    ),
    section(
      "portfolio",
      "المحفظة والتحصيل",
      "Portfolio and collections",
      text(
        "اجمع وضع الأصول وملاحظات التحصيل مع مصدر كل سجل.",
        "Review assets and collection observations with each record's source.",
      ),
      financeGate,
      [
        ["overview", "نظرة على المحفظة", "Portfolio overview"],
        ["income", "الإيرادات المسجلة", "Recorded income"],
        ["costs", "المصاريف والالتزامات", "Costs and commitments"],
      ],
    ),
    section(
      "documents",
      "هوية الأصل وأدلته",
      "Asset identity and evidence",
      passport,
      passportGate,
      [
        ["passport", "جواز الأصل", "Asset passport", "passport", "record-id"],
        [
          "sources",
          "مصادر المعلومات",
          "Information sources",
          "passport",
          "record-id",
        ],
        [
          "missing",
          "المعلومات الناقصة",
          "Missing evidence",
          "passport",
          "record-id",
        ],
      ],
    ),
  ],
  nexus: [
    section("deals", "غرفة الصفقة", "Deal room", deal, dealGate, [
      ["open", "فتح صفقة محفوظة", "Open a saved deal", "deal", "deal-record"],
      ["viewing", "المعاينة", "Viewing request", "deal", "deal-record"],
      [
        "offer",
        "العرض والتفاوض",
        "Offer and negotiation",
        "deal",
        "deal-record",
      ],
    ]),
    section(
      "office",
      "المكتب والوسطاء",
      "Office and brokers",
      text(
        "نظّم أعضاء المكتب والعملاء والعمل المشترك مع تفويضات محددة.",
        "Coordinate office membership, clients and collaboration with explicit mandates.",
      ),
      text(
        "عضوية مؤسسية ودعوات مقبولة وصلاحيات للمكتب والصفقة.",
        "Institutional membership, accepted invitations and scoped office/deal authority.",
      ),
      [
        ["team", "أعضاء المكتب", "Office team"],
        ["clients", "علاقات العملاء", "Client relationships"],
        ["commissions", "العمولات والاتفاقات", "Commissions and agreements"],
      ],
    ),
    section(
      "investment",
      "الاستثمار ومساحة القرار",
      "Investment and decisions",
      passport,
      passportGate,
      [
        [
          "asset",
          "اقرأ الأصل قبل الاستثمار",
          "Inspect the asset",
          "passport",
          "record-id",
        ],
        [
          "criteria",
          "حدد معايير المقارنة",
          "Set comparison criteria",
          "intent",
          "intent-budget",
        ],
        [
          "evidence",
          "مراجعة أدلة القرار",
          "Review decision evidence",
          "intent",
          "intent-budget",
        ],
      ],
    ),
    section(
      "finance",
      "التمويل والتأمين",
      "Finance and insurance",
      text(
        "قارن الخيارات الموثقة وافصل العرض عن قبول الجهة المرخصة.",
        "Compare documented options; distinguish an offer from licensed-provider acceptance.",
      ),
      financeGate,
      [
        ["funding", "خيارات التمويل", "Funding options"],
        ["insurance", "متطلبات التأمين", "Insurance requirements"],
        ["installments", "الأقساط والالتزامات", "Installments and obligations"],
      ],
    ),
    section(
      "mandates",
      "العقود والتفويض",
      "Contracts and mandates",
      text(
        "اربط كل تصرف بطرف مفوض ونطاق واضح ومدة سريان.",
        "Link every action to an authorized party, explicit scope and effective period.",
      ),
      text(
        "هوية الأطراف وتفويض صالح وتكامل توقيع معتمد عند تفعيله.",
        "Verified party identity, a valid mandate and an approved signing integration when enabled.",
      ),
      [
        ["authority", "من يملك التصرف؟", "Authority to act"],
        ["documents", "مستندات الاتفاق", "Agreement documents"],
        ["approvals", "الموافقات والتوقيع", "Approvals and signing"],
      ],
    ),
    section(
      "partners",
      "الشركاء والفرص",
      "Partners and opportunities",
      text(
        "نسّق التعاون والفرص الخاصة بموافقة الأطراف ومصدر المعلومات.",
        "Coordinate partners and private opportunities with consent and provenance.",
      ),
      text(
        "موافقة الأطراف وتكاملات موثوقة وحدود مشاركة محددة.",
        "Party consent, trusted integrations and explicit sharing boundaries.",
      ),
      [
        ["providers", "مقدمو الخدمات", "Service providers"],
        ["opportunities", "الفرص الخاصة", "Private opportunities"],
        ["collaboration", "التعاون المهني", "Professional collaboration"],
      ],
    ),
  ],
  forge: [
    section(
      "bim",
      "النماذج الهندسية",
      "Engineering models",
      text(
        "اربط إصدارات النموذج والمستندات بالأصل المخطط.",
        "Link versioned models and documents to the planned asset.",
      ),
      engineeringGate,
      [
        ["model", "نموذج المشروع", "Project model"],
        ["versions", "الإصدارات والتغييرات", "Versions and changes"],
        ["clashes", "تعارضات التخصصات", "Discipline clashes"],
      ],
    ),
    section(
      "simulation",
      "المحاكاة العلمية",
      "Scientific simulation",
      text(
        "افصل المشهد البصري عن نتائج نموذج علمي مُعاير.",
        "Separate visual scenes from calibrated scientific-model results.",
      ),
      engineeringGate,
      [
        ["wind", "الرياح وتدفق الهواء", "Wind and airflow"],
        ["solar", "الشمس والظلال", "Solar and shadow"],
        ["compare", "مقارنة المحاكاة", "Compare simulations"],
      ],
    ),
    section(
      "structure",
      "الإنشاء والأنظمة",
      "Structure and systems",
      text(
        "راجع افتراضات الأحمال والمواد وتكامل الأنظمة.",
        "Review assumptions for loads, materials and building systems.",
      ),
      engineeringGate,
      [
        ["loads", "الأحمال والإنشاء", "Loads and structure"],
        ["systems", "الأنظمة الكهروميكانيكية", "Building systems"],
        ["materials", "المواد والمواصفات", "Materials and specifications"],
      ],
    ),
    section(
      "environment",
      "البيئة والاستدامة",
      "Environment and sustainability",
      text(
        "افهم حدود بيانات الطاقة والمياه والأثر البيئي.",
        "Understand the limits of energy, water and environmental-impact data.",
      ),
      engineeringGate,
      [
        ["energy", "الطاقة والكفاءة", "Energy and efficiency"],
        ["water", "المياه والموارد", "Water and resources"],
        ["carbon", "الأثر البيئي", "Environmental impact"],
      ],
    ),
    section(
      "delivery",
      "التنفيذ والجداول",
      "Construction delivery",
      text(
        "اربط البرنامج والتكلفة والتغييرات بالمراجعة البشرية.",
        "Connect schedule, cost and changes to human review.",
      ),
      engineeringGate,
      [
        ["schedule", "البرنامج الزمني", "Delivery schedule"],
        ["cost", "التكلفة والتغييرات", "Cost and changes"],
        ["site", "الموقع والجودة", "Site and quality"],
      ],
    ),
    section(
      "compliance",
      "الأكواد والمستندات",
      "Codes and documents",
      text(
        "اعرض متطلبات الكود وإصداره والمستندات المؤيدة للمراجعة.",
        "Expose code versions, requirements and supporting review documents.",
      ),
      engineeringGate,
      [
        ["codes", "متطلبات الأكواد", "Code requirements"],
        ["permits", "التصاريح والاعتمادات", "Permits and approvals"],
        ["handover", "مستندات التسليم", "Handover documents"],
      ],
    ),
  ],
  civic: [
    section(
      "places",
      "المكان والأحياء",
      "Places and districts",
      text(
        "افهم الحي وشبكة خدماته ومصدر كل مؤشر.",
        "Understand district services and the provenance of each indicator.",
      ),
      cityGate,
      [
        ["districts", "الأحياء والمجتمعات", "Districts and communities"],
        ["services", "إتاحة الخدمات", "Service availability"],
        ["equity", "عدالة الوصول", "Access and equity"],
      ],
    ),
    section(
      "land-use",
      "الأراضي والتنمية",
      "Land use and development",
      text(
        "قارن سيناريوهات استخدام الأرض ضمن ضوابط الجهة.",
        "Compare land-use scenarios within institutional rules.",
      ),
      cityGate,
      [
        ["uses", "استخدامات الأراضي", "Land uses"],
        ["density", "الكثافة والتنمية", "Density and development"],
        ["alternatives", "بدائل المخطط", "Planning alternatives"],
      ],
    ),
    section(
      "mobility",
      "الحركة والوصول",
      "Mobility and access",
      text(
        "راجع الوصول والتنقل دون اختلاق بيانات حركة مباشرة.",
        "Review access and mobility without inventing live traffic data.",
      ),
      cityGate,
      [
        ["access", "الوصول والتنقل", "Access and journeys"],
        ["transit", "النقل العام", "Public transport"],
        ["walkability", "المشي والحياة اليومية", "Walkability and daily life"],
      ],
    ),
    section(
      "infrastructure",
      "البنية التحتية",
      "Infrastructure",
      text(
        "اربط الشبكات وسعتها ومراحل تطويرها بالأدلة.",
        "Link infrastructure networks, capacity and delivery phases to evidence.",
      ),
      cityGate,
      [
        ["networks", "الشبكات والمرافق", "Networks and utilities"],
        ["capacity", "السعة والطلب", "Capacity and demand"],
        ["phasing", "مراحل التطوير", "Development phases"],
      ],
    ),
    section(
      "policy",
      "السياسات والسيناريوهات",
      "Policy and scenarios",
      text(
        "قارن أثر القرارات بافتراضات قابلة للمراجعة.",
        "Compare decision impacts with reviewable assumptions.",
      ),
      cityGate,
      [
        ["rules", "اشتراطات التخطيط", "Planning rules"],
        ["scenarios", "سيناريوهات التنمية", "Development scenarios"],
        ["impact", "الأثر والمتابعة", "Impact and monitoring"],
      ],
    ),
    section(
      "quality",
      "جودة الحياة",
      "Quality of life",
      text(
        "اربط مؤشرات جودة الحياة بمصادرها وحدود تفسيرها.",
        "Link quality-of-life indicators to their sources and interpretation limits.",
      ),
      cityGate,
      [
        ["indicators", "المؤشرات الحضرية", "Urban indicators"],
        ["environment", "البيئة والمجال العام", "Environment and public realm"],
        ["participation", "المشاركة المجتمعية", "Community participation"],
      ],
    ),
  ],
};

export type FamilyContext = {
  world: OulaWorldId;
  section?: string;
  branch?: string;
  role: string;
  lang: OulaLanguage;
};
export function readFamilyContext(
  params: URLSearchParams,
  world: OulaWorldId,
): FamilyContext {
  const found = FAMILY_SECTIONS[world].find(
    (s) => s.id === params.get("section"),
  );
  const branch = found?.branches.find((b) => b.id === params.get("branch"));
  const role = roles.some((r) => r.id === params.get("role"))
    ? params.get("role")!
    : (roles.find((r) => r.world === world.toUpperCase())?.id ?? "individual");
  return {
    world,
    section: found?.id,
    branch: branch?.id,
    role,
    lang: params.get("lang") === "en" ? "en" : "ar",
  };
}
export function getContextBranch(context: FamilyContext) {
  const section = FAMILY_SECTIONS[context.world].find(
    (s) => s.id === context.section,
  );
  return {
    section,
    branch: section?.branches.find((b) => b.id === context.branch),
  };
}
export function familyHref(context: FamilyContext) {
  const params = new URLSearchParams({
    role: context.role,
    lang: context.lang,
  });
  if (context.section) params.set("section", context.section);
  if (context.branch) params.set("branch", context.branch);
  return `/gateway/${context.world}?${params}`;
}
export function workspaceHref(context: FamilyContext): string | null {
  const { branch } = getContextBranch(context);
  if (!branch?.target) return null;
  const params = new URLSearchParams({
    world: context.world.toUpperCase(),
    role: context.role,
    lang: context.lang,
    view: branch.target.view,
    section: context.section!,
    branch: branch.id,
    connection: "server",
  });
  return "/workspace?" + params;
}
export const normalizeSearch = (s: string) =>
  s
    .normalize("NFKD")
    .replace(/[\u064b-\u065f\u0670\u0640]/g, "")
    .replace(/[أإآ]/g, "ا")
    .toLowerCase()
    .trim();
export function searchFamilies(query: string) {
  const q = normalizeSearch(query);
  if (!q) return [];
  return OULA_WORLDS.flatMap((world) =>
    FAMILY_SECTIONS[world.id].flatMap((section) =>
      section.branches.map((branch) => {
        const exact = [world.id, world.arabicName, world.englishName].map(
          normalizeSearch,
        );
        const labels = [
          section.name.ar,
          section.name.en,
          branch.name.ar,
          branch.name.en,
          section.id,
          branch.id,
        ].map(normalizeSearch);
        const full = normalizeSearch(
          section.description.ar + section.description.en,
        );
        const score = exact.includes(q)
          ? 100
          : labels.includes(q)
            ? 60
            : labels.some((s) => s.includes(q))
              ? 30
              : full.includes(q)
                ? 1
                : 0;
        return { world, section, branch, score };
      }),
    ),
  )
    .filter((r) => r.score > 0)
    .sort((a, b) => b.score - a.score)
    .slice(0, 12);
}
export function safeWorkspaceReturnTo(
  value: string | null | undefined,
): string {
  const fallback = "/workspace?connection=server";
  if (!value || value.length > 1800 || /[\\\u0000-\u0020\u007f]/.test(value))
    return fallback;
  let url: URL;
  try {
    url = new URL(value, "https://oula.invalid");
  } catch {
    return fallback;
  }
  if (
    url.origin !== "https://oula.invalid" ||
    url.pathname !== "/workspace" ||
    !value.startsWith("/workspace")
  )
    return fallback;
  const world =
    OULA_WORLDS.find(
      (w) => w.id.toUpperCase() === url.searchParams.get("world"),
    )?.id ?? "pulse";
  const context = readFamilyContext(url.searchParams, world);
  const result = new URLSearchParams({
    world: world.toUpperCase(),
    role: context.role,
    lang: context.lang,
    connection: "server",
  });
  const view = url.searchParams.get("view");
  if (view && Object.hasOwn(views, view)) result.set("view", view);
  if (context.section) result.set("section", context.section);
  if (context.branch) result.set("branch", context.branch);
  return "/workspace?" + result;
}
