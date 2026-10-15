import { notFound } from "next/navigation";
import { OulaGuardianProof } from "../../components/oula-guardian-proof";

// Compile-time gated to explicit local development/test mode. Never enable in production.
export default function GuardianProofPage() {
  if (process.env.NEXT_PUBLIC_OULA_DEV_AUTH !== "true" ||
      !process.env.NEXT_PUBLIC_OULA_API_BASE_URL) notFound();
  return <OulaGuardianProof baseUrl={process.env.NEXT_PUBLIC_OULA_API_BASE_URL} />;
}
