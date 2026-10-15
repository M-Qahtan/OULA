"use client";

import { useState } from "react";
import Link from "next/link";
import { acquireDevAuthSession, devContext } from "../lib/dev-auth";
import { fetchOulaExperienceManifest, type ExperienceManifest } from "../lib/experience-manifest";
import type { OulaLanguage } from "../lib/gateway";

type Profile = "buyer" | "property-manager";

const labels: Record<string, { ar: string; en: string }> = {
  INTENT_CREATE: { ar: "تسجيل الاحتياج العقاري", en: "Create a housing intent" },
  MATCH_RUN: { ar: "مطابقة العقارات", en: "Run property matching" },
  PROPERTY_PASSPORT: { ar: "قراءة جواز العقار", en: "Read property passport" },
  MANAGEMENT_OVERVIEW: { ar: "متابعة التزامات العقار", en: "Review property obligations" },
  DEAL_READ: { ar: "الاطلاع على الصفقة", en: "Read transaction" },
  DEAL_ADVANCE: { ar: "تحديث حالة الصفقة", en: "Advance transaction" },
};

/** A real read-only JWT-scoped projection, shown ONLY in explicit dev fixture builds. */
export function OulaExperienceLensProof({ apiBaseUrl, language }: {
  apiBaseUrl: string;
  language: OulaLanguage;
}) {
  const [profile, setProfile] = useState<Profile | null>(null);
  const [manifest, setManifest] = useState<ExperienceManifest | null>(null);
  const [loading, setLoading] = useState<Profile | null>(null);
  const [error, setError] = useState<string | null>(null);
  const isAr = language === "ar";
  const t = (ar: string, en: string) => isAr ? ar : en;

  const connect = async (next: Profile) => {
    setProfile(null); setManifest(null); setError(null); setLoading(next);
    try {
      const session = await acquireDevAuthSession(apiBaseUrl, next);
      const purpose = next === "buyer" ? "PROPERTY_DECISION_SUPPORT" : "PROPERTY_MANAGEMENT";
      const response = await fetchOulaExperienceManifest(apiBaseUrl, devContext(session, purpose));
      if (response.lens !== (next === "buyer" ? "PULSE" : "HABITAT")) {
        throw new Error("Authenticated experience lens mismatch");
      }
      setProfile(next); setManifest(response);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : String(cause));
    } finally {
      setLoading(null);
    }
  };

  return <section data-testid="experience-lens-proof" aria-label="Authenticated experience proof"
    style={{ margin:"32px auto", maxWidth:1000, padding:"clamp(18px,3vw,36px)", position:"relative", zIndex:2,
      color:"#edf9f3", background:"linear-gradient(125deg,#0a2931,#15443f)",
      border:"1px solid #49786e", borderRadius:22, boxShadow:"0 20px 60px #020b1230" }}>
    <span style={{fontSize:11,letterSpacing:2,color:"#a5d7cb"}}>OULA TRUSTED EXPERIENCE / DEV_TEST_ONLY</span>
    <h2 style={{fontSize:"clamp(23px,3vw,32px)",margin:"12px 0 8px"}}>
      {t("واجهتك من صلاحياتك الفعلية", "Your workspace, from real permissions")}
    </h2>
    <p style={{fontSize:14,lineHeight:1.9,color:"#d3e3dd",maxWidth:750}}>
      {t("اختر هوية تطوير خيالية لتجربة كيف يحدد خادم Java ما يُسمح لهذا المستخدم برؤيته. هذا ليس تسجيل دخول إنتاجيًا، ولا يمنحك اختيار الواجهة صلاحيات جديدة.",
        "Pick a fictional development identity. Java determines its available capabilities using the signed token, workspace, purpose and scopes. This is not production login or an authority grant.")}
    </p>
    <div style={{display:"flex",flexWrap:"wrap",gap:10,margin:"20px 0"}}>
      <button type="button" data-testid="lens-buyer" disabled={!!loading} onClick={()=>void connect("buyer")}
        style={{padding:"12px 20px",borderRadius:11,border:"1px solid #92b9ac",background:"#d5f1e5",color:"#12372b",cursor:"pointer",fontWeight:700}}>
        {t("تجربة المشتري", "Buyer profile")}
      </button>
      <button type="button" data-testid="lens-manager" disabled={!!loading} onClick={()=>void connect("property-manager")}
        style={{padding:"12px 20px",borderRadius:11,border:"1px solid #92b9ac",background:"#103d3d",color:"#f1faf4",cursor:"pointer",fontWeight:700}}>
        {t("تجربة مدير الأملاك", "Property manager profile")}
      </button>
    </div>
    {loading && <p role="status">{t("جارٍ التحقق من الصلاحيات…", "Checking signed permissions…")}</p>}
    {error && <p role="alert" data-testid="lens-error">{error}</p>}
    {manifest && profile && <div data-testid="lens-result" style={{borderTop:"1px solid #49786e",paddingTop:18}}>
      <p data-testid="lens-identity" style={{margin:"0 0 12px",fontWeight:700}}>
        {t("العالم المصرح: ", "Authorized lens: ")}{manifest.lens}
        {" · "}{t("عدد القدرات: ", "Capabilities: ")}{manifest.capabilities.length}
      </p>
      <div data-testid="lens-capabilities" style={{display:"flex",flexWrap:"wrap",gap:10,marginBottom:15}}>
        {manifest.capabilities.map(item=><span key={item.code} data-testid={"lens-capability-"+item.code}
          style={{border:"1px solid #678e81",borderRadius:9,padding:"10px 12px",fontSize:13}}>
          {labels[item.code]?.[language] ?? item.code}
          <small style={{display:"block",color:"#b6cfc7",fontSize:10,marginTop:4}}>{item.mode} / {item.code}</small>
        </span>)}
      </div>
      {manifest.capabilities.length === 0 && <p>{t("لم تُمنح أي قدرات لهذا السياق.", "No capabilities granted in this context.")}</p>}
      <Link data-testid="lens-open-workspace" href={profile === "buyer" ? "/" : "/guardian-proof"}
        style={{display:"inline-block",padding:"10px 16px",borderRadius:10,background:"#d5f1e5",color:"#153c36",fontWeight:700,textDecoration:"none"}}>
        {profile === "buyer" ? t("افتح رحلة المشتري التجريبية", "Open buyer dev journey")
          : t("افتح حارس العقار التجريبي", "Open Guardian dev journey")} ↗
      </Link>
      <p style={{fontSize:11,color:"#a5c1bb",marginTop:15}}>
        {t("المصدر: استجابة الخادم موثّقة الصلاحيات · لا تحفظ الرموز في المتصفح بعد انتهاء الجلسة · البيانات خيالية.",
          "Server-authorized scope projection · token held only during the request · fictional data.")}
      </p>
    </div>}
  </section>;
}
