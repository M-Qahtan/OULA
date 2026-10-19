"use client";
import { ConnectedDealRoom } from "./connected-deal-room";
import { ConnectedPropertyCare } from "./connected-property-care";
import { useEffect, useRef, useState } from "react";
import { z } from "zod";
import { createBrowserOulaApi } from "../lib/browser-api";
import {
  intentInputSchema,
  type HumanDecision,
  type OperationalAdvice,
  type PropertyVitals,
  type Recommendation,
  type RecommendationExplanation,
} from "../lib/api-contracts";
import type {
  IntentCreateRequest,
  IntentResponse,
  MatchRunResponse,
  PropertyPassport,
  TransactionResponse,
} from "../lib/api";
import { demoProperties, previewMatch, type DemoProperty } from "../lib/model";
import type { SessionState } from "../lib/use-oula-session";
import type { WorkspaceView } from "../lib/workspaces";
import {
  errorText,
  Icon,
  StatusBanner,
  word,
  type Language,
} from "./workspace-primitives";

type Props = {
  language: Language;
  view: WorkspaceView;
  mode: "demo" | "server";
  session: SessionState;
  loginHref: string;
  onNavigate: (view: WorkspaceView) => void;
};
type Candidate = {
  id: string;
  name: string;
  score: number;
  confidence?: number;
  demo?: DemoProperty;
};
type Command = {
  body: string;
  intentKey: string;
  matchingKey: string;
  intent?: IntentResponse;
};
type Api = ReturnType<typeof createBrowserOulaApi>;
const newKey = () => crypto.randomUUID();

