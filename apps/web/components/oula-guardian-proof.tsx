"use client";

import { useMemo, useRef, useState } from "react";
import { acquireDevAuthSession, devContext, type DevAuthSession } from "../lib/dev-auth";
import { createGuardianClient, type GuardianResult, type ManagementOverview } from "../lib/guardian-client";

const PROPERTY_ID = "30000000-0000-4000-8000-000000000004";
type Step = "connect" | "enroll" | "obligation" | "assess" | "read" | null;
const copy = (english: boolean, ar: string, en: string) => english ? en : ar;

export function OulaGuardianProof({ baseUrl }: { baseUrl: string }) {
  const [english, setEnglish] = useState(false);
  const [session, setSession] = useState<DevAuthSession | null>(null);
  const [enrolled, setEnrolled] = useState(false);
  const [obligationSaved, setObligationSaved] = useState(false);
  const [assessment, setAssessment] = useState<GuardianResult | null>(null);
  const [overview, setOverview] = useState<ManagementOverview | null>(null);
  const [busy, setBusy] = useState<Step>(null);
  const [error, setError] = useState<string | null>(null);
  const commandKeys = useRef<Record<string, string>>({});
  const obligationDueAt = useRef<string | null>(null);
  const api = useMemo(() => session
    ? createGuardianClient(baseUrl, devContext(session, "PROPERTY_MANAGEMENT"))
    : null, [baseUrl, session]);
  const t = (ar: string, en: string) => copy(english, ar, en);
  const key = (name: string) => {
    if (!commandKeys.current[name]) commandKeys.current[name] = name + "-" + crypto.randomUUID();
    return commandKeys.current[name]!;
  };
  const execute = async (step: Step, task: () => Promise<void>) => {
    setBusy(step); setError(null);
    try { await task(); }
    catch (cause) { setError(cause instanceof Error ? cause.message : String(cause)); }
    finally { setBusy(null); }
  };

  const connect = () => execute("connect", async () => {
    const fixture = await acquireDevAuthSession(baseUrl, "property-manager");
    if (fixture.mode !== "DEV_TEST_ONLY" || fixture.fixture !== "property-manager") {
      throw new Error("Unexpected development identity");
    }
    commandKeys.current = {}; obligationDueAt.current = null;
    setSession(fixture); setEnrolled(false); setObligationSaved(false);
    setAssessment(null); setOverview(null);
  });
  const enroll = () => execute("enroll", async () => {
    if (!api) throw new Error("Login required");
    const result = await api.enroll(PROPERTY_ID, key("enroll"));
    if (result.status !== "ACTIVE") throw new Error("Enrollment not ACTIVE");
    setEnrolled(true);
  });
  const obligation = () => execute("obligation", async () => {
    if (!api || !enrolled) throw new Error("Enroll first");
    // Stable value across retries with the same Idempotency-Key.
    if (!obligationDueAt.current) obligationDueAt.current = new Date(Date.now() + 60 * 60 * 1000).toISOString();
    const result = await api.createObligation(PROPERTY_ID, {
      obligationType: "INSURANCE_RENEWAL",
      title: "Fictional insurance review",
      dueAt: obligationDueAt.current,
      priority: "CRITICAL",
      sourceType: "DEV_FIXTURE",
      sourceReference: "fictional-policy-no-external-provider",
    }, key("obligation"));
    if (result.status !== "OPEN") throw new Error("Obligation not OPEN");
    setObligationSaved(true);
  });
  const assess = () => execute("assess", async () => {
    if (!api || !obligationSaved) throw new Error("Record an obligation first");
    const result = await api.assess(PROPERTY_ID, key("assess"));
    setAssessment(result);
    setOverview(await api.overview(PROPERTY_ID));
  });
  const refresh = () => execute("read", async () => {
    if (!api) throw new Error("Login required");
    setOverview(await api.overview(PROPERTY_ID));
  });

  return <div dir={english ? "ltr" : "rtl"} style={{ minHeight: "100vh", background: "var(--surface)", padding: "24px" }}>
    <main className="canvas" style={{ maxWidth: 1100, padding: "20px" }} data-testid="guardian-proof">
      <div className="section-heading">
        <div><span className="micro-label">OULA / HABITAT / CANONICAL JAVA + POSTGRESQL</span>
          <h1>{t("موئل — حارس العقار", "HABITAT — Property Guardian")}</h1></div>
        <button className="minor-button" type="button" onClick={() => setEnglish(!english)}>
          {english ? "عربي" : "English"}</button>
      </div>
      <section className="panel">
        <div className="panel-head"><span className="micro-label">WORKSPACE · PURPOSE · HUMAN APPROVAL</span>
          <span className="status-tag">DEV_TEST_ONLY · FICTIONAL</span></div>
        <p>{t("رحلة تطوير فعلية تستخدم JWT وJava وPostgreSQL. العقار والوثائق افتراضية وغير موثقة. لا توجد مدفوعات أو عقود رسمية أو تكاملات حكومية.",
          "Real development journey with JWT, Java and PostgreSQL. Property data is fictional and unverified. No payments, legal signing or government integration.")}</p>
        <div className="decision-check">
          <div><b>01</b><span>{t("الهوية", "Identity")}</span><strong data-testid="guardian-session">{session ? "CONNECTED" : "NOT CONNECTED"}</strong></div>
          <div><b>02</b><span>{t("الإدارة", "Enrollment")}</span><strong data-testid="guardian-enrolled">{enrolled ? "ACTIVE" : "—"}</strong></div>
          <div><b>03</b><span>{t("الالتزامات", "Obligations")}</span><strong data-testid="guardian-obligations">{overview?.obligations.length ?? (obligationSaved ? 1 : 0)}</strong></div>
          <div><b>04</b><span>{t("إشارات الحارس", "Guardian signals")}</span><strong data-testid="guardian-signals">{overview?.guardianSignals.length ?? 0}</strong></div>
        </div>
        {session && <p className="form-note" data-testid="guardian-workspace">WORKSPACE {session.workspaceId} · PROPERTY {PROPERTY_ID}</p>}
        <div className="panel-actions">
          <button type="button" className="minor-button" data-testid="guardian-connect" disabled={busy !== null} onClick={connect}>{t("١. دخول مدير الأملاك", "1. Connect")}</button>
          <button type="button" className="minor-button" data-testid="guardian-enroll" disabled={!api || busy !== null || enrolled} onClick={enroll}>{t("٢. تفعيل الإدارة", "2. Enroll")}</button>
          <button type="button" className="minor-button" data-testid="guardian-create-obligation" disabled={!enrolled || busy !== null || obligationSaved} onClick={obligation}>{t("٣. إنشاء التزام", "3. Obligation")}</button>
          <button type="button" className="primary-button" data-testid="guardian-assess" disabled={!obligationSaved || busy !== null || !!assessment} onClick={assess}>{t("٤. تشغيل الحارس", "4. Assess Guardian")}</button>
          <button type="button" className="minor-button" data-testid="guardian-refresh" disabled={!session || busy !== null} onClick={refresh}>{t("تحديث قاعدة البيانات", "Refresh from DB")}</button>
        </div>
        {busy && <p role="status">{t("يجري تنفيذ أمر مصرح به…", "Executing an authorized command…")}</p>}
        {error && <p role="alert" data-testid="guardian-error">{error}</p>}
        {assessment && <div className="context-panel" data-testid="guardian-assessment">
          <span className="micro-label">GUARDIAN ASSESSMENT / DEV</span>
          <p>Evaluated {assessment.obligationsEvaluated} · Signals {assessment.signalsCreated} · Actions {assessment.actionsCreated}</p>
        </div>}
        {overview && <div className="context-panel" data-testid="guardian-overview">
          <span className="micro-label">CANONICAL DATABASE VIEW</span>
          <p>Enrollment {overview.enrollment.status} · Obligations {overview.obligations.length} · Signals {overview.guardianSignals.length} · Actions {overview.actions.length}</p>
          <ul>{overview.obligations.map((o) => <li key={o.id}>{o.title} · {o.status}</li>)}</ul>
        </div>}
      </section>
    </main>
  </div>;
}
