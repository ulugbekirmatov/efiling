import { Suspense } from "react";
import Link from "next/link";
import { notFound } from "next/navigation";
import { loadScenario } from "../../lib/scenarioCatalog";
import { Chip } from "../../../design-system/Badge";
import AirScenarioTabs from "./AirScenarioTabs";
import ComposeButton from "./ComposeButton";
import "./air-scenario.css";

export const dynamic = "force-dynamic";

export default async function AirScenarioPage({ params }) {
  const loaded = await loadScenario(params.id);
  if (!loaded) notFound();
  const { summary, formXml, manifestXml } = loaded;

  return (
    <div className="page">
      <p className="crumbs">
        <Link href="/air/scenarios">AATS test scenarios</Link>
        {" / "}
        Scenario {summary.testScenarioId}
      </p>
      <header className="page-head">
        <p className="eyebrow">AIR</p>
        <h1>Scenario {summary.testScenarioId}</h1>
      </header>
      <section className="card air-scenario-summary" aria-label="Scenario summary">
        <p>
          Tax year {summary.taxYear} · {summary.form1095CCount} Form 1095-C ·{" "}
          {summary.otherAleMemberCount} other ALE members
        </p>
        <div className="row">
          <Chip>{summary.offerStyle === "annual" ? "Annual offer" : "Monthly offer"}</Chip>
          <Chip tone={summary.pdfs.length > 0 ? undefined : "waiting"}>
            {summary.pdfs.length} PDFs
          </Chip>
        </div>
        <ComposeButton scenarioId={summary.id} />
      </section>
      <Suspense fallback={<p className="muted air-scenario-loading">Loading…</p>}>
        <AirScenarioTabs summary={summary} formXml={formXml} manifestXml={manifestXml} />
      </Suspense>
    </div>
  );
}
