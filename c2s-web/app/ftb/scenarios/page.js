import Link from "next/link";
import { listScenarios } from "../lib/ftbCatalog";
import { formLabel, stateLabel, stateTone } from "../lib/ftbStates";
import { Badge, Chip } from "../../design-system/Badge";
import "./ftb-scenarios.css";

export const dynamic = "force-dynamic";

function ScenarioCard({ scenario }) {
  const checksTone = scenario.checksPassed === scenario.checksTotal ? "safe" : "attention";
  return (
    <Link href={`/ftb/scenarios/${scenario.id}`} className="card lift ftb-scenarios-card">
      <p className="section-label">Scenario {scenario.id}</p>
      <h2 className="card-title">{formLabel(scenario.formKind)}</h2>
      <p>{scenario.purpose}</p>
      <div className="row ftb-scenarios-chips">
        <Badge tone={stateTone(scenario.state)}>{stateLabel(scenario.state)}</Badge>
        <Chip tone={checksTone}>
          {scenario.checksPassed} of {scenario.checksTotal} checks
        </Chip>
        <Chip>{scenario.recordCount} records</Chip>
        {scenario.correctionOf ? <Chip>Corrects {scenario.correctionOf}</Chip> : null}
      </div>
      <p className="muted">{scenario.nextAction}</p>
    </Link>
  );
}

function byId(scenarios, id) {
  return scenarios.find((scenario) => scenario.id === id);
}

export default function FtbScenariosPage() {
  const { meta, scenarios } = listScenarios();
  const accepted = scenarios.filter((scenario) => scenario.state === "accepted").length;
  const checksPassed = scenarios.reduce((sum, scenario) => sum + scenario.checksPassed, 0);
  const checksTotal = scenarios.reduce((sum, scenario) => sum + scenario.checksTotal, 0);
  const blocked = scenarios.filter((scenario) => scenario.state === "blocked").length;
  const notSent = scenarios.filter((scenario) => scenario.state === "not_sent").length;
  const next = scenarios.find((scenario) => !scenario.blocked && scenario.state !== "accepted");
  const first = byId(scenarios, "1");
  const originalB = byId(scenarios, "2");
  const correctionB = byId(scenarios, "2C");
  const originalC = byId(scenarios, "5");
  const correctionC = byId(scenarios, "5C");

  return (
    <div className="page">
      <header className="page-head">
        <p className="eyebrow">FTB</p>
        <h1>FTB test scenarios</h1>
        <p className="intro">
          The five File Exchange test scenarios, in the order FTB tests them. Nothing on these pages
          sends.
        </p>
      </header>

      <div className="row ftb-scenarios-meta">
        <Chip>Generated {meta.generatedAt}</Chip>
        <Chip>CA-TCC {meta.caTcc}</Chip>
        <span className="muted">{meta.caTccSource}</span>
        <Chip tone={meta.ledgerFound ? "safe" : "waiting"}>
          {meta.ledgerFound ? "Ledger found" : "No ledger yet"}
        </Chip>
      </div>

      {meta.ledgerNote ? <p className="callout attention ftb-scenarios-note">{meta.ledgerNote}</p> : null}

      <div className="card ftb-scenarios-stats">
        <div className="ftb-scenarios-stats-row">
          <div>
            <p className="section-label">Accepted</p>
            <p className="mono">{accepted} of {scenarios.length}</p>
          </div>
          <div>
            <p className="section-label">Checks passing</p>
            <p className="mono">
              {checksPassed} of {checksTotal}
            </p>
          </div>
          <div>
            <p className="section-label">Blocked</p>
            <p className="mono">{blocked}</p>
          </div>
          <div>
            <p className="section-label">Not sent</p>
            <p className="mono">{notSent}</p>
          </div>
        </div>
      </div>

      {next ? (
        <p className="callout ftb-scenarios-next">
          Scenario {next.id}. {next.nextAction}
        </p>
      ) : (
        <p className="callout safe ftb-scenarios-next">
          All five scenarios are accepted. Record each ReceiptId for the portal evaluation form.
        </p>
      )}

      <div className="ftb-scenarios-flow" aria-label="Order of the five scenarios">
        <ScenarioCard scenario={first} />
        <div className="ftb-scenarios-pair">
          <ScenarioCard scenario={originalB} />
          <span className="mono muted ftb-scenarios-then" aria-hidden="true">
            then
          </span>
          <ScenarioCard scenario={correctionB} />
        </div>
        <div className="ftb-scenarios-pair">
          <ScenarioCard scenario={originalC} />
          <span className="mono muted ftb-scenarios-then" aria-hidden="true">
            then
          </span>
          <ScenarioCard scenario={correctionC} />
        </div>
      </div>
    </div>
  );
}
