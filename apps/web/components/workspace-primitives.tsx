"use client";
import { useId } from "react";
import type { Localized } from "../lib/personal-agent";
export type Language = "ar" | "en";
export const word = (language: Language, value: Localized) => value[language];
export function OulaMark({ size = 48 }: { size?: number }) {
  const id = useId().replaceAll(":", "");
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 80 92"
      aria-hidden="true"
      className="oula-mark"
    >
      <defs>
        <linearGradient id={id + "g"} x1="0" x2="1" y2="1">
          <stop stopColor="#fff1da" />
          <stop offset=".38" stopColor="#c9a276" />
          <stop offset=".72" stopColor="#fce5c0" />
          <stop offset="1" stopColor="#655048" />
        </linearGradient>
        <linearGradient id={id + "b"} x1="0" x2="1" y2="1">
          <stop stopColor="#d3ecff" />
          <stop offset=".4" stopColor="var(--world-accent)" />
          <stop offset="1" stopColor="#182b49" />
        </linearGradient>
      </defs>
      <path
        d="M53 6C25-6 2 33 7 62C9 82 24 94 40 84C17 74 15 55 22 34C28 15 43 8 53 6Z"
        fill={`url(#${id}b)`}
      />
      <path
        d="M53 6C72 11 76 32 66 56C60 73 44 90 29 87C53 78 57 52 54 36C52 23 45 13 35 11C41 6 48 4 53 6Z"
        fill={`url(#${id}g)`}
      />
      <path
        d="M34 10C21 26 18 49 25 65"
        fill="none"
        stroke="#e4efff"
        strokeWidth=".8"
        opacity=".6"
      />
    </svg>
  );
}
const paths: Record<string, React.ReactNode> = {
  overview: (
    <>
      <path d="m3 10 9-7 9 7" />
      <path d="M5 9v12h14V9M9 21v-8h6v8" />
    </>
  ),
  intent: (
    <>
      <path d="M21 11a8 8 0 0 1-8 8H6l-4 3V11a9 9 0 0 1 19 0Z" />
      <path d="M7 10h10M7 14h6" />
    </>
  ),
  matching: (
    <>
      <circle cx="12" cy="12" r="9" />
      <path d="m16 8-3 5-5 3 3-5Z" />
    </>
  ),
  passport: (
    <>
      <rect x="5" y="3" width="14" height="18" rx="2" />
      <path d="M9 7h6M9 11h6M9 15h3" />
    </>
  ),
  decision: (
    <>
      <path d="M5 20V10M12 20V4M19 20v-9M2 21h20" />
    </>
  ),
  deal: (
    <>
      <path d="m3 7 5-3 5 3 6-2 3 4-9 10-4-2-4-5Z" />
      <path d="m8 10 3 3 5-5" />
    </>
  ),
  guardian: (
    <>
      <path d="m12 3 8 4v6c0 4-8 8-8 8s-8-4-8-8V7Z" />
      <path d="m8 12 3 3 5-6" />
    </>
  ),
  studio: (
    <>
      <path d="m12 2 9 5v10l-9 5-9-5V7Z" />
      <path d="m3 7 9 5 9-5M12 12v10" />
    </>
  ),
  send: (
    <>
      <path d="m3 3 18 9-18 9 3-9Z" />
      <path d="M6 12h15" />
    </>
  ),
  arrow: (
    <>
      <path d="M4 12h16m-6-6 6 6-6 6" />
    </>
  ),
  location: (
    <>
      <path d="M19 10c0 5-7 11-7 11S5 15 5 10a7 7 0 1 1 14 0Z" />
      <circle cx="12" cy="10" r="2" />
    </>
  ),
};
export function Icon({ name, size = 21 }: { name: string; size?: number }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.5"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      {paths[name] ?? paths.studio}
    </svg>
  );
}
export function Wave({ active = false }: { active?: boolean }) {
  return (
    <span
      className={"voice-wave " + (active ? "active" : "")}
      aria-hidden="true"
    >
      {[7, 13, 21, 32, 18, 39, 25, 46, 29, 16, 34, 22, 12, 8].map(
        (height, i) => (
          <i key={i} style={{ height, animationDelay: i * 45 + "ms" }} />
        ),
      )}
    </span>
  );
}
export function StatusBanner({
  children,
  tone = "info",
}: {
  children: React.ReactNode;
  tone?: "info" | "error" | "success";
}) {
  return (
    <div
      className={"workspace-status " + tone}
      role={tone === "error" ? "alert" : "status"}
    >
      {children}
    </div>
  );
}
export function errorText(error: unknown, language: Language): string {
  const code = error instanceof Error ? error.message : "";
  const messages: Record<string, Localized> = {
    AUTH_REQUIRED: {
      ar: "سجّل الدخول للوصول إلى بيانات مساحة العمل.",
      en: "Sign in to access your workspace data.",
    },
    SESSION_EXPIRED: {
      ar: "انتهت جلسة الدخول. أعد تسجيل الدخول ثم حاول مجددًا.",
      en: "Your session expired. Sign in and try again.",
    },
    PERMISSION_DENIED: {
      ar: "لا تملك صلاحية هذا الإجراء في مساحة العمل الحالية.",
      en: "This action is not permitted in the current workspace.",
    },
    CONFLICT: {
      ar: "تعارض مع نسخة الخادم أو طلب سابق. أعد قراءة السجل قبل متابعة التعديل.",
      en: "Conflict with the server version or an earlier command. Reload the record before editing.",
    },
    NOT_FOUND: {
      ar: "السجل غير موجود في مساحة العمل الحالية.",
      en: "This record was not found in your current workspace.",
    },
    VALIDATION_FAILED: {
      ar: "تحقق من قيم الحقول ثم أعد المحاولة.",
      en: "Check the field values and try again.",
    },
    CONTRACT_MISMATCH: {
      ar: "تعذر عرض البيانات بأمان لأن استجابة الخدمة لا تطابق العقد المتوقع.",
      en: "The service response does not match the expected contract and cannot be displayed safely.",
    },
    BACKEND_UNAVAILABLE: {
      ar: "تعذر الاتصال بالخدمة. احتفظنا بالمدخلات لتتمكن من إعادة المحاولة.",
      en: "The service is unavailable. Your inputs are preserved for a retry.",
    },
  };
  return word(language, messages[code] ?? messages.BACKEND_UNAVAILABLE!);
}
