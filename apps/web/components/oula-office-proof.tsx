"use client";

import { useState } from "react";
import Link from "next/link";
import { acquireDevAuthSession, devContext, type DevAuthSession } from "../lib/dev-auth";
import { createOfficeMembershipApi, type OfficeInvitation, type OfficeMember } from "../lib/office-membership";

const OFFICE="20000000-0000-4000-8000-000000000005";
const RECIPIENT="10000000-0000-4000-8000-000000000006";
const ISSUER="http://localhost:8080/dev-auth";
const SUBJECT="dev-fixture:office-invitee";

/** Isolated, nonproduction signed-session + PostgreSQL membership demonstration. */
export function OulaOfficeProof({ baseUrl }: { baseUrl: string }) {
  const [admin,setAdmin]=useState<DevAuthSession|null>(null);
  const [invitee,setInvitee]=useState<DevAuthSession|null>(null);
  const [members,setMembers]=useState<readonly OfficeMember[]>([]);
  const [invitations,setInvitations]=useState<readonly OfficeInvitation[]>([]);
  const [mine,setMine]=useState<readonly OfficeInvitation[]>([]);
  const [busy,setBusy]=useState(false);
  const [error,setError]=useState<string|null>(null);
  const [message,setMessage]=useState("");
  const api=(session:DevAuthSession)=>createOfficeMembershipApi(
    baseUrl,devContext(session,"TRANSACTION_EXECUTION"));

  const execute=async(work:()=>Promise<void>)=>{
    if(busy)return;
    setBusy(true);setError(null);
    try {await work();}
    catch(cause){setError(cause instanceof Error?cause.message:String(cause));}
    finally {setBusy(false);}
  };
  const refresh=async(a:DevAuthSession,b:DevAuthSession)=>{
    const [active,pending,myPending]=await Promise.all([
      api(a).activeMembers(),api(a).pendingInvitations(),api(b).myInvitations(),
    ]);
    setMembers(active);setInvitations(pending);setMine(myPending);
  };
  const connect=()=>execute(async()=>{
    setAdmin(null);setInvitee(null);setMembers([]);setInvitations([]);setMine([]);
    const [a,b]=await Promise.all([
      acquireDevAuthSession(baseUrl,"office-admin"),
      acquireDevAuthSession(baseUrl,"office-invitee"),
    ]);
    if(a.workspaceId!==OFFICE || b.workspaceId!==OFFICE ||
       a.actorId===b.actorId || a.mode!=="DEV_TEST_ONLY" || b.mode!=="DEV_TEST_ONLY" ||
       !a.scopes.includes("oula.office.members.manage") ||
       b.scopes.includes("oula.office.members.manage")) {
      throw new Error("Synthetic office identity boundary mismatch");
    }
    await refresh(a,b);
    setAdmin(a);setInvitee(b);
    setMessage("تأكيد جلسات موقعة، واسترجاع العضويات من قاعدة البيانات.");
  });
  const invite=()=>execute(async()=>{
    if(!admin || !invitee) return;
    await api(admin).invite({
      actorId:invitee.actorId,issuer:ISSUER,subject:SUBJECT,
      role:"BROKER",expiresAt:new Date(Date.now()+3600000).toISOString(),
    },crypto.randomUUID());
    await refresh(admin,invitee);
    setMessage("دعوة معلّقة؛ لا صلاحية للوسيط قبل الموافقة.");
  });
  const accept=()=>execute(async()=>{
    if(!admin || !invitee)return;
    const current=mine.find(i=>i.actorId===RECIPIENT);
    if(!current)return;
    await api(invitee).accept(current.invitationId,crypto.randomUUID());
    await refresh(admin,invitee);
    setMessage("قُبلت الدعوة من هوية الوسيط المستلم؛ العضوية نشطة.");
  });
  const cancel=()=>execute(async()=>{
    if(!admin || !invitee)return;
    const pending=invitations.find(i=>i.actorId===RECIPIENT);
    if(!pending)return;
    await api(admin).cancel(pending.invitationId,crypto.randomUUID());
    await refresh(admin,invitee);
    setMessage("أُلغيت الدعوة، وأصبحت غير قابلة للقبول.");
  });
  const revoke=()=>execute(async()=>{
    if(!admin || !invitee)return;
    await api(admin).revoke(RECIPIENT,crypto.randomUUID());
    await refresh(admin,invitee);
    setMessage("تم سحب العضوية مع إلغاء تفويضات قراءة الصفقات.");
  });
  const joined=members.some(m=>m.actorId===RECIPIENT);
  const pending=invitations.some(i=>i.actorId===RECIPIENT);
  const btn:React.CSSProperties={padding:"12px 16px",borderRadius:11,
    background:"#d4efe2",border:"1px solid #85b6a8",color:"#103831",
    fontWeight:700,cursor:"pointer"};
  const panel:React.CSSProperties={border:"1px solid #416f65",borderRadius:16,
    padding:20,background:"#153b3b",minWidth:0};
  return <main dir="rtl" data-testid="office-proof" style={{minHeight:"100vh",
    padding:"clamp(20px,4vw,60px)",background:"linear-gradient(135deg,#09212c,#154540)",
    color:"#f1f8f4",fontFamily:"Tahoma,system-ui,sans-serif"}}>
    <div style={{maxWidth:1080,margin:"0 auto"}}>
      <p style={{fontSize:12,letterSpacing:2,color:"#addccd"}}>OULA NEXUS · DEV_TEST_ONLY</p>
      <h1 style={{fontSize:"clamp(30px,4vw,44px)",margin:"10px 0"}}>مركز عضوية المكتب العقاري</h1>
      <p style={{lineHeight:1.9,maxWidth:780,color:"#c8e1d8"}}>
        تجربة تطويرية خيالية موصولة فعليًا بـ Java وPostgreSQL.
        يوجّه المدير الدعوة ويقبلها الموظف بنفسه قبل أن يصبح عضوًا.
        ليست منصة تسجيل مكاتب إنتاجية أو إثبات ترخيص وساطة.
      </p>
      <p><Link href="/gateway" style={{color:"#b6e6d2"}}>العودة إلى عوالم عُلا ↗</Link></p>
      <section aria-label="عضوية المكتب" style={{...panel,padding:"clamp(18px,3vw,35px)"}}>
        <h2>إدارة الدعوات والصلاحيات</h2>
        <div style={{display:"flex",flexWrap:"wrap",gap:12,margin:"18px 0"}}>
          <button type="button" data-testid="office-connect" disabled={busy}
            style={btn} onClick={()=>void connect()}>اتصال بهويات تطويرية موقعة</button>
          <button type="button" data-testid="office-refresh" disabled={busy || !admin || !invitee}
            style={btn} onClick={()=>void (admin&&invitee?execute(async()=>{
              await refresh(admin,invitee);setMessage("السجلات محدثة من الخادم.");
            }):Promise.resolve())}>تحديث البيانات</button>
          <button type="button" data-testid="office-invite"
            disabled={busy || !admin || !invitee || pending || joined}
            style={btn} onClick={()=>void invite()}>دعوة وسيط</button>
          <button type="button" data-testid="office-accept"
            disabled={busy || !invitee || mine.length===0}
            style={btn} onClick={()=>void accept()}>قبول دعوة الوسيط</button>
          <button type="button" data-testid="office-cancel"
            disabled={busy || !admin || !pending}
            style={btn} onClick={()=>void cancel()}>إلغاء الدعوة</button>
          <button type="button" data-testid="office-revoke"
            disabled={busy || !admin || !joined}
            style={btn} onClick={()=>void revoke()}>سحب العضوية</button>
        </div>
        <p>الجلسة: <strong data-testid="office-session">{admin&&invitee?"SIGNED DEV SESSION":"NOT CONNECTED"}</strong></p>
        {busy&&<p role="status">جارٍ التحقق من الصلاحيات…</p>}
        {message&&<p data-testid="office-message" role="status" style={{color:"#adddcc"}}>{message}</p>}
        {error&&<p data-testid="office-error" role="alert" style={{color:"#ffd1c8"}}>{error}</p>}
        <div style={{display:"grid",gridTemplateColumns:"repeat(auto-fit,minmax(230px,1fr))",gap:16,marginTop:20}}>
          <section style={panel} aria-label="الأعضاء النشطون">
            <h3>الأعضاء النشطون</h3>
            <strong data-testid="office-active-count">{members.length}</strong>
            <ul>{members.map(m=><li key={m.actorId} style={{margin:"10px 0"}}>
              <strong>{m.role}</strong> · <code dir="ltr">{m.actorId}</code>
            </li>)}</ul>
          </section>
          <section style={panel} aria-label="دعوات المدير">
            <h3>دعوات الإدارة المعلّقة</h3>
            <strong data-testid="office-pending-count">{invitations.length}</strong>
            <ul>{invitations.map(i=><li key={i.invitationId}>{i.role} · {i.status}</li>)}</ul>
          </section>
          <section style={panel} aria-label="دعوات الموظف">
            <h3>صندوق دعوات الموظف</h3>
            <strong data-testid="office-mine-count">{mine.length}</strong>
            <ul>{mine.map(i=><li key={i.invitationId}>{i.role} · {i.status}</li>)}</ul>
          </section>
        </div>
        <p style={{fontSize:12,color:"#b0d4c9",lineHeight:1.8}}>
          الصلاحيات تتحدد بالخادم لا بالواجهة. الرموز في ذاكرة هذه الصفحة فقط؛
          لا تُحفظ في التخزين المحلي، وتختفي مع إعادة تحميل الصفحة.
          العضوية الخيالية المُلغاة لا يعاد إنشاؤها تلقائيًا.
        </p>
      </section>
    </div>
  </main>;
}
