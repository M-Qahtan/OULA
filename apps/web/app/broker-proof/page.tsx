import { notFound } from "next/navigation";
import { OulaBrokerProof } from "../../components/oula-broker-proof";

/** Deliberately compiled out of non-dev experience; never a real brokerage panel. */
export default function BrokerProofPage() {
  if (process.env.NEXT_PUBLIC_OULA_DEV_AUTH !== "true" ||
      !process.env.NEXT_PUBLIC_OULA_API_BASE_URL) notFound();
  return <OulaBrokerProof baseUrl={process.env.NEXT_PUBLIC_OULA_API_BASE_URL} />;
}
