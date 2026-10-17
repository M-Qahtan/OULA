"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { acquireDevAuthSession, devContext } from "../lib/dev-auth";
import { fetchOulaExperienceManifest, type ExperienceManifest } from "../lib/experience-manifest";
import type { OulaLanguage } from "../lib/gateway";

type Profile = "buyer" | "property-manager" | "broker-readonly";

const labels: Record<string, { ar: string; en: string }> = {
  INTENT_CREATE: { ar: "تسجيل الاحتياج العقاري", en: "Create a housing intent" },
  MATCH_RUN: { ar: "مطابقة العقارات", en: "Run property matching" },
  PROPERTY_PASSPORT: { ar: "قراءة جواز العقار", en: "Read property passport" },
  MANAGEMENT_OVERVIEW: { ar: "متابعة التزامات العقار", en: "Review property obligations" },
  PROPERTY_INTAKE: { ar: "إضافة عقار معلن ذاتيًا", en: "Declare a private property" },
  PRIVATE_LISTING_DRAFT: { ar: "مسودة إعلان خاص غير منشور", en: "Create an unpublished private listing" },
  PRIVATE_LISTING_READ: { ar: "عرض إعلانات مساحتي الخاصة", en: "Read private workspace drafts" },
  DEAL_READ: { ar: "الاطلاع على الصفقة", en: "Read transaction" },
  DEAL_ROOM_READ: { ar: "قراءة غرفة الصفقة", en: "Read deal room" },
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
  const requestEpoch = useRef(0);

  useEffect(() => () => { requestEpoch.current += 1; }, []);
  const isAr = language === "ar";
  const t = (ar: string, en: string) => isAr ? ar : en;

  const connect = async (next: Profile) => {
    const epoch = ++requestEpoch.current;
    setProfile(null); setManifest(null); setError(null); setLoading(next);
    try {
      const session = await acquireDevAuthSession(apiBaseUrl, next);
      const purpose = next === "buyer" ? "PROPERTY_DECISION_SUPPORT"
        : next === "property-manager" ? "PROPERTY_MANAGEMENT" : "TRANSACTION_EXECUTION";
      const expectedLens = next === "buyer" ? "PULSE"
        : next === "property-manager" ? "HABITAT" : "NEXUS";
      const response = await fetchOulaExperienceManifest(apiBaseUrl, devContext(session, purpose));
      if (response.lens !== expectedLens) {
        throw new Error("Authenticated experience lens mismatch");
      }
      if (requestEpoch.current !== epoch) return;
      setProfile(next); setManifest(response);
    } catch (cause) {
      if (requestEpoch.current === epoch) setError(cause instanceof Error ? cause.message : String(cause));
    } finally {
      if (requestEpoch.current === epoch) setLoading(null);
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
      <button type="button" data-testid="lens-buyer" onClick={()=>void connect("buyer")}
        style={{padding:"12px 20px",borderRadius:11,border:"1px solid #92b9ac",background:"#d5f1e5",color:"#12372b",cursor:"pointer",fontWeight:700}}>
        {t("تجربة المشتري", "Buyer profile")}
      </button>
      <button type="button" data-testid="lens-manager" onClick={()=>void connect("property-manager")}
        style={{padding:"12px 20px",borderRadius:11,border:"1px solid #92b9ac",background:"#103d3d",color:"#f1faf4",cursor:"pointer",fontWeight:700}}>
        {t("تجربة مدير الأملاك", "Property manager profile")}
      </button>
      <button type="button" data-testid="lens-broker" onClick={()=>void connect("broker-readonly")}
        style={{padding:"12px 20px",borderRadius:11,border:"1px solid #92b9ac",background:"#103d3d",color:"#f1faf4",cursor:"pointer",fontWeight:700}}>
        {t("تجربة الوسيط — قراءة فقط", "Broker preview — read only")}
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
      {profile === "property-manager" &&
        manifest.capabilities.some(item => item.code === "PROPERTY_INTAKE" && item.mode === "COMMAND") &&
        manifest.capabilities.some(item => item.code === "PRIVATE_LISTING_DRAFT" && item.mode === "COMMAND") &&
        <Link data-testid="lens-open-owner-intake" href="/owner-intake-proof"
          style={{display:"inline-block",padding:"10px 16px",borderRadius:10,marginInlineEnd:12,
            background:"#fff",color:"#153c36",fontWeight:700,textDecoration:"none"}}>
          {t("إضافة عقار وإعداد إعلان خاص تجريبي", "Create a fictional property and private draft")} ↗
        </Link>}
      <Link data-testid="lens-open-workspace"
        href={profile === "buyer" ? "/" : profile === "property-manager" ? "/guardian-proof" : "/gateway/nexus"}
        style={{display:"inline-block",padding:"10px 16px",borderRadius:10,background:"#d5f1e5",color:"#153c36",fontWeight:700,textDecoration:"none"}}>
        {profile === "buyer" ? t("افتح رحلة المشتري التجريبية", "Open buyer dev journey")
          : profile === "property-manager" ? t("افتح حارس العقار التجريبي", "Open Guardian dev journey")
          : t("استعرض واجهة الوسطاء التصورية", "Explore conceptual broker workspace")} ↗
      </Link>
      {profile === "broker-readonly" && <p data-testid="broker-preview-boundary" role="status"
        style={{fontSize:12,color:"#d6e7df",lineHeight:1.8}}>
        {t("بيئة تطوير خيالية للعرض فقط. يمكن اكتشاف صلاحيات قراءة الصفقات، لكن لا توجد صفقة حقيقية أو تفويض وساطة أو صلاحية لإنشائها أو توقيعها أو تحريك أموال.",
          "Fictional read-only broker preview. Deal-read discovery is scoped to the signed workspace. No real deal, broker mandate, creation, signing, or money movement.")}
      </p>}
      <p style={{fontSize:11,color:"#a5c1bb",marginTop:15}}>
        {t("المصدر: استجابة الخادم موثّقة الصلاحيات · لا تحفظ الرموز في المتصفح بعد انتهاء الجلسة · البيانات خيالية.",
          "Server-authorized scope projection · token held only during the request · fictional data.")}
      </p>
    </div>}
  </section>;
}
