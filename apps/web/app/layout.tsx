import type { Metadata, Viewport } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "عُلا | OULA — Built World Intelligence",
  description: "عُلا: طبقة ذكاء وتشغيل للعالم المبني. النموذج الحالي للاختبار الداخلي فقط.",
};
export const viewport: Viewport = { width: "device-width", initialScale: 1 };

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return <html lang="ar" dir="rtl"><body>{children}</body></html>;
}
