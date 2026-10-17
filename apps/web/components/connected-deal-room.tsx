"use client";

import { useEffect, useRef, useState } from "react";
import { z } from "zod";
import type { DealRoomResponse } from "../lib/api";
import { createBrowserOulaApi } from "../lib/browser-api";
import { offerInputSchema } from "../lib/api-contracts";
import type { SessionState } from "../lib/use-oula-session";
import {
  errorText,
  StatusBanner,
  word,
  type Language,
} from "./workspace-primitives";

type Props = {
  language: Language;
  mode: "demo" | "server";
  session: SessionState;
  intentId?: string;
  propertyId?: string;
  onStart: () => void;
};

export function ConnectedDealRoom({
  language,
  mode,
  session,
  intentId,
  propertyId,
  onStart,
}: Props) {
  const w = (ar: string, en: string) => word(language, { ar, en });
  const [room, setRoom] = useState<DealRoomResponse | null>(null);
  const [recordId, setRecordId] = useState("");
  const [confirmed, setConfirmed] = useState(false);
  const [amount, setAmount] = useState(1050000);
  const [terms, setTerms] = useState("");
  const [preferredAt, setPreferredAt] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const lock = useRef(false);
  const active = useRef(true);
  const controller = useRef<AbortController | null>(null);
  const commands = useRef(new Map<string, string>());
  const recordInput = useRef<HTMLInputElement>(null);
  const amountInput = useRef<HTMLInputElement>(null);
  useEffect(() => {
    active.current = true;
    return () => {
      active.current = false;
      controller.current?.abort();
    };
  }, []);
  const can = (scope: string) =>
    mode === "server" &&
    session.status === "authenticated" &&
    session.scopes.includes(scope) &&
    session.purposes.includes("TRANSACTION_EXECUTION");
  const key = (operation: string, body: unknown) => {
    const fingerprint = operation + ":" + JSON.stringify(body);
    if (!commands.current.has(fingerprint))
      commands.current.set(fingerprint, crypto.randomUUID());
    return commands.current.get(fingerprint)!;
  };
  const perform = async (
    work: (
      api: ReturnType<typeof createBrowserOulaApi>,
    ) => Promise<DealRoomResponse>,
  ) => {
    if (lock.current || session.status !== "authenticated") return;
    lock.current = true;
    setBusy(true);
    setError(null);
    controller.current = new AbortController();
    try {
      const next = await work(createBrowserOulaApi(controller.current.signal));
      if (active.current) {
        setRoom(next);
        setRecordId(next.transaction.id);
        setConfirmed(false);
      }
    } catch (cause) {
      if (active.current) setError(errorText(cause, language));
    } finally {
      lock.current = false;
      if (active.current) setBusy(false);
    }
  };
  const load = () => {
    if (!z.uuid().safeParse(recordId).success) {
      setError(w("أدخل معرّف صفقة صحيحًا.", "Enter a valid transaction ID."));
      recordInput.current?.focus();
      return;
    }
    void perform((api) => api.getDealRoom(recordId));
  };
  const create = () => {
    if (!intentId || !propertyId || !confirmed) return;
    void perform(async (api) => {
      const input = { intentId, propertyId };
      const transaction = await api.createTransaction(
        input,
        key("create", input),
      );
      return api.getDealRoom(transaction.id);
    });
  };
  const qualify = () => {
    if (!room || !confirmed) return;
    void perform(async (api) => {
      const input = {
        expectedVersion: room.transaction.version,
        target: "QUALIFIED" as const,
        reason: "Human reviewed property selection in OULA workspace",
      };
      await api.advanceTransaction(
        room.transaction.id,
        input,
        key("qualify", { id: room.transaction.id, ...input }),
      );
      return api.getDealRoom(room.transaction.id);
    });
  };
  const view = () => {
    if (!room || !confirmed) return;
    let date: string | null = null;
    if (preferredAt) {
      const parsed = new Date(preferredAt);
      if (
        !Number.isFinite(parsed.getTime()) ||
        parsed.getTime() <= Date.now()
      ) {
        setError(
          w(
            "اختر موعدًا لاحقًا للوقت الحالي أو اترك الموعد للتنسيق.",
            "Choose a future time or leave the date open for coordination.",
          ),
        );
        return;
      }
      date = parsed.toISOString();
    }
    void perform(async (api) => {
      const input = {
        expectedVersion: room.transaction.version,
        preferredAt: date,
        humanConfirmed: true as const,
      };
      await api.requestViewing(
        room.transaction.id,
        input,
        key("viewing", { id: room.transaction.id, ...input }),
      );
      return api.getDealRoom(room.transaction.id);
    });
  };
  const offer = () => {
    if (!room || !confirmed) return;
    const input = {
      expectedVersion: room.transaction.version,
      amount,
      currency: "SAR",
      termsSummary: terms,
      humanConfirmed: true as const,
    };
    if (
      !offerInputSchema.safeParse(input).success ||
      Math.abs(amount * 100 - Math.round(amount * 100)) > 0.00001
    ) {
      setError(
        w(
          "أدخل مبلغًا موجبًا بمنزلتين عشريتين كحد أقصى، وشروطًا لا تزيد عن 1000 حرف.",
          "Enter a positive amount with at most two decimal places and terms of at most 1,000 characters.",
        ),
      );
      amountInput.current?.focus();
      return;
    }
    void perform(async (api) => {
      await api.submitOffer(
        room.transaction.id,
        input,
        key("offer", { id: room.transaction.id, ...input }),
      );
      return api.getDealRoom(room.transaction.id);
    });
  };
  const stage = (value: string) =>
    ({
      DRAFT: w("مسودة", "Draft"),
      QUALIFIED: w("تمت المراجعة الأولية", "Qualified"),
      VIEWING: w("المعاينة مطلوبة", "Viewing requested"),
      OFFERING: w("تم تقديم العرض", "Offer submitted"),
    })[value] ?? value;

  return (
    <section
      aria-label={w("إجراءات غرفة الصفقة", "Deal room actions")}
      aria-busy={busy}
    >
      <h2>
        {w(
          "كل خطوة واضحة، وكل التزام بإرادتك",
          "Clear steps. Commitments under your control.",
        )}
      </h2>
      {mode === "demo" ? (
        <>
          <StatusBanner>
            {w(
              "غرفة توضيحية. افتح مساحة العمل للمعاملات المحفوظة في الخدمة.",
              "This is a preview. Switch to Workspace for service-backed transactions.",
            )}
          </StatusBanner>
          <a className="gold-button" href="/journey">
            {w("افتح الرحلة التوضيحية", "Open the demo journey")}
          </a>
        </>
      ) : (
        <>
          <p className="panel-copy">
            {w(
              "المعاينة تُسجَّل كطلب، والعرض كتقديم من طرفك. لا يمثل ذلك قبول الطرف الآخر أو توقيعًا أو دفعًا أو نقل ملكية.",
              "A viewing is a recorded request; an offer is your submission. Neither means counterparty acceptance, signature, payment or title transfer.",
            )}
          </p>
          {error && (
            <div id="deal-error">
              <StatusBanner tone="error">{error}</StatusBanner>
            </div>
          )}
          <form
            noValidate
            className="connected-form compact"
            onSubmit={(event) => {
              event.preventDefault();
              load();
            }}
          >
            <label htmlFor="deal-record">
              {w("افتح صفقة محفوظة", "Open a saved transaction")}
            </label>
            <div className="record-controls">
              <input
                ref={recordInput}
                id="deal-record"
                disabled={busy}
                value={recordId}
                onChange={(event) => setRecordId(event.target.value)}
                dir="ltr"
                autoComplete="off"
                maxLength={36}
              />
              <button
                className="glass-button"
                disabled={busy || !can("oula.transaction.read")}
                type="submit"
              >
                {w("أعد قراءة الصفقة", "Reload transaction")}
              </button>
            </div>
          </form>
          {!room && (!intentId || !propertyId) && (
            <div className="workspace-empty">
              <p>
                {w(
                  "اختر عقارًا بعد المطابقة أو افتح صفقة محفوظة.",
                  "Choose a property after matching, or open a saved transaction.",
                )}
              </p>
              <button className="gold-button" onClick={onStart}>
                {w("ابدأ باحتياجك", "Start with your needs")}
              </button>
            </div>
          )}
          {!room && intentId && propertyId && (
            <p className="record-reference">
              {w("العقار المختار", "Selected property")}:{" "}
              <span dir="ltr">{propertyId}</span>
            </p>
          )}
          {room && (
            <>
              <div
                className="passport-summary"
                data-testid="workspace-deal-state"
              >
                <div>
                  <span>{w("حالة الصفقة", "Transaction stage")}</span>
                  <strong>{stage(room.transaction.stage)}</strong>
                </div>
                <div>
                  <span>{w("المعاينات", "Viewings")}</span>
                  <strong>{room.viewings.length}</strong>
                </div>
                <div>
                  <span>{w("العروض", "Offers")}</span>
                  <strong>{room.offers.length}</strong>
                </div>
                <div>
                  <span>{w("النسخة", "Version")}</span>
                  <strong>{room.transaction.version}</strong>
                </div>
              </div>
              {room.viewings.map((item) => (
                <p key={item.id}>
                  {w("طلب معاينة", "Viewing request")} ·{" "}
                  {item.preferredAt
                    ? new Intl.DateTimeFormat(
                        language === "ar" ? "ar-SA" : "en-GB",
                        {
                          dateStyle: "medium",
                          timeStyle: "short",
                          timeZone: "Asia/Riyadh",
                        },
                      ).format(new Date(item.preferredAt)) +
                      w(" · توقيت الرياض", " · Riyadh time")
                    : w("الموعد للتنسيق", "Time to be coordinated")}
                </p>
              ))}
              {room.offers.map((item) => (
                <p key={item.id}>
                  {w("عرض مقدّم", "Submitted offer")}:{" "}
                  {new Intl.NumberFormat(
                    language === "ar" ? "ar-SA" : "en-US",
                    { style: "currency", currency: item.currency },
                  ).format(item.amount)}{" "}
                  · {item.termsSummary}
                </p>
              ))}
            </>
          )}
          {room?.availableCommands.includes("REQUEST_VIEWING") && (
            <div className="connected-form">
              <label htmlFor="viewing-time">
                {w(
                  "موعد مقترح — اختياري، بتوقيت جهازك",
                  "Preferred time — optional, in your device timezone",
                )}
              </label>
              <input
                id="viewing-time"
                type="datetime-local"
                disabled={busy}
                value={preferredAt}
                onChange={(event) => setPreferredAt(event.target.value)}
              />
            </div>
          )}
          {room?.availableCommands.includes("SUBMIT_OFFER") && (
            <div className="connected-form">
              <label htmlFor="offer-amount">
                {w("مبلغ العرض — ريال سعودي", "Offer amount — Saudi riyals")}
              </label>
              <input
                ref={amountInput}
                id="offer-amount"
                type="number"
                min="0.01"
                step="0.01"
                disabled={busy}
                value={amount}
                onChange={(event) => setAmount(Number(event.target.value))}
                aria-describedby={error ? "deal-error" : undefined}
              />
              <label htmlFor="offer-terms">
                {w("شروط العرض — اختيارية", "Offer terms — optional")}
              </label>
              <textarea
                id="offer-terms"
                className="resize-none"
                disabled={busy}
                value={terms}
                maxLength={1000}
                rows={4}
                style={{ resize: "none" }}
                onChange={(event) => setTerms(event.target.value)}
              />
            </div>
          )}
          {((!room && intentId && propertyId) ||
            !!room?.availableCommands.length) && (
            <div className="human-approval">
              <label>
                <input
                  type="checkbox"
                  disabled={busy}
                  checked={confirmed}
                  onChange={(event) => setConfirmed(event.target.checked)}
                />
                {w(
                  "راجعت المعلومات وأؤكد الخطوة التالية بنفسي.",
                  "I reviewed the information and personally confirm the next step.",
                )}
              </label>
              {!room && (
                <button
                  className="gold-button"
                  disabled={
                    busy || !confirmed || !can("oula.transaction.create")
                  }
                  onClick={create}
                >
                  {w("أنشئ مسودة الصفقة", "Create transaction draft")}
                </button>
              )}
              {room?.availableCommands.includes("QUALIFY") && (
                <button
                  className="gold-button"
                  disabled={
                    busy || !confirmed || !can("oula.transaction.advance")
                  }
                  onClick={qualify}
                >
                  {w("أكّد المراجعة الأولية", "Confirm qualification")}
                </button>
              )}
              {room?.availableCommands.includes("REQUEST_VIEWING") && (
                <button
                  className="gold-button"
                  disabled={
                    busy ||
                    !confirmed ||
                    !can("oula.transaction.viewing.request")
                  }
                  onClick={view}
                >
                  {w("سجّل طلب المعاينة", "Record viewing request")}
                </button>
              )}
              {room?.availableCommands.includes("SUBMIT_OFFER") && (
                <button
                  className="gold-button"
                  disabled={
                    busy || !confirmed || !can("oula.transaction.offer.submit")
                  }
                  onClick={offer}
                >
                  {w("قدّم العرض", "Submit offer")}
                </button>
              )}
            </div>
          )}
          {busy && (
            <StatusBanner>
              {w(
                "جارٍ حفظ الخطوة وقراءة الحالة من الخدمة…",
                "Saving the step and reading the service state…",
              )}
            </StatusBanner>
          )}
          {room && !room.availableCommands.length && (
            <StatusBanner tone="success">
              {w(
                "تم حفظ خطواتك. يمكنك إعادة قراءة الصفقة للتحقق من حالتها.",
                "Your steps are saved. Reload the transaction to check its state.",
              )}
            </StatusBanner>
          )}
        </>
      )}
    </section>
  );
}
