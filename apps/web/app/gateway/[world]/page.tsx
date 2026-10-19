import { notFound } from "next/navigation";
import { Suspense } from "react";
import WorldWorkspace from "../world-workspace";
import { OULA_WORLDS, type OulaWorldId } from "../../../lib/gateway";
import "../../workspace.css";

export function generateStaticParams() {
  return OULA_WORLDS.map((world) => ({ world: world.id }));
}

export default async function WorldPage({
  params,
}: {
  params: Promise<{ world: string }>;
}) {
  const { world } = await params;
  if (!OULA_WORLDS.some((item) => item.id === world)) notFound();
  return (
    <Suspense fallback={<main aria-busy="true">عُلا · OULA</main>}>
      <WorldWorkspace worldId={world as OulaWorldId} />
    </Suspense>
  );
}
