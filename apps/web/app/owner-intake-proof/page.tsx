import { notFound } from "next/navigation";
import { OwnerIntakeProof } from "../../components/owner-intake-proof";

/** Intentionally invisible in production builds, never a real listing publish UI. */
export default function OwnerIntakeProofPage() {
  if (process.env.NEXT_PUBLIC_OULA_DEV_AUTH !== "true" ||
      !process.env.NEXT_PUBLIC_OULA_API_BASE_URL) notFound();
  return <OwnerIntakeProof baseUrl={process.env.NEXT_PUBLIC_OULA_API_BASE_URL} />;
}