/** The server and presentation datasets occupy separate, explicitly selected modes. */
export function ConnectedJourney({
  language,
  view,
  mode,
  session,
  loginHref,
  onNavigate,
}: Props) {
  const w = (ar: string, en: string) => word(language, { ar, en });
  const [consent, setConsent] = useState(false);
  const [consentApproved, setConsentApproved] = useState(false);
  const consentKey = useRef<string | null>(null);
  const [input, setInput] = useState<IntentCreateRequest>({
    intentType: "BUY",
    budgetMax: 2000000,
    minimumBedrooms: 3,
    preferredDistricts: ["الملقا"],
  });
  const [district, setDistrict] = useState("الملقا");
  const [candidates, setCandidates] = useState<Candidate[]>([]);
  const [run, setRun] = useState<MatchRunResponse | null>(null);
  const [passport, setPassport] = useState<PropertyPassport | null>(null);
  const [previewProperty, setPreviewProperty] = useState<DemoProperty | null>(
    null,
  );
  const [recordId, setRecordId] = useState("");
  const [vitals, setVitals] = useState<PropertyVitals | null>(null);
  const [advice, setAdvice] = useState<OperationalAdvice | null>(null);
  const [transaction, setTransaction] = useState<TransactionResponse | null>(
    null,
  );
  const [recommendation, setRecommendation] = useState<Recommendation | null>(
    null,
  );
  const [explanation, setExplanation] =
    useState<RecommendationExplanation | null>(null);
  const [decision, setDecision] = useState<HumanDecision | null>(null);
  const [evidenceIds, setEvidenceIds] = useState("");
  const [approved, setApproved] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [validation, setValidation] = useState(false);
  const [searched, setSearched] = useState(false);
  const command = useRef<Command | null>(null);
  const recommendationCommand = useRef<{
    body: string;
    key: string;
    value?: Recommendation;
  } | null>(null);
  const decisionKey = useRef<string | null>(null);
  const locked = useRef(false);
  const controller = useRef<AbortController | null>(null);
  const active = useRef(true);
  const firstField = useRef<HTMLInputElement>(null);
  useEffect(() => {
    active.current = true;
    return () => {
      active.current = false;
      controller.current?.abort();
    };
  }, []);
  const serverReady = mode === "server" && session.status === "authenticated";
  const ready = mode === "demo" || serverReady;
  const can = (scope: string) =>
    mode === "demo" ||
    (session.status === "authenticated" && session.scopes.includes(scope));
  const format = (value: number) =>
    new Intl.NumberFormat(language === "ar" ? "ar-SA" : "en-US").format(value);

  async function perform(work: (api: Api) => Promise<void>) {
    if (locked.current || !ready) return;
    locked.current = true;
    setBusy(true);
    setError(null);
    controller.current = new AbortController();
    try {
      await work(createBrowserOulaApi(controller.current.signal));
    } catch (failure) {
      if (active.current) setError(errorText(failure, language));
    } finally {
      locked.current = false;
      if (active.current) setBusy(false);
    }
  }

  async function findCandidates() {
    const payload = {
      ...input,
      preferredDistricts: district.trim() ? [district.trim()] : [],
    };
    const valid = intentInputSchema.safeParse(payload);
    setValidation(!valid.success);
    if (!valid.success) {
      setError(
        w(
          "أدخل ميزانية أكبر من صفر، وعدد غرف صحيحًا، وحيًا لا يزيد عن 100 حرف.",
          "Enter a positive budget, a whole bedroom count and a district of at most 100 characters.",
        ),
      );
      firstField.current?.focus();
      return;
    }
    await perform(async (api) => {
      if (mode === "demo") {
        const results = demoProperties
          .map((property) => ({
            property,
            match: previewMatch(
              {
                budgetSar: valid.data.budgetMax,
                minBedrooms: valid.data.minimumBedrooms,
                minAreaSqm: 0,
                preferredDistrict: district,
              },
              property,
            ),
          }))
          .filter((item) => !item.match.blocked)
          .sort((a, b) => b.match.score - a.match.score);
        setCandidates(
          results.map(({ property, match }) => ({
            id: property.id,
            name:
              language === "ar"
                ? `${property.kind} — ${property.district}`
                : `Sample home · ${property.id}`,
            score: match.score,
            demo: property,
          })),
        );
      } else {
        const body = JSON.stringify(valid.data);
        if (!command.current || command.current.body !== body)
          command.current = {
            body,
            intentKey: newKey(),
            matchingKey: newKey(),
          };
        const current = command.current;
        const intent =
          current.intent ??
          (await api.createIntent(valid.data, current.intentKey));
        current.intent = intent;
        if (
          intent.workspaceId !==
          (session.status === "authenticated" ? session.workspaceId : "")
        )
          throw new Error("CONTRACT_MISMATCH");
        if (
          session.status === "authenticated" &&
          session.mode === "DEV_TEST_ONLY"
        ) {
          const preparation = await fetch("/api/local/intent", {
            method: "POST",
            signal: controller.current?.signal,
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ intentId: intent.id }),
          });
          if (!preparation.ok) throw new Error("BACKEND_UNAVAILABLE");
        }
        const result = await api.runMatching(intent.id, current.matchingKey);
        if (!active.current) return;
        setRun(result);
        setCandidates(
          result.matches.map((match) => ({
            id: match.propertyId,
            name:
              w("عقار من مساحة العمل", "Workspace property") +
              " · " +
              match.rank,
            score: match.score,
            confidence: match.confidence,
          })),
        );
      }
      if (!active.current) return;
      setSearched(true);
      setPassport(null);
      setPreviewProperty(null);
      setRecommendation(null);
      setExplanation(null);
      setDecision(null);
      setApproved(false);
      recommendationCommand.current = null;
      decisionKey.current = null;
      onNavigate("matching");
    });
  }

  const openPassport = async (candidate: Candidate) =>
    perform(async (api) => {
      if (candidate.demo) {
        setPreviewProperty(candidate.demo);
        setPassport(null);
      } else {
        const result = await api.getPropertyPassport(candidate.id);
        if (!active.current) return;
        setPassport(result);
        setPreviewProperty(null);
      }
      setRecordId(candidate.id);
      onNavigate("passport");
    });

  const loadRecord = async () => {
    if (!z.uuid().safeParse(recordId).success) {
      setValidation(true);
      setError(
        w(
          "أدخل معرف سجل صحيحًا من مساحة عملك.",
          "Enter a valid record ID from your workspace.",
        ),
      );
      firstField.current?.focus();
      return;
    }
    setValidation(false);
    await perform(async (api) => {
      if (view === "deal") {
        const result = await api.getTransaction(recordId);
        if (active.current) setTransaction(result);
      } else if (view === "guardian") {
        // Each read has its own visible error. A failed advisory never conceals an available snapshot.
        const result = await api.getPropertyVitals(recordId);
        if (!active.current) return;
        setVitals(result);
        setAdvice(null);
        const adviceResult = await api.getOperationalAdvice(recordId);
        if (active.current) setAdvice(adviceResult);
      } else {
        const result = await api.getPropertyPassport(recordId);
        if (active.current) setPassport(result);
      }
    });
  };

  const generate = async () => {
    if (!run) return;
    const ids = evidenceIds
      .split(",")
      .map((id) => id.trim())
      .filter(Boolean);
    if (!z.array(z.uuid()).min(1).max(25).safeParse(ids).success) {
      setError(
        w(
          "أدخل معرّف دليل واحد على الأقل، مسموحًا ومحققًا من مصدر معتمد في مساحة العمل.",
          "Enter at least one eligible evidence ID from an authorized, verified workspace source.",
        ),
      );
      return;
    }
    await perform(async (api) => {
      const body = JSON.stringify({ run: run.matchRunId, ids });
      if (
        !recommendationCommand.current ||
        recommendationCommand.current.body !== body
      )
        recommendationCommand.current = { body, key: newKey() };
      const current = recommendationCommand.current;
      const value =
        current.value ??
        (await api.generateRecommendation(run.matchRunId, ids, current.key));
      current.value = value;
      if (!active.current) return;
      setRecommendation(value);
      const details = await api.explainRecommendation(value.recommendationId);
      if (active.current) setExplanation(details);
    });
  };

  const commitDecision = async () => {
    if (!recommendation || !approved || decision) return;
    await perform(async (api) => {
      decisionKey.current ??= newKey();
      const value = await api.recordDecision(
        recommendation.recommendationId,
        recommendation.recommendedPropertyId,
        decisionKey.current,
      );
      if (active.current) setDecision(value);
    });
  };

  const recordForm = (label: string) => (
    <form
      noValidate
      className="connected-form compact"
      onSubmit={(event) => {
        event.preventDefault();
        void loadRecord();
      }}
    >
      <label htmlFor="record-id">{label}</label>
      <div className="record-controls">
        <input
          ref={firstField}
          id="record-id"
          disabled={busy}
          value={recordId}
          onChange={(event) => {
            setRecordId(event.target.value);
            setValidation(false);
            setPassport(null);
            setTransaction(null);
            setVitals(null);
            setAdvice(null);
          }}
          aria-invalid={validation}
          aria-describedby={validation ? "journey-error" : undefined}
          autoComplete="off"
          dir="ltr"
          placeholder="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
          maxLength={36}
        />
        {recordId && (
          <button
            type="button"
            className="glass-button"
            disabled={busy}
            aria-label={w("مسح المعرف", "Clear record ID")}
            onClick={() => {
              setRecordId("");
              setPassport(null);
              setTransaction(null);
              setVitals(null);
              setAdvice(null);
              firstField.current?.focus();
            }}
          >
            ×
          </button>
        )}
        <button className="gold-button" disabled={!ready || busy} type="submit">
          {busy
            ? w("جارٍ القراءة…", "Reading…")
            : w("قراءة السجل", "Read record")}
        </button>
      </div>
    </form>
  );

  return (
    <section
      className="connected-panel"
      aria-label={w("رحلة عُلا", "OULA journey")}
      aria-busy={busy}
    >
      <div className="data-basis">
        <span
          className={"basis-dot " + (mode === "server" ? "server" : "demo")}
        />
        {mode === "server"
          ? w(
              "بيانات مساحة العمل عبر الخدمة — لا تحويل تلقائي إلى بيانات توضيحية",
              "Workspace data from the service — no automatic demo fallback",
            )
          : w(
              "عرض توضيحي · بيانات عقارية افتراضية",
              "Demo · fictional property data",
            )}
      </div>
      {mode === "server" && !serverReady && (
        <StatusBanner tone="error">
          {w(
            "يتطلب هذا القسم جلسة دخول وإعداد اتصال الخدمة.",
            "This section requires a signed-in session and a configured service connection.",
          )}{" "}
          {session.status === "signed-out" && session.localDevAuth ? (
            w(
              "ابدأ جلسة محلية من لوحة عُلا معك.",
              "Start a local session in the OULA assistant panel.",
            )
          ) : (
            <a href={loginHref}>{w("تسجيل الدخول", "Sign in")}</a>
          )}
        </StatusBanner>
      )}
      {error && (
        <div id="journey-error">
          <StatusBanner tone="error">{error}</StatusBanner>
        </div>
      )}
      {view === "intent" && (
        <>
          {mode === "server" && (
            <div className="human-approval">
              <label>
                <input
                  type="checkbox"
                  checked={consentApproved}
                  disabled={busy || consent}
                  onChange={(event) => setConsentApproved(event.target.checked)}
                />
                {w(
                  "أوافق على معالجة سياق احتياجي داخل مساحة العمل لغرض دعم قراري العقاري.",
                  "I consent to processing my needs within this workspace for property decision support.",
                )}
              </label>
              <button
                className="glass-button"
                disabled={
                  !serverReady ||
                  !consentApproved ||
                  busy ||
                  consent ||
                  !can("oula.consent.write")
                }
                onClick={() =>
                  void perform(async (api) => {
                    consentKey.current ??= newKey();
                    const result = await api.decideConsent(
                      {
                        consentCode: "LIFE_CONTEXT_PROCESSING",
                        decision: "GRANTED",
                        reason: "Explicit workspace consent",
                      },
                      consentKey.current,
                    );
                    if (active.current)
                      setConsent(result.decision === "GRANTED");
                  })
                }
              >
                {consent
                  ? w("تم تسجيل الموافقة", "Consent recorded")
                  : w("سجّل الموافقة", "Record consent")}
              </button>
            </div>
          )}
          <h2>
            {w("ما المكان الذي تبحث عنه؟", "What place are you looking for?")}
          </h2>
          <p className="panel-copy">
            {w(
              "ابدأ باحتياجك، ثم قارن الخيارات وأدلتها. تظل أنت صاحب القرار.",
              "Start with your needs, then compare options and their evidence. You keep the decision.",
            )}
          </p>
          <form
            noValidate
            className="connected-form"
            onSubmit={(event) => {
              event.preventDefault();
              void findCandidates();
            }}
          >
            <div className="form-pair">
              <div>
                <label htmlFor="intent-type">{w("أريد", "I want to")}</label>
                <select
                  id="intent-type"
                  disabled={busy}
                  value={input.intentType}
                  onChange={(event) =>
                    setInput({
                      ...input,
                      intentType: event.target.value as "BUY" | "RENT",
                    })
                  }
                >
                  <option value="BUY">{w("الشراء", "Buy")}</option>
                  <option value="RENT">{w("الاستئجار", "Rent")}</option>
                </select>
              </div>
              <div>
                <label htmlFor="intent-budget">
                  {w(
                    "الميزانية القصوى — عملة مساحة العمل",
                    "Maximum budget — workspace currency",
                  )}
                </label>
                <input
                  ref={firstField}
                  id="intent-budget"
                  disabled={busy}
                  type="number"
                  min="1"
                  step="1"
                  value={input.budgetMax}
                  onChange={(event) =>
                    setInput({
                      ...input,
                      budgetMax: Number(event.target.value),
                    })
                  }
                  aria-invalid={validation && !(input.budgetMax > 0)}
                  aria-describedby={validation ? "journey-error" : undefined}
                />
              </div>
            </div>
            <div className="form-pair">
              <div>
                <label htmlFor="intent-bedrooms">
                  {w("أقل عدد غرف نوم", "Minimum bedrooms")}
                </label>
                <input
                  id="intent-bedrooms"
                  disabled={busy}
                  type="number"
                  min="0"
                  step="1"
                  value={input.minimumBedrooms}
                  onChange={(event) =>
                    setInput({
                      ...input,
                      minimumBedrooms: Number(event.target.value),
                    })
                  }
                  aria-invalid={
                    validation &&
                    (!Number.isInteger(input.minimumBedrooms) ||
                      input.minimumBedrooms < 0)
                  }
                  aria-describedby={validation ? "journey-error" : undefined}
                />
              </div>
              <div>
                <label htmlFor="intent-district">
                  {w("الحي المفضل — اختياري", "Preferred district — optional")}
                </label>
                <input
                  id="intent-district"
                  disabled={busy}
                  value={district}
                  maxLength={100}
                  onChange={(event) => setDistrict(event.target.value)}
                />
              </div>
            </div>
            <div className="form-actions">
              <button
                className="gold-button"
                type="submit"
                disabled={
                  !ready ||
                  busy ||
                  (mode === "server" && !consent) ||
                  !can("oula.intent.write") ||
                  !can("oula.matching.run")
                }
              >
                {busy
                  ? w("جارٍ البحث…", "Finding options…")
                  : w("اعثر على خياراتي", "Find my options")}{" "}
                <Icon name="arrow" />
              </button>
              {command.current && (
                <button
                  className="glass-button"
                  type="button"
                  disabled={busy}
                  onClick={() => {
                    command.current = null;
                    setError(null);
                  }}
                >
                  {w("بدء طلب جديد", "Start a new request")}
                </button>
              )}
            </div>
          </form>
        </>
      )}
      {view === "matching" && (
        <>
          <h2>{w("خيارات تبدأ من احتياجك", "Options shaped by your needs")}</h2>
          <p className="panel-copy">
            {w(
              "الملاءمة تدعم المقارنة، ولا تثبت ملكية العقار أو صحة جميع معلوماته.",
              "Fit supports comparison; it does not prove title or verify every property detail.",
            )}
          </p>
          {!searched ? (
            <div className="workspace-empty">
              <Icon name="matching" size={40} />
              <p>
                {w(
                  "حدّد احتياجك لبدء المطابقة.",
                  "Define your intent to start matching.",
                )}
              </p>
              <button
                className="gold-button"
                onClick={() => onNavigate("intent")}
              >
                {w("حدّد احتياجي", "Define my intent")}
              </button>
            </div>
          ) : candidates.length === 0 ? (
            <div className="workspace-empty">
              <p>
                {w(
                  "لا توجد خيارات مطابقة للاشتراطات الحالية.",
                  "No options match your current constraints.",
                )}
              </p>
              <button
                className="glass-button"
                onClick={() => onNavigate("intent")}
              >
                {w("تعديل الاحتياج", "Adjust my intent")}
              </button>
            </div>
          ) : (
            <div className="candidate-grid">
              {candidates.map((candidate) => (
                <article className="candidate" key={candidate.id}>
                  <div className="candidate-symbol">
                    <Icon name="overview" size={38} />
                    <span>
                      {format(candidate.score)} <small>/ 100</small>
                    </span>
                  </div>
                  <h3>{candidate.name}</h3>
                  {candidate.demo ? (
                    <p>
                      {format(candidate.demo.priceSar)}{" "}
                      {w("ر.س · سعر توضيحي", "SAR · example price")}
                    </p>
                  ) : (
                    <p>
                      {w("درجة ثقة المطابقة", "Matching confidence")}:{" "}
                      {format((candidate.confidence ?? 0) * 100)}%
                    </p>
                  )}
                  <small className="record-reference" dir="ltr">
                    {candidate.id}
                  </small>
                  <button
                    className="glass-button"
                    disabled={busy || !can("oula.property.passport.read")}
                    onClick={() => void openPassport(candidate)}
                  >
                    {w("افتح جواز العقار", "Open property passport")}{" "}
                    <Icon name="arrow" size={16} />
                  </button>
                </article>
              ))}
            </div>
          )}
        </>
      )}
      {view === "passport" && (
        <>
          <h2>
            {w(
              "تعرف على العقار، ومصدر كل معلومة",
              "Know the property and each fact's source",
            )}
          </h2>
          {mode === "server" && recordForm(w("معرّف العقار", "Property ID"))}
          {passport ? (
            <>
              <div className="passport-summary">
                <div>
                  <span>{w("نوع الأصل", "Asset type")}</span>
                  <strong>
                    {passport.assetType ?? w("غير مسجل", "Not recorded")}
                  </strong>
                </div>
                <div>
                  <span>{w("الحي", "District")}</span>
                  <strong>{passport.district ?? "—"}</strong>
                </div>
                <div>
                  <span>
                    {w(
                      "السعر المعلن — العملة غير محددة في السجل",
                      "Asking price — currency not specified in this record",
                    )}
                  </span>
                  <strong>
                    {passport.askingPrice == null
                      ? "—"
                      : format(passport.askingPrice)}
                  </strong>
                </div>
                <div>
                  <span>
                    {w(
                      "تغطية المعلومات الموسومة محققة",
                      "Coverage of facts marked verified",
                    )}
                  </span>
                  <strong>
                    {format(passport.verifiedFactCoverage * 100)}%
                  </strong>
                </div>
              </div>
              <p className="panel-copy">
                {w(
                  "نسبة التغطية ليست إثبات ملكية أو تقييمًا معتمدًا. حالات المعرفة أدناه من المصدر.",
                  "Coverage is not proof of title or a certified valuation. Truth statuses below come from the source.",
                )}
              </p>
              <div className="fact-list">
                {passport.facts.length === 0 ? (
                  <p>
                    {w(
                      "لا توجد معلومات مصدرية مسجلة بعد.",
                      "No source-linked facts are recorded yet.",
                    )}
                  </p>
                ) : (
                  passport.facts.map((fact) => (
                    <article key={fact.id}>
                      <div>
                        <strong>{fact.key}</strong>
                        <span className="truth-badge">{fact.truthStatus}</span>
                      </div>
                      <code>{fact.valueJson}</code>
                      <small>
                        {w("المصدر", "Source")}: {fact.sourceType}
                      </small>
                    </article>
                  ))
                )}
              </div>
              <button
                className="gold-button"
                onClick={() => onNavigate("decision")}
              >
                {w("انتقل لدعم القرار", "Continue to decision support")}
              </button>
            </>
          ) : previewProperty ? (
            <>
              <div className="passport-summary">
                <div>
                  <span>{w("الحي", "District")}</span>
                  <strong>{previewProperty.district}</strong>
                </div>
                <div>
                  <span>{w("المساحة التوضيحية", "Example area")}</span>
                  <strong>{format(previewProperty.areaSqm)} m²</strong>
                </div>
                <div>
                  <span>{w("غرف النوم", "Bedrooms")}</span>
                  <strong>{format(previewProperty.bedrooms)}</strong>
                </div>
                <div>
                  <span>{w("حالة المعرفة", "Truth status")}</span>
                  <strong>DECLARED</strong>
                </div>
              </div>
              <StatusBanner>
                {w(
                  "هذا عقار افتراضي. لا توجد أدلة مستقلة للتحقق منه.",
                  "This property is fictional and has no independently verified evidence.",
                )}
              </StatusBanner>
              <button
                className="gold-button"
                onClick={() => onNavigate("decision")}
              >
                {w("قارن القرار", "Review my choice")}
              </button>
            </>
          ) : (
            <div className="workspace-empty">
              <p>
                {w(
                  "افتح جواز عقار من نتائج المطابقة، أو اقرأ سجلًا من مساحة عملك.",
                  "Open a passport from matching results, or read a record from your workspace.",
                )}
              </p>
              <button
                className="glass-button"
                onClick={() => onNavigate("matching")}
              >
                {w("عرض الخيارات", "View options")}
              </button>
            </div>
          )}
        </>
      )}
      {view === "decision" && (
        <>
          {mode === "server" && passport && command.current?.intent && (
            <div className="human-approval">
              <h3>
                {w(
                  "اختيارك بعد مراجعة الجواز",
                  "Your choice after reviewing the passport",
                )}
              </h3>
              <p>
                {w(
                  "يمكنك متابعة اختيارك الشخصي. التوصية الآلية أدناه تحتاج أدلة مقبولة من مصادر معتمدة، ولا تتوفر تلقائيًا للبيانات الخيالية.",
                  "You can proceed with your own choice. An automated recommendation below requires eligible, source-authorized evidence, which fictional data does not provide automatically.",
                )}
              </p>
              <button
                className="gold-button"
                onClick={() => onNavigate("deal")}
              >
                {w(
                  "راجع اختياري في غرفة الصفقة",
                  "Review my choice in the deal room",
                )}
              </button>
            </div>
          )}
          <h2>
            {w(
              "الذكاء يقترح. القرار لك.",
              "Intelligence proposes. The decision is yours.",
            )}
          </h2>
          {mode === "demo" ? (
            <>
              <StatusBanner>
                {w(
                  "هذا القسم يشرح رحلة القرار ببيانات توضيحية. التسجيل الفعلي متاح داخل مساحة العمل المتصلة.",
                  "This section illustrates decision support. Recording a decision requires a connected workspace.",
                )}
              </StatusBanner>
              <a className="glass-button" href="/journey">
                {w(
                  "استكشف رحلة القرار التوضيحية",
                  "Explore the decision preview",
                )}{" "}
                <Icon name="arrow" />
              </a>
            </>
          ) : !run ? (
            <div className="workspace-empty">
              <p>
                {w(
                  "ابدأ بالمطابقة حتى تستند التوصية إلى خيارات مسجلة.",
                  "Start with matching so the recommendation refers to recorded options.",
                )}
              </p>
              <button
                className="gold-button"
                onClick={() => onNavigate("intent")}
              >
                {w("ابدأ بالاحتياج", "Start with intent")}
              </button>
            </div>
          ) : (
            <>
              <label className="standalone-label" htmlFor="evidence-ids">
                {w(
                  "معرفات أدلة مقبولة من مصادر معتمدة — مفصولة بفاصلة",
                  "Eligible, source-authorized evidence IDs — comma-separated",
                )}
              </label>
              <input
                className="workspace-input"
                id="evidence-ids"
                disabled={busy}
                value={evidenceIds}
                onChange={(event) => setEvidenceIds(event.target.value)}
                autoComplete="off"
                maxLength={1000}
                dir="ltr"
              />
              <button
                className="gold-button"
                disabled={
                  busy ||
                  !ready ||
                  !can("oula.intelligence.recommend") ||
                  !can("oula.intelligence.read")
                }
                onClick={() => void generate()}
              >
                {busy
                  ? w("جارٍ إعداد التوصية…", "Preparing recommendation…")
                  : w(
                      "أعد توصية من نتائج المطابقة",
                      "Prepare a recommendation from these matches",
                    )}
              </button>
              {explanation && (
                <article className="recommendation">
                  <span className="kicker">
                    {w(
                      "توصية مسجلة من الخدمة",
                      "Recorded service recommendation",
                    )}
                  </span>
                  <h3>{explanation.executiveSummary}</h3>
                  <p>
                    {w("الملاءمة", "Fit")}: {format(explanation.lifeFitScore)}
                    /100 · {w("الثقة", "Confidence")}:{" "}
                    {format(explanation.confidence * 100)}%
                  </p>
                  <p>
                    {w("المخاطر", "Risk")}: {explanation.riskClass}
                  </p>
                  <p>
                    {explanation.modelId} · {explanation.modelVersion}
                  </p>
                  <details>
                    <summary>
                      {w(
                        "الافتراضات وعدم اليقين",
                        "Assumptions and uncertainty",
                      )}
                    </summary>
                    <pre>
                      {JSON.stringify(
                        {
                          uncertainty: explanation.uncertainty,
                          confidence: explanation.confidenceBreakdown,
                          evidence: explanation.evidenceIds,
                          assumptions: explanation.assumptionIds,
                        },
                        null,
                        2,
                      )}
                    </pre>
                  </details>
                </article>
              )}
              {recommendation && explanation && !decision && (
                <div className="human-approval">
                  <label>
                    <input
                      type="checkbox"
                      disabled={busy}
                      checked={approved}
                      onChange={(event) => setApproved(event.target.checked)}
                    />
                    {w(
                      "راجعت التوصية وحدودها، وأختار العقار الموصى به لتسجيل قراري فقط.",
                      "I reviewed the recommendation and its limits, and choose the recommended property to record my decision only.",
                    )}
                  </label>
                  <button
                    className="gold-button"
                    disabled={
                      !approved ||
                      busy ||
                      !ready ||
                      !can("oula.intelligence.decide")
                    }
                    onClick={() => void commitDecision()}
                  >
                    {w("سجّل قراري", "Record my decision")}
                  </button>
                  <small>
                    {w(
                      "تسجيل القرار لا يوقّع عقدًا ولا يدفع مبلغًا ولا ينقل ملكية.",
                      "Recording a decision does not sign, pay or transfer ownership.",
                    )}
                  </small>
                </div>
              )}
              {decision && (
                <StatusBanner tone="success">
                  {w(
                    "تم تسجيل قرارك في الخدمة.",
                    "Your decision was recorded by the service.",
                  )}{" "}
                  <span dir="ltr">{decision.decisionId}</span>
                </StatusBanner>
              )}
            </>
          )}
        </>
      )}
      <div hidden={view !== "deal"}>
        <ConnectedDealRoom
          language={language}
          mode={mode}
          session={session}
          intentId={command.current?.intent?.id}
          propertyId={decision?.selectedPropertyId ?? passport?.propertyId}
          onStart={() => onNavigate("intent")}
        />
      </div>
      {view === "guardian" && (
        <>
          <h2>
            {w(
              "عقارك يحتاج متابعة، لا مجرد قائمة",
              "Your property needs care beyond a listing",
            )}
          </h2>
          {mode === "demo" ? (
            <>
              <div className="passport-summary">
                <div>
                  <span>{w("التزامات توضيحية", "Example obligations")}</span>
                  <strong>3</strong>
                </div>
                <div>
                  <span>
                    {w("إشارات تحتاج مراجعة", "Example review signals")}
                  </span>
                  <strong>2</strong>
                </div>
                <div>
                  <span>{w("حالة الأدلة", "Evidence basis")}</span>
                  <strong>DEMO</strong>
                </div>
              </div>
              <StatusBanner>
                {w(
                  "هذه أمثلة لتجربة إدارة الأملاك، وليست سجلات تحصيل أو قياسات فعلية.",
                  "These examples illustrate property care; they are not actual collections or measurements.",
                )}
              </StatusBanner>
            </>
          ) : (
            <>
              {recordForm(w("معرف العقار الذي تديره", "Managed property ID"))}
              <ConnectedPropertyCare
                key={recordId}
                propertyId={recordId}
                session={session}
                language={language}
              />
              {vitals && (
                <>
                  <div className="passport-summary">
                    <div>
                      <span>{w("الحالة المسجلة", "Recorded condition")}</span>
                      <strong>{vitals.overallStatus}</strong>
                    </div>
                    <div>
                      <span>{w("الأبعاد المعروفة", "Known dimensions")}</span>
                      <strong>{vitals.knownDimensionCount}/8</strong>
                    </div>
                    <div>
                      <span>
                        {w("الالتزامات المتأخرة", "Overdue obligations")}
                      </span>
                      <strong>{vitals.overdueObligations ?? "—"}</strong>
                    </div>
                    <div>
                      <span>
                        {w("أوامر العمل المفتوحة", "Open work orders")}
                      </span>
                      <strong>{vitals.openWorkOrders ?? "—"}</strong>
                    </div>
                  </div>
                  <small>
                    {w("وقت التقييم", "Assessed at")}: {vitals.assessedAt} ·{" "}
                    {vitals.policyKey}/{vitals.policyVersion}
                  </small>
                </>
              )}
              {advice && (
                <>
                  <h3>{w("توصيات تحتاج مراجعتك", "Advice for your review")}</h3>
                  <div className="fact-list">
                    {advice.recommendations.map((item, i) => (
                      <article key={i}>
                        <div>
                          <strong>{item.dimension}</strong>
                          <span className="truth-badge">
                            {item.observedStatus}
                          </span>
                        </div>
                        <p>{item.explanation}</p>
                        <small>{item.executionGate}</small>
                      </article>
                    ))}
                  </div>
                  {advice.limitations.map((limitation, i) => (
                    <p className="panel-copy" key={i}>
                      {limitation}
                    </p>
                  ))}
                </>
              )}
            </>
          )}
        </>
      )}
    </section>
  );
}
