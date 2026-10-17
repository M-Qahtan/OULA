"use client";

import { useState } from "react";
import Link from "next/link";
import { acquireDevAuthSession, devContext, prepareDevBrokerDeal } from "../lib/dev-auth";
import { createOulaApi, type DealRoomResponse } from "../lib/api";

const FICTIONAL_DEAL_ID = "50000000-0000-4000-8000-000000000004";

/** A real signed read-only Java/PostgreSQL proof; no mutation API is exposed in this UI. */
export function OulaBrokerProof({ baseUrl }: { baseUrl: string }) {
  const [room, setRoom] = useState<DealRoomResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function connect() {
    setLoading(true);
    setError(null);
    setRoom(null);
    try {
      const session = await acquireDevAuthSession(baseUrl, "broker-readonly");
      if (session.mode !== "DEV_TEST_ONLY" || session.fixture !== "broker-readonly" ||
          session.scopes.includes("oula.transaction.advance") ||
          session.scopes.includes("oula.transaction.create") ||
          session.scopes.includes("oula.transaction.offer.submit")) {
        throw new Error("Read-only fictional broker identity required");
      }
      const prepared = await prepareDevBrokerDeal(baseUrl, session);
      if (prepared.transactionId !== FICTIONAL_DEAL_ID || prepared.workspaceId !== session.workspaceId) {
        throw new Error("Broker fixture workspace or transaction mismatch");
      }
      const response = await createOulaApi(baseUrl, devContext(session, "TRANSACTION_EXECUTION"))
        .getDealRoom(FICTIONAL_DEAL_ID);
      if (response.transaction.id !== FICTIONAL_DEAL_ID ||
          response.transaction.workspaceId !== session.workspaceId ||
          response.transaction.stage !== "DRAFT" ||
          response.availableCommands.length !== 0 ||
          response.viewings.length !== 0 || response.offers.length !== 0) {
        throw new Error("Fictional deal boundary or expected read-only state mismatch");
      }
      setRoom(response);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : String(cause));
    } finally {
      setLoading(false);
    }
  }

  return <main dir="rtl" data-testid="broker-proof"
    style={{background:"#081d27",minHeight:"100vh",padding:"clamp(20px,5vw,70px)",
      fontFamily:"Tahoma,system-ui,sans-serif",color:"#edf8f3"}}>
    <section style={{maxWidth:950,margin:"0 auto",border:"1px solid #386860",
      borderRadius:22,padding:"clamp(20px,4vw,42px)",background:"#123539"}}>
      <p style={{fontSize:12,letterSpacing:1.8,color:"#bce5d9"}}>OULA NEXUS · DEV_TEST_ONLY</p>
      <h1 style={{fontSize:"clamp(28px,4vw,42px)",margin:"8px 0 12px"}}>غرفة الصفقة — عرض الوسيط للقراءة فقط</h1>
      <p style={{lineHeight:1.9}}>تجربة خيالية متصلة فعليًا بـ Java وPostgreSQL.
        تعرض حالة صفقة تجريبية معزولة داخل مساحة عمل وسيط افتراضي.
        لا يوجد تفويض وساطة حقيقي أو عقود موقعة أو تحويلات مالية.</p>
      <p><Link href="/gateway" style={{color:"#d8efd9"}}>العودة إلى عوالم عُلا ↗</Link></p>
      <button type="button" data-testid="broker-connect" onClick={()=>void connect()}
        disabled={loading}
        style={{border:0,borderRadius:12,padding:"13px 20px",fontWeight:700,
          background:"#d9f1e3",color:"#12352b",cursor:"pointer"}}>
        {loading ? "جارٍ التحقق..." : "اتصال بهوية الوسيط التجريبية"}
      </button>
      <p>حالة الاتصال: <strong data-testid="broker-session">{room ? "READ ONLY" : "NOT CONNECTED"}</strong></p>
      {room && <section data-testid="broker-read-only-room" style={{borderTop:"1px solid #477166",paddingTop:20}}>
        <p>مساحة العمل: <code data-testid="broker-workspace" dir="ltr">{room.transaction.workspaceId}</code></p>
        <p>معرّف الصفقة: <code data-testid="broker-transaction-id" dir="ltr">{room.transaction.id}</code></p>
        <p>مرحلة الصفقة: <strong data-testid="broker-deal-stage">{room.transaction.stage}</strong></p>
        <p>المعاينات المسجلة: {room.viewings.length} · العروض المسجلة: {room.offers.length}</p>
        <p>الأوامر المصرح بها: <strong data-testid="broker-available-commands">{room.availableCommands.length}</strong></p>
        <p style={{color:"#b3dace"}}>المصدر: استجابة Deal Room من API بوساطة JWT موقّع.
          لا تُحفظ رموز الدخول بعد تحديث الصفحة، ولا تُعرض أي أزرار كتابة.</p>
      </section>}
      {error && <p data-testid="broker-error" role="alert" style={{color:"#ffd0c8"}}>{error}</p>}
    </section>
  </main>;
}
