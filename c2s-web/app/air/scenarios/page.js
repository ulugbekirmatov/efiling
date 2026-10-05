import Link from "next/link";
import { listScenarios } from "../lib/scenarioCatalog";
import { Chip } from "../../design-system/Badge";
import "./air-scenarios.css";

export const dynamic = "force-dynamic";

export default async function AirScenariosPage() {
  const scenarios = await listScenarios();
  return (
    <div className="page">
      <header className="page-head">
        <p className="eyebrow">AIR</p>
        <h1>AATS test scenarios</h1>
        <p className="intro">
          Form 1094-C and 1095-C scenarios for the AIR assurance testing system. Pick one to preview
          the composed transmission or compose a run.
        </p>
      </header>
      <div className="air-scenarios-grid">
        {scenarios.map((scenario) => (
          <Link
            key={scenario.id}
            href={`/air/scenarios/${scenario.id}`}
            className="card lift air-scenarios-card"
          >
            <p className="section-label">Scenario {scenario.testScenarioId}</p>
            <h2 className="card-title">Tax year {scenario.taxYear}</h2>
            <p>
              {scenario.form1095CCount} Form 1095-C · {scenario.otherAleMemberCount} other ALE
              members
            </p>
            <div className="row air-scenarios-chips">
              <Chip>{scenario.offerStyle === "annual" ? "Annual offer" : "Monthly offer"}</Chip>
              <Chip tone={scenario.pdfs.length > 0 ? undefined : "waiting"}>
                {scenario.pdfs.length} PDFs
              </Chip>
            </div>
          </Link>
        ))}
      </div>
      {scenarios.length === 0 ? (
        <p className="callout waiting">No scenario files found in the AIR package.</p>
      ) : null}
    </div>
  );
}
