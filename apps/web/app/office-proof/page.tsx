import { notFound } from "next/navigation";
import { OulaOfficeProof } from "../../components/oula-office-proof";

/** Only available with explicitly enabled nonproduction dev-auth fixtures. */
export default function OfficeProofPage() {
  if(process.env.NEXT_PUBLIC_OULA_DEV_AUTH!=="true" ||
     !process.env.NEXT_PUBLIC_OULA_API_BASE_URL) notFound();
  return <OulaOfficeProof baseUrl={process.env.NEXT_PUBLIC_OULA_API_BASE_URL} />;
}
