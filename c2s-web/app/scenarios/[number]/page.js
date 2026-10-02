import { Suspense } from "react";
import Link from "next/link";
import { notFound } from "next/navigation";
import { groupByDocument, loadScenario, sendClientId } from "../scenarioData";
import { Chip } from "../../design-system/Badge";
import { StatusBadge, formatEin } from "../ui";
import ScenarioTabs from "./ScenarioTabs";
import "./scenario.css";

export const dynamic = "force-dynamic";

export default function ScenarioDetailPage({ params }) {
  const loaded = loadScenario(params.number);
  if (!loaded) notFound();
  const { scenario, formats, lineMapping, hasPdf } = loaded;

  return (
    <div className="page">
      <p className="crumbs">
        <Link href="/scenarios">ATS test scenarios</Link>
        {" / "}
        Scenario {scenario.number}
      </p>
      <header className="page-head">
        <p className="eyebrow">Scenario {scenario.number}</p>
        <h1>{scenario.title}</h1>
      </header>
      <section className="card scenario-employer" aria-label="Employer and status">
        <p className="scenario-employer-name">{scenario.employer.name}</p>
        <p className="mono muted">
          {formatEin(scenario.employer.ein)}
          {scenario.employer.nameControl ? ` · ${scenario.employer.nameControl}` : ""}
        </p>
        <p className="muted">{scenario.employer.address}</p>
        <p>
          {scenario.returnTypeCd} · {scenario.quarter}
        </p>
        <div className="row">
          {scenario.documents.map((doc) => (
            <Chip key={doc}>
              {doc}
            </Chip>
          ))}
        </div>
        <StatusBadge status={scenario.status} />
        {scenario.status.note ? <p className="muted">{scenario.status.note}</p> : null}
        <p>
          Send journal client id <span className="mono">{sendClientId(scenario)}</span>
          {" · "}
          <Link href="/send-inspector">Open send inspector</Link>
        </p>
      </section>
      <Suspense fallback={<p className="muted scenario-loading">Loading…</p>}>
        <ScenarioTabs
          number={scenario.number}
          scenario={scenario}
          formats={formats}
          documentGroups={groupByDocument(lineMapping)}
          hasPdf={hasPdf}
        />
      </Suspense>
    </div>
  );
}
