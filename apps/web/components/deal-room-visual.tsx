"use client";
import type { DealRoomResponse } from "../lib/api";
import { SpatialMetric, SpatialScene } from "./spatial-primitives";
import type { Language } from "./workspace-primitives";

export function DealRoomVisual({
  room,
  language,
}: {
  room: DealRoomResponse;
  language: Language;
}) {
  const w = (ar: string, en: string) => (language === "ar" ? ar : en);
  const steps = [
    ["DRAFT", w("مسودة", "Draft")],
    ["QUALIFIED", w("تمت المراجعة الأولية", "Qualified")],
    ["VIEWING", w("المعاينة مطلوبة", "Viewing requested")],
    ["OFFERING", w("تم تقديم العرض", "Offer submitted")],
  ];
  const stage =
    steps.find((s) => s[0] === room.transaction.stage)?.[1] ??
    room.transaction.stage;
  const current = steps.findIndex((s) => s[0] === room.transaction.stage);
  const date = (s: string) =>
    new Intl.DateTimeFormat(language === "ar" ? "ar-SA" : "en-GB", {
      dateStyle: "medium",
      timeStyle: "short",
      timeZone: "Asia/Riyadh",
    }).format(new Date(s));
  return (
    <div data-testid="spatial-deal-room">
      <SpatialScene
        compact
        language={language}
        title={w("خطوتك التالية، بكل وضوح", "Your next step, in focus")}
        subtitle={w(
          "المعاينة طلب مسجل. والعرض تقديم من طرفك. أنت تتحكم بكل التزام.",
          "A viewing is a recorded request. An offer is your submission. You control every commitment.",
        )}
      />
      <ol
        className="spatial-stage-track"
        aria-label={w("مراحل الصفقة المسجلة", "Recorded transaction stages")}
      >
        {steps.map(([id, label], i) => (
          <li
            key={id}
            data-complete={current >= i}
            aria-current={current === i ? "step" : undefined}
          >
            {label}
          </li>
        ))}
      </ol>
      <div
        className="spatial-metrics grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-3"
        data-testid="workspace-deal-state"
      >
        <SpatialMetric
          icon="deal"
          label={w("حالة الصفقة", "Transaction stage")}
          value={stage}
        />
        <SpatialMetric
          icon="location"
          label={w("المعاينات", "Viewings")}
          value={room.viewings.length}
          detail={w("طلبات مسجلة", "Recorded requests")}
        />
        <SpatialMetric
          icon="decision"
          label={w("العروض", "Offers")}
          value={room.offers.length}
          detail={w(
            "عروض مقدمة، لا قبول ضمني",
            "Submissions, no implied acceptance",
          )}
        />
        <SpatialMetric
          icon="passport"
          label={w("نسخة السجل", "Record version")}
          value={room.transaction.version}
        />
      </div>
      <div className="spatial-deal-records">
        <article>
          <h3>{w("مسار المعاينة", "Viewing record")}</h3>
          {room.viewings.length ? (
            room.viewings.map((item) => (
              <p key={item.id}>
                {w("طلب معاينة", "Viewing request")} ·{" "}
                {item.preferredAt
                  ? `${date(item.preferredAt)} · ${w("توقيت الرياض", "Riyadh time")}`
                  : w("الموعد للتنسيق", "Time to be coordinated")}{" "}
                · {item.status}
              </p>
            ))
          ) : (
            <p>
              {w(
                "لا توجد طلبات معاينة مسجلة.",
                "No viewing requests recorded.",
              )}
            </p>
          )}
        </article>
        <article>
          <h3>{w("مسار العروض", "Offer record")}</h3>
          {room.offers.length ? (
            room.offers.map((item) => (
              <p key={item.id}>
                {w("عرض مقدّم", "Submitted offer")}:{" "}
                {new Intl.NumberFormat(language === "ar" ? "ar-SA" : "en-US", {
                  style: "currency",
                  currency: item.currency,
                }).format(item.amount)}{" "}
                · {item.status}
                {item.termsSummary && <> · {item.termsSummary}</>}
              </p>
            ))
          ) : (
            <p>{w("لا توجد عروض مقدمة بعد.", "No offers submitted yet.")}</p>
          )}
        </article>
      </div>
      <p className="record-reference">
        {w("معرّف الصفقة", "Transaction ID")}:{" "}
        <span dir="ltr">{room.transaction.id}</span>
      </p>
    </div>
  );
}
