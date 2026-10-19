"use client";
import { useEffect, useRef, useState } from "react";
import { z } from "zod";
import { createBrowserOulaApi } from "../lib/browser-api";
import {
  careBelongsTo,
  obligationInputSchema,
  resolutionInputSchema,
  type PropertyCare,
} from "../lib/property-care-contracts";
import type { SessionState } from "../lib/use-oula-session";
import { errorText, StatusBanner, type Language } from "./workspace-primitives";
import "./property-care.css";

type Api = ReturnType<typeof createBrowserOulaApi>;
export function ConnectedPropertyCare({
  propertyId,
  session,
  language,
}: {
  propertyId: string;
  session: SessionState;
  language: Language;
}) {
  const w = (ar: string, en: string) => (language === "ar" ? ar : en);
  const [care, setCare] = useState<PropertyCare | null>(null);
  const [title, setTitle] = useState("");
  const [dueAt, setDueAt] = useState("");
  const [priority, setPriority] = useState("MEDIUM");
  const [note, setNote] = useState("");
  const [approval, setApproval] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const active = useRef(true);
  const locked = useRef(false);
  const abort = useRef<AbortController | null>(null);
  const keys = useRef(new Map<string, string>());
  const can = (scope: string) =>
    session.status === "authenticated" &&
    session.purposes.includes("PROPERTY_MANAGEMENT") &&
    session.scopes.includes(scope);
  const valid = z.uuid().safeParse(propertyId).success;
  useEffect(() => {
    active.current = true;
    return () => {
      active.current = false;
      abort.current?.abort();
    };
  }, []);
  async function read(api: Api) {
    const result = await api.getPropertyCare(propertyId);
    if (
      session.status !== "authenticated" ||
      !careBelongsTo(result, propertyId, session.workspaceId)
    )
      throw new Error("CONTRACT_MISMATCH");
    if (active.current) setCare(result);
  }
  async function perform(
    operation: string,
    body: unknown,
    write?: (api: Api, key: string) => Promise<unknown>,
  ) {
    if (!valid || locked.current || !can("oula.property.management.read"))
      return;
    locked.current = true;
    setBusy(true);
    setError("");
    setNotice("");
    abort.current = new AbortController();
    const api = createBrowserOulaApi(abort.current.signal);
    const command = JSON.stringify([operation, propertyId, body]);
    if (!keys.current.has(command))
      keys.current.set(command, crypto.randomUUID());
    try {
      if (write) await write(api, keys.current.get(command)!);
      if (!active.current) return;
      await read(api);
      if (!active.current) return;
      keys.current.delete(command);
      setApproval(false);
      setNotice(
        w(
          "قُرئ السجل المحفوظ من الخدمة.",
          "Saved state read back from the service.",
        ),
      );
      if (operation === "obligation") {
        setTitle("");
        setDueAt("");
      }
      if (operation === "complete") setNote("");
    } catch (reason) {
      if (active.current) {
        setCare(null);
        setError(errorText(reason, language));
      }
    } finally {
      if (active.current) {
        locked.current = false;
        setBusy(false);
      }
    }
  }
  function saveObligation(event: React.FormEvent) {
    event.preventDefault();
    const date = new Date(dueAt);
    const input = obligationInputSchema.safeParse({
      obligationType: "MAINTENANCE",
      title,
      dueAt: Number.isNaN(date.getTime()) ? "" : date.toISOString(),
      priority,
      sourceType: "USER_DECLARED",
    });
    if (!input.success || !approval) {
      setError(
        w(
          "أكمل عنوان الالتزام وموعده وتأكيدك.",
          "Complete the title, due date and confirmation.",
        ),
      );
      return;
    }
    void perform("obligation", input.data, (api, key) =>
      api.createObligation(propertyId, input.data, key),
    );
  }
  const enabled = valid && !busy && can("oula.property.management.read");
  return (
    <section
      className="care-panel"
      aria-labelledby="care-title"
      data-testid="workspace-care"
      aria-busy={busy}
    >
      <div className="care-heading">
        <div>
          <small>HABITAT · {w("متابعة الأصل", "ASSET CARE")}</small>
          <h3 id="care-title">
            {w("من الالتزام إلى المعالجة", "From obligation to resolution")}
          </h3>
        </div>
        <span className="truth-badge">
          {w("سجلات مساحة العمل", "Workspace records")}
        </span>
      </div>
      <p className="panel-copy">
        {w(
          "استخدم معرف العقار أعلاه. تُحفظ الإضافة والتقييم والمعالجة بعد تأكيدك، ثم تُقرأ النتيجة من الخدمة.",
          "Use the property ID above. Changes are confirmed by you, saved, then read back from the service.",
        )}
      </p>
      <div className="workspace-actions">
        <button
          className="glass-button"
          disabled={!enabled}
          onClick={() => void perform("read", null)}
        >
          {w("اقرأ سجل المتابعة", "Read care record")}
        </button>
        <button
          className="gold-button"
          disabled={
            !enabled ||
            !approval ||
            !can("oula.property.management.write") ||
            !!care?.enrollment
          }
          onClick={() =>
            void perform("enroll", null, (api, key) =>
              api.enrollPropertyCare(propertyId, key),
            )
          }
        >
          {w("فعّل متابعة العقار", "Enroll property care")}
        </button>
        <button
          className="glass-button"
          disabled={
            !enabled || !approval || !care || !can("oula.property.guardian.run")
          }
          onClick={() =>
            void perform("assess", null, (api, key) =>
              api.assessGuardian(propertyId, key),
            )
          }
        >
          {w("قيّم المواعيد والتنبيهات", "Assess deadlines")}
        </button>
      </div>
      <label className="care-confirm">
        <input
          type="checkbox"
          checked={approval}
          disabled={!enabled}
          onChange={(event) => setApproval(event.target.checked)}
        />
        {w(
          "راجعت البيانات وأؤكد الإجراء بنفسي. توثيق المعالجة إقرار مني، وليس اعتماد فحص مهني.",
          "I reviewed the information and personally confirm the action. A resolution note is my declaration, not a certified inspection.",
        )}
      </label>
      {!valid && (
        <p className="panel-copy">
          {w(
            "أدخل معرّف عقار صالحًا لبدء المتابعة.",
            "Enter a valid property ID to begin.",
          )}
        </p>
      )}
      {error && <StatusBanner tone="error">{error}</StatusBanner>}
      {notice && <StatusBanner>{notice}</StatusBanner>}
      {care && (
        <>
          {!can("oula.property.action.complete") && (
            <StatusBanner>
              {w(
                "يمكنك استعراض سجل المتابعة. توثيق المعالجة يحتاج صلاحية إضافية في حسابك.",
                "You can review care records. Recording resolution requires an additional account permission.",
              )}
            </StatusBanner>
          )}
          <div className="care-summary">
            <div>
              <span>{w("الالتزامات", "Obligations")}</span>
              <strong>{care.obligations.length}</strong>
            </div>
            <div>
              <span>{w("تنبيهات مفتوحة", "Open signals")}</span>
              <strong>
                {care.guardianSignals.filter((s) => s.status === "OPEN").length}
              </strong>
            </div>
            <div>
              <span>{w("إجراءات مكتملة", "Completed actions")}</span>
              <strong>
                {care.actions.filter((a) => a.status === "COMPLETED").length}
              </strong>
            </div>
          </div>
          <form className="care-form" onSubmit={saveObligation} noValidate>
            <h4>{w("أضف التزام متابعة", "Add a care obligation")}</h4>
            <label htmlFor="care-obligation-title">
              {w("عنوان الالتزام", "Obligation title")}
            </label>
            <input
              id="care-obligation-title"
              value={title}
              maxLength={255}
              disabled={busy || !can("oula.property.management.write")}
              onChange={(event) => {
                setTitle(event.target.value);
                setApproval(false);
              }}
            />
            <div className="care-fields">
              <div>
                <label htmlFor="care-due">
                  {w("الموعد المحلي", "Due date — local time")}
                </label>
                <input
                  id="care-due"
                  type="datetime-local"
                  value={dueAt}
                  disabled={busy}
                  onChange={(event) => {
                    setDueAt(event.target.value);
                    setApproval(false);
                  }}
                />
              </div>
              <div>
                <label htmlFor="care-priority">
                  {w("الأولوية", "Priority")}
                </label>
                <select
                  id="care-priority"
                  value={priority}
                  disabled={busy}
                  onChange={(event) => {
                    setPriority(event.target.value);
                    setApproval(false);
                  }}
                >
                  {[
                    ["LOW", "منخفضة", "Low"],
                    ["MEDIUM", "متوسطة", "Medium"],
                    ["HIGH", "مرتفعة", "High"],
                    ["CRITICAL", "حرجة", "Critical"],
                  ].map(([id, ar, en]) => (
                    <option key={id} value={id}>
                      {w(ar!, en!)}
                    </option>
                  ))}
                </select>
              </div>
            </div>
            <p className="panel-copy">
              {w(
                "المصدر: إقرار المستخدم. لا تُسجَّل هذه الإضافة كمعلومة محققة رسميًا.",
                "Source: user declaration. This is not recorded as independently verified information.",
              )}
            </p>
            <button
              className="gold-button"
              disabled={
                !enabled ||
                !approval ||
                !title.trim() ||
                !dueAt ||
                !can("oula.property.management.write")
              }
            >
              {w("احفظ الالتزام", "Save obligation")}
            </button>
          </form>
          <div className="care-records">
            {care.obligations.length === 0 ? (
              <p>{w("لا توجد التزامات مسجلة.", "No obligations recorded.")}</p>
            ) : (
              care.obligations.map((item) => (
                <article key={item.id}>
                  <strong>{item.title}</strong>
                  <span>
                    {item.status} · {item.priority}
                  </span>
                  <time dateTime={item.dueAt}>
                    {new Intl.DateTimeFormat(
                      language === "ar" ? "ar-SA" : "en-GB",
                      { dateStyle: "medium", timeStyle: "short" },
                    ).format(new Date(item.dueAt))}
                  </time>
                  <small>{item.sourceType}</small>
                </article>
              ))
            )}
          </div>
          {care.actions.length > 0 && (
            <>
              <label htmlFor="care-resolution">
                {w("ماذا عالجت؟", "Resolution note")}
              </label>
              <textarea
                id="care-resolution"
                className="resize-none"
                rows={4}
                maxLength={1000}
                value={note}
                disabled={busy}
                onChange={(event) => {
                  setNote(event.target.value);
                  setApproval(false);
                }}
              />
              <div className="care-records">
                {care.actions.map((action) => (
                  <article key={action.id}>
                    <strong>{action.title}</strong>
                    <span>{action.status}</span>
                    {action.resolutionNote && <p>{action.resolutionNote}</p>}
                    <button
                      className="glass-button"
                      disabled={
                        !enabled ||
                        !approval ||
                        !resolutionInputSchema.safeParse({
                          resolutionNote: note,
                        }).success ||
                        !can("oula.property.action.complete") ||
                        action.status === "COMPLETED"
                      }
                      onClick={() =>
                        void perform(
                          "complete",
                          { id: action.id, resolutionNote: note.trim() },
                          (api, key) =>
                            api.completePropertyAction(
                              action.id,
                              note.trim(),
                              key,
                            ),
                        )
                      }
                    >
                      {w("وثّق اكتمال المعالجة", "Record resolution")}
                    </button>
                  </article>
                ))}
              </div>
            </>
          )}
        </>
      )}
    </section>
  );
}
