"use client";
import { useEffect, useRef, useState } from "react";
import { z } from "zod";
import { createBrowserOulaApi } from "../lib/browser-api";
import { propertyInputSchema } from "../lib/api-contracts";
import type {
  DeclaredProperty,
  DraftListing,
} from "../lib/property-intake-client";
import type { SessionState } from "../lib/use-oula-session";
import {
  errorText,
  StatusBanner,
  word,
  type Language,
} from "./workspace-primitives";

export function PropertyIntakeForm({
  language,
  mode,
  session,
}: {
  language: Language;
  mode: "demo" | "server";
  session: SessionState;
}) {
  const w = (ar: string, en: string) => word(language, { ar, en });
  const [assetType, setAssetType] = useState<"RESIDENTIAL" | "COMMERCIAL">(
    "RESIDENTIAL",
  );
  const [district, setDistrict] = useState("");
  const [bedrooms, setBedrooms] = useState(3);
  const [askingPrice, setAskingPrice] = useState(1000000);
  const [transactionType, setTransactionType] = useState<"SALE" | "RENT">(
    "SALE",
  );
  const [property, setProperty] = useState<DeclaredProperty | null>(null);
  const [listing, setListing] = useState<DraftListing | null>(null);
  const [recordId, setRecordId] = useState("");
  const [approved, setApproved] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const lock = useRef(false);
  const active = useRef(true);
  const controller = useRef<AbortController | null>(null);
  const firstField = useRef<HTMLInputElement>(null);
  const commands = useRef(new Map<string, string>());
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
    session.scopes.includes(scope);
  const key = (operation: string, body: unknown) => {
    const value = operation + ":" + JSON.stringify(body);
    if (!commands.current.has(value))
      commands.current.set(value, crypto.randomUUID());
    return commands.current.get(value)!;
  };
  const perform = async (
    work: (api: ReturnType<typeof createBrowserOulaApi>) => Promise<void>,
  ) => {
    if (lock.current) return;
    lock.current = true;
    setBusy(true);
    setError(null);
    controller.current = new AbortController();
    try {
      await work(createBrowserOulaApi(controller.current.signal));
    } catch (cause) {
      if (active.current) setError(errorText(cause, language));
    } finally {
      lock.current = false;
      if (active.current) setBusy(false);
    }
  };
  const save = () => {
    const input = {
      assetType,
      district,
      bedrooms,
      askingPrice,
      demo:
        session.status === "authenticated" && session.mode === "DEV_TEST_ONLY",
    };
    if (!propertyInputSchema.safeParse(input).success) {
      setError(
        w(
          "أدخل الحي، وعدد غرف من 0 إلى 100، وسعرًا موجبًا.",
          "Enter a district, 0–100 bedrooms and a positive asking price.",
        ),
      );
      firstField.current?.focus();
      return;
    }
    if (!approved || !can("oula.property.write")) return;
    void perform(async (api) => {
      const saved = await api.createProperty(input, key("property", input));
      const read = await api.getProperty(saved.id);
      if (active.current) {
        setProperty(read);
        setRecordId(read.id);
        setListing(null);
        setApproved(false);
      }
    });
  };
  const read = () => {
    if (!z.uuid().safeParse(recordId).success) {
      setError(w("أدخل معرّف عقار صحيحًا.", "Enter a valid property ID."));
      return;
    }
    void perform(async (api) => {
      const value = await api.getProperty(recordId);
      if (active.current) {
        setProperty(value);
        setListing(null);
      }
    });
  };
  const draft = () => {
    if (!property || !approved || !can("oula.listing.write")) return;
    void perform(async (api) => {
      const value = await api.createListing(
        property.id,
        transactionType,
        property.askingPrice,
        key("listing", {
          id: property.id,
          transactionType,
          price: property.askingPrice,
        }),
      );
      const stored = await api.getListing(value.id);
      if (active.current) {
        setListing(stored);
        setApproved(false);
      }
    });
  };
  return (
    <section className="connected-panel" aria-busy={busy}>
      <h2>{w("ابدأ ببيانات عقارك", "Start with your property")}</h2>
      <p className="panel-copy">
        {w(
          "تُحفظ المعلومات كمعلومات معلنة منك. لا تُثبت الملكية، وتظل مسودة الإعلان خاصة داخل مساحة العمل.",
          "Information is saved as your declaration. It does not establish ownership. Listing drafts stay private within the workspace.",
        )}
      </p>
      {mode === "demo" && (
        <StatusBanner>
          {w(
            "انتقل إلى مساحة العمل وسجّل الدخول لحفظ عقارك.",
            "Switch to Workspace and sign in to save your property.",
          )}
        </StatusBanner>
      )}
      {session.status === "authenticated" &&
        session.mode === "DEV_TEST_ONLY" && (
          <StatusBanner>
            DEV_TEST_ONLY ·{" "}
            {w(
              "كل سجل يُضاف بهذه الجلسة يحمل مصدرًا تجريبيًا.",
              "Every record created in this session carries demo provenance.",
            )}
          </StatusBanner>
        )}
      {error && (
        <div id="intake-error">
          <StatusBanner tone="error">{error}</StatusBanner>
        </div>
      )}
      {!property ? (
        <form
          noValidate
          className="connected-form"
          onSubmit={(event) => {
            event.preventDefault();
            save();
          }}
        >
          <label htmlFor="property-kind">
            {w("نوع العقار", "Property type")}
          </label>
          <select
            id="property-kind"
            disabled={busy}
            value={assetType}
            onChange={(event) =>
              setAssetType(event.target.value as "RESIDENTIAL" | "COMMERCIAL")
            }
          >
            <option value="RESIDENTIAL">{w("سكني", "Residential")}</option>
            <option value="COMMERCIAL">{w("تجاري", "Commercial")}</option>
          </select>
          <label htmlFor="property-district">{w("الحي", "District")}</label>
          <input
            ref={firstField}
            id="property-district"
            disabled={busy}
            value={district}
            maxLength={160}
            onChange={(event) => setDistrict(event.target.value)}
            aria-invalid={!!error && !district.trim()}
            aria-describedby={error ? "intake-error" : undefined}
          />
          <div className="form-pair">
            <div>
              <label htmlFor="property-bedrooms">
                {w("غرف النوم", "Bedrooms")}
              </label>
              <input
                id="property-bedrooms"
                type="number"
                min="0"
                max="100"
                step="1"
                disabled={busy}
                value={bedrooms}
                onChange={(event) => setBedrooms(Number(event.target.value))}
              />
            </div>
            <div>
              <label htmlFor="property-price">
                {w("السعر المعلن — ريال سعودي", "Asking price — Saudi riyals")}
              </label>
              <input
                id="property-price"
                type="number"
                min="0.01"
                step="0.01"
                disabled={busy}
                value={askingPrice}
                onChange={(event) => setAskingPrice(Number(event.target.value))}
              />
            </div>
          </div>
          <label>
            <input
              type="checkbox"
              checked={approved}
              disabled={busy}
              onChange={(event) => setApproved(event.target.checked)}
            />
            {w(
              "أؤكد أن هذه بيانات معلنة، وأطلب حفظها في مساحة العمل.",
              "I confirm these are declared details and request saving them in the workspace.",
            )}
          </label>
          <button
            className="gold-button"
            disabled={
              busy ||
              !approved ||
              !can("oula.property.write") ||
              !can("oula.property.read")
            }
            type="submit"
          >
            {w("احفظ العقار", "Save property")}
          </button>
        </form>
      ) : (
        <>
          <div className="passport-summary" data-testid="workspace-property">
            <div>
              <span>{w("الحي", "District")}</span>
              <strong>{property.district}</strong>
            </div>
            <div>
              <span>{w("حالة المعلومات", "Truth status")}</span>
              <strong>{property.truthStatus}</strong>
            </div>
            <div>
              <span>{w("مصدر البيانات", "Data origin")}</span>
              <strong>{property.dataOrigin}</strong>
            </div>
            <div>
              <span>{w("المرجع", "Reference")}</span>
              <strong className="record-reference" dir="ltr">
                {property.id}
              </strong>
            </div>
          </div>
          {!listing && (
            <div className="connected-form">
              <label htmlFor="listing-type">
                {w("غرض مسودة الإعلان", "Listing draft purpose")}
              </label>
              <select
                id="listing-type"
                disabled={busy}
                value={transactionType}
                onChange={(event) =>
                  setTransactionType(event.target.value as "SALE" | "RENT")
                }
              >
                <option value="SALE">{w("بيع", "Sale")}</option>
                <option value="RENT">{w("إيجار", "Rent")}</option>
              </select>
              <label>
                <input
                  type="checkbox"
                  disabled={busy}
                  checked={approved}
                  onChange={(event) => setApproved(event.target.checked)}
                />
                {w(
                  "أؤكد إنشاء مسودة خاصة، دون نشرها للعامة.",
                  "I confirm creating a private draft without public publication.",
                )}
              </label>
              <button
                className="gold-button"
                disabled={
                  busy ||
                  !approved ||
                  !can("oula.listing.write") ||
                  !can("oula.listing.read")
                }
                onClick={draft}
              >
                {w("احفظ مسودة الإعلان", "Save listing draft")}
              </button>
            </div>
          )}
          {listing && (
            <StatusBanner tone="success">
              <span data-testid="workspace-listing">
                {w("حُفظت المسودة الخاصة", "Private draft saved")} ·{" "}
                {listing.status} · <span dir="ltr">{listing.id}</span>
              </span>
            </StatusBanner>
          )}
          <button
            className="glass-button"
            disabled={busy}
            onClick={() => {
              setProperty(null);
              setListing(null);
              setApproved(false);
              commands.current.clear();
            }}
          >
            {w("أضف عقارًا آخر", "Add another property")}
          </button>
        </>
      )}
      <form
        noValidate
        className="connected-form compact"
        onSubmit={(event) => {
          event.preventDefault();
          read();
        }}
      >
        <label htmlFor="saved-property">
          {w("أعد فتح عقار محفوظ", "Reopen a saved property")}
        </label>
        <div className="record-controls">
          <input
            id="saved-property"
            value={recordId}
            disabled={busy}
            onChange={(event) => setRecordId(event.target.value)}
            maxLength={36}
            dir="ltr"
          />
          <button
            className="glass-button"
            type="submit"
            disabled={busy || !can("oula.property.read")}
          >
            {w("اقرأ العقار", "Read property")}
          </button>
        </div>
      </form>
      {busy && (
        <StatusBanner>
          {w(
            "جارٍ الحفظ والتحقق من السجل…",
            "Saving and checking the stored record…",
          )}
        </StatusBanner>
      )}
    </section>
  );
}
