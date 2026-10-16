"use client";

import { useMemo, useRef, useState } from "react";
import { acquireDevAuthSession, devContext, type DevAuthSession } from "../lib/dev-auth";
import { createPrivatePropertyIntakeClient, type DeclaredProperty, type DraftListing } from "../lib/property-intake-client";

export function OwnerIntakeProof({ baseUrl }: { baseUrl: string }) {
  const [session, setSession] = useState<DevAuthSession | null>(null);
  const [district, setDistrict] = useState("الملقا");
  const [price, setPrice] = useState("1250000");
  const [bedrooms, setBedrooms] = useState("3");
  const [property, setProperty] = useState<DeclaredProperty | null>(null);
  const [listing, setListing] = useState<DraftListing | null>(null);
  const [verifiedRead, setVerifiedRead] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const keys = useRef<{ property?: string; listing?: string }>({});
  const api = useMemo(() => session
    ? createPrivatePropertyIntakeClient(baseUrl, devContext(session,"PROPERTY_MANAGEMENT"))
    : null, [baseUrl,session]);
  const execute = async (fn: () => Promise<void>) => {
    setBusy(true); setError(null);
    try { await fn(); } catch (e) { setError(e instanceof Error ? e.message : String(e)); }
    finally { setBusy(false); }
  };
  const connect = () => execute(async () => {
    const logged = await acquireDevAuthSession(baseUrl,"property-manager");
    if (logged.mode !== "DEV_TEST_ONLY" || logged.fixture !== "property-manager") {
      throw new Error("Only a signed fictional property-manager is accepted");
    }
    keys.current = {}; setProperty(null); setListing(null); setVerifiedRead(false);
    setSession(logged);
  });
  const create = () => execute(async () => {
    if (!api) throw new Error("Authenticated manager required");
    const askingPrice = Number(price), beds = Number(bedrooms);
    if (!Number.isFinite(askingPrice) || askingPrice <= 0 ||
        !Number.isInteger(beds) || beds < 0 || beds > 100 ||
        !district.trim() || district.length > 160) throw new Error("Invalid declared property details");
    keys.current.property ??= crypto.randomUUID();
    const record = await api.createProperty({
      assetType:"RESIDENTIAL",district:district.trim(),bedrooms:beds,
      askingPrice, demo:true
    },keys.current.property);
    if (record.truthStatus !== "DECLARED" || record.dataOrigin !== "DEMO") {
      throw new Error("Rejecting unsupported verified-property assertion");
    }
    setProperty(record); setVerifiedRead(false);
  });
  const makeDraft = () => execute(async () => {
    if (!api || !property) throw new Error("Property must be saved first");
    keys.current.listing ??= crypto.randomUUID();
    const result = await api.createListing(property.id, "SALE", property.askingPrice,keys.current.listing);
    if (result.status !== "DRAFT" || result.truthStatus !== "DECLARED") {
      throw new Error("Listing may only be a private unverified DRAFT");
    }
    setListing(result); setVerifiedRead(false);
  });
  const reread = () => execute(async () => {
    if (!api || !property || !listing) throw new Error("Complete intake first");
    const savedProperty=await api.readProperty(property.id);
    const savedListing=await api.readListing(listing.id);
    const privateDrafts=await api.searchPrivateDrafts(property.district);
    if (savedProperty.id!==property.id || savedProperty.truthStatus!=="DECLARED" ||
        savedListing.id!==listing.id || savedListing.status!=="DRAFT" ||
        !privateDrafts.some(x=>x.id===listing.id)) throw new Error("Canonical database verification failed");
    setProperty(savedProperty); setListing(savedListing); setVerifiedRead(true);
  });

  const style={border:"1px solid #b8c9c5",borderRadius:12,padding:"12px 14px",width:"100%",maxWidth:300};
  return <main dir="rtl" data-testid="owner-intake-proof"
    style={{fontFamily:"Tahoma,system-ui,sans-serif",minHeight:"100vh",background:"#f3f7f5",color:"#153a33",padding:"24px"}}>
    <section style={{maxWidth:1000,margin:"0 auto",background:"#fff",borderRadius:22,
      padding:"clamp(18px,4vw,45px)",boxShadow:"0 24px 72px #1a31231c"}}>
      <p style={{fontWeight:700,letterSpacing:1,color:"#55776d"}}>OULA · HABITAT · DEV_TEST_ONLY</p>
      <h1 style={{fontSize:"clamp(25px,4vw,39px)",margin:"6px 0"}}>إدخال العقار وإعداد إعلان خاص</h1>
      <p>هذه تجربة تطوير متصلة بـ Java وPostgreSQL، بهوية مالك خيالية وبيانات غير موثّقة.
        <strong> لا نشر عام، ولا عقد، ولا تحصيل، ولا تحقق ملكية.</strong></p>
      <div style={{display:"grid",gridTemplateColumns:"repeat(auto-fit,minmax(220px,1fr))",gap:20,margin:"28px 0"}}>
        <label>الحي السكني<input aria-label="الحي السكني" data-testid="intake-district"
          disabled={!!property || busy} value={district} onChange={e=>{setDistrict(e.target.value);keys.current.property=undefined;}}
          style={style}/></label>
        <label>عدد الغرف<input aria-label="عدد الغرف" data-testid="intake-bedrooms"
          inputMode="numeric" disabled={!!property || busy} value={bedrooms}
          onChange={e=>{setBedrooms(e.target.value);keys.current.property=undefined;}} style={style}/></label>
        <label>السعر المعلن (ر.س)<input aria-label="السعر المعلن" data-testid="intake-price"
          inputMode="decimal" disabled={!!property || busy} value={price}
          onChange={e=>{setPrice(e.target.value);keys.current.property=undefined;}} style={style}/></label>
      </div>
      <div style={{display:"flex",flexWrap:"wrap",gap:10,margin:"14px 0"}}>
        <button data-testid="intake-connect" disabled={busy} onClick={connect}>١. دخول مدير الأملاك التجريبي</button>
        <button data-testid="intake-create" disabled={!session || !!property || busy} onClick={create}>٢. حفظ العقار</button>
        <button data-testid="intake-listing" disabled={!property || !!listing || busy} onClick={makeDraft}>٣. إنشاء إعلان غير منشور</button>
        <button data-testid="intake-verify" disabled={!listing || busy} onClick={reread}>٤. التحقق من قاعدة البيانات</button>
      </div>
      {session && <p data-testid="intake-workspace">مساحة العمل: <code>{session.workspaceId}</code></p>}
      {property && <section data-testid="intake-property" style={{borderTop:"1px solid #ddd",padding:"17px 0"}}>
        <strong>العقار محفوظ: {property.district}</strong>
        <p>معرّف: <code>{property.id}</code></p>
        <p>مصدر البيانات: {property.sourceType} · حالة الحقيقة: <b>{property.truthStatus}</b></p>
      </section>}
      {listing && <section data-testid="intake-draft" style={{borderTop:"1px solid #ddd",padding:"17px 0"}}>
        <strong>الإعلان: {listing.status}</strong>
        <p>معرّف: <code>{listing.id}</code></p>
        <p>النطاق: خاص لمساحة العمل فقط · الصنف: {listing.transactionType} · {listing.truthStatus}</p>
      </section>}
      {verifiedRead && <p data-testid="intake-database-verified" role="status">
        تم التأكد من قراءة العقار والإعلان الخاص من واجهات قاعدة البيانات: DECLARED / DRAFT.
      </p>}
      {busy && <p role="status">جارٍ تنفيذ الطلب المصرّح به…</p>}
      {error && <p data-testid="intake-error" role="alert">{error}</p>}
      <p style={{fontSize:12,color:"#637a72",marginTop:30}}>للعرض الهندسي فقط. لا تُستخدم هذه السجلات في معاملات أو إعلانات حقيقية.</p>
    </section>
  </main>;
}
