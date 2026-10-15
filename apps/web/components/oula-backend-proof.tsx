"use client";

import { useMemo, useRef, useState } from "react";
import {
  createOulaApi,
  type DealRoomResponse,
  type IntentResponse,
  type MatchRunResponse,
  type PropertyPassport,
  type TransactionResponse,
} from "../lib/api";
import {
  acquireDevAuthSession,
  devContext,
  prepareDevGoldenPath,
  type DevAuthSession,
} from "../lib/dev-auth";

type Language = "ar" | "en";
type Busy =
  | "connect"
  | "consent"
  | "intent"
  | "passport"
  | "transaction"
  | "qualify"
  | "viewing"
  | "offer"
  | null;

const t = (lang: Language, ar: string, en: string) => lang === "ar" ? ar : en;

export function OulaBackendProof({
  language,
  baseUrl,
}: {
  language: Language;
  baseUrl: string;
}) {
  const [session, setSession] = useState<DevAuthSession | null>(null);
  const [consent, setConsent] = useState<string | null>(null);
  const [intent, setIntent] = useState<IntentResponse | null>(null);
  const [matches, setMatches] = useState<MatchRunResponse | null>(null);
  const [passport, setPassport] = useState<PropertyPassport | null>(null);
  const [transaction, setTransaction] = useState<TransactionResponse | null>(null);
  const [dealRoom, setDealRoom] = useState<DealRoomResponse | null>(null);
  const [busy, setBusy] = useState<Busy>(null);
  const [error, setError] = useState<string | null>(null);
  const keys = useRef<Record<string, string>>({});

  const decisionApi = useMemo(
    () => session
      ? createOulaApi(baseUrl, devContext(session, "PROPERTY_DECISION_SUPPORT"))
      : null,
    [baseUrl, session],
  );
  const transactionApi = useMemo(
    () => session
      ? createOulaApi(baseUrl, devContext(session, "TRANSACTION_EXECUTION"))
      : null,
    [baseUrl, session],
  );

  const key = (name: string) => {
    if (!keys.current[name]) {
      keys.current[name] = `${name}-${crypto.randomUUID()}`;
    }
    return keys.current[name]!;
  };

  const run = async (name: Busy, action: () => Promise<void>) => {
    setBusy(name);
    setError(null);
    try {
      await action();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : String(cause));
    } finally {
      setBusy(null);
    }
  };

  const connect = () => run("connect", async () => {
    const next = await acquireDevAuthSession(baseUrl, "buyer");
    keys.current = {};
    setSession(next);
    setConsent(null);
    setIntent(null);
    setMatches(null);
    setPassport(null);
    setTransaction(null);
    setDealRoom(null);
  });

  const grantConsent = () => run("consent", async () => {
    if (!decisionApi) throw new Error("Connect DEV_AUTH first");
    const result = await decisionApi.decideConsent(
      {
        consentCode: "LIFE_CONTEXT_PROCESSING",
        decision: "GRANTED",
        reason: "Explicit fictional Expo integration consent",
      },
      key("consent"),
    );
    setConsent(result.decision);
  });

  const createIntentAndMatch = () => run("intent", async () => {
    if (!decisionApi || !session) throw new Error("Connect DEV_AUTH first");
    const created = await decisionApi.createIntent(
      {
        intentType: "BUY",
        budgetMax: 1_200_000,
        minimumBedrooms: 3,
        preferredDistricts: ["الملقا"],
      },
      key("intent"),
    );
    await prepareDevGoldenPath(baseUrl, session, created.id);
    const matchRun = await decisionApi.runMatching(created.id, key("matching"));
    if (matchRun.matches.length === 0) {
      throw new Error("Canonical matching returned no fixture candidates");
    }
    setIntent(created);
    setMatches(matchRun);
    setPassport(null);
    setTransaction(null);
    setDealRoom(null);
  });

  const openPassport = () => run("passport", async () => {
    if (!decisionApi || !matches?.matches[0]) throw new Error("Run canonical matching first");
    const result = await decisionApi.getPropertyPassport(matches.matches[0].propertyId);
    setPassport(result);
  });

  const createTransaction = () => run("transaction", async () => {
    if (!transactionApi || !intent || !matches?.matches[0]) {
      throw new Error("Intent and canonical match are required");
    }
    const result = await transactionApi.createTransaction(
      {
        intentId: intent.id,
        propertyId: matches.matches[0].propertyId,
      },
      key("transaction"),
    );
    setTransaction(result);
    setDealRoom(await transactionApi.getDealRoom(result.id));
  });

  const qualify = () => run("qualify", async () => {
    if (!transactionApi || !transaction) throw new Error("Create the transaction first");
    const result = await transactionApi.advanceTransaction(
      transaction.id,
      {
        expectedVersion: transaction.version,
        target: "QUALIFIED",
        reason: "Explicit DEV_TEST_ONLY buyer qualification",
      },
      key("qualify"),
    );
    setTransaction(result);
    setDealRoom(await transactionApi.getDealRoom(result.id));
  });

  const requestViewing = () => run("viewing", async () => {
    if (!transactionApi || !transaction) throw new Error("Qualified transaction required");
    const result = await transactionApi.requestTransactionViewing(
      transaction.id,
      {
        expectedVersion: transaction.version,
        preferredAt: "2026-10-20T16:30:00Z",
        note: "Fictional Expo viewing request",
        humanConfirmed: true,
      },
      key("viewing"),
    );
    setTransaction(result.transaction);
    setDealRoom(await transactionApi.getDealRoom(result.transaction.id));
  });

  const submitOffer = () => run("offer", async () => {
    if (!transactionApi || !transaction) throw new Error("Viewing stage required");
    const result = await transactionApi.submitTransactionOffer(
      transaction.id,
      {
        expectedVersion: transaction.version,
        amount: 1_050_000,
        currency: "SAR",
        termsSummary: "Fictional DEV_TEST_ONLY offer; no payment, signing or external acceptance.",
        humanConfirmed: true,
      },
      key("offer"),
    );
    setTransaction(result.transaction);
    setDealRoom(await transactionApi.getDealRoom(result.transaction.id));
  });

  return (
    <section className="panel backend-proof" data-testid="backend-proof">
      <div className="panel-head">
        <span className="micro-label">DEV_AUTH / REAL JAVA + POSTGRESQL</span>
        <span className="status-tag" aria-label="Development integration only">
          <span className="status-dot" aria-hidden="true" /> DEV_TEST_ONLY
        </span>
      </div>

      <h2>{t(language, "إثبات المسار الحقيقي — دون ادعاء إنتاج", "Real backend proof — not production")}</h2>
      <p>
        {t(
          language,
          "الهوية والعقارات هنا خيالية، لكن JWT والتحقق والصلاحيات والـAPIs والحفظ في PostgreSQL حقيقية داخل بيئة التطوير.",
          "Identities and properties are fictional, but JWT validation, authorization, APIs and PostgreSQL persistence are real in the development environment.",
        )}
      </p>
      <p className="form-note">SIGNED JWT · WORKSPACE/PURPOSE/SCOPE · JAVA · POSTGRESQL · NO SILENT FALLBACK</p>

      <div className="decision-check">
        <div>
          <b>01</b>
          <span>{t(language, "جلسة التطوير", "Dev session")}</span>
          <strong data-testid="backend-session">{session ? "CONNECTED" : "NOT CONNECTED"}</strong>
        </div>
        <div>
          <b>02</b>
          <span>{t(language, "الموافقة الصريحة", "Explicit consent")}</span>
          <strong data-testid="backend-consent">{consent ?? "—"}</strong>
        </div>
        <div>
          <b>03</b>
          <span>{t(language, "Intent / Match", "Intent / Match")}</span>
          <strong data-testid="backend-match-count">{matches ? String(matches.matches.length) : "—"}</strong>
        </div>
        <div>
          <b>04</b>
          <span>{t(language, "حالة الصفقة", "Transaction stage")}</span>
          <strong data-testid="backend-transaction-stage">{transaction?.stage ?? "—"}</strong>
        </div>
      </div>

      {session && (
        <p className="form-note" data-testid="backend-workspace">
          WORKSPACE {session.workspaceId} · FIXTURE {session.fixture}
        </p>
      )}

      {passport && (
        <div className="canonical-state" data-testid="backend-passport">
          <span className="micro-label">PROPERTY PASSPORT / CANONICAL API</span>
          <strong>{passport.propertyId}</strong>
          <span>
            {passport.district ?? "—"} · truth coverage {Math.round(passport.verifiedFactCoverage * 100)}% ·
            {passport.facts[0] ? ` ${passport.facts[0].truthStatus}/${passport.facts[0].sourceType}` : " no facts"}
          </span>
        </div>
      )}

      {dealRoom && (
        <div className="canonical-state" data-testid="backend-deal-room">
          <span className="micro-label">DEAL ROOM / AUTHORITATIVE BACKEND STATE</span>
          <strong>{dealRoom.transaction.stage}</strong>
          <span>
            VIEWINGS {dealRoom.viewings.length} · OFFERS {dealRoom.offers.length} ·
            HUMAN_CONFIRMATION_REQUIRED {String(dealRoom.humanConfirmationRequired)}
          </span>
        </div>
      )}

      {error && <p role="alert" className="form-note" data-testid="backend-error">{error}</p>}

      <div className="panel-actions" data-testid="backend-actions">
        <button className="minor-button" type="button" disabled={busy !== null} onClick={connect}>
          {busy === "connect" ? "…" : t(language, "اتصل بالـBackend", "Connect backend")}
        </button>
        <button className="minor-button" type="button" disabled={!session || busy !== null} onClick={grantConsent}>
          {t(language, "سجّل الموافقة", "Record consent")}
        </button>
        <button className="minor-button" type="button" disabled={!session || busy !== null} onClick={createIntentAndMatch}>
          {t(language, "أنشئ Intent وشغّل Match", "Create Intent + Match")}
        </button>
        <button className="minor-button" type="button" disabled={!matches || busy !== null} onClick={openPassport}>
          {t(language, "افتح الجواز الحقيقي", "Open canonical passport")}
        </button>
        <button className="minor-button" type="button" disabled={!matches || !intent || busy !== null} onClick={createTransaction}>
          {t(language, "ابدأ الصفقة", "Create transaction")}
        </button>
        <button className="minor-button" type="button" disabled={transaction?.stage !== "DRAFT" || busy !== null} onClick={qualify}>
          QUALIFY
        </button>
        <button className="minor-button" type="button" disabled={transaction?.stage !== "QUALIFIED" || busy !== null} onClick={requestViewing}>
          REQUEST VIEWING
        </button>
        <button className="primary-button" type="button" disabled={transaction?.stage !== "VIEWING" || busy !== null} onClick={submitOffer}>
          SUBMIT OFFER ↗
        </button>
      </div>
    </section>
  );
}
