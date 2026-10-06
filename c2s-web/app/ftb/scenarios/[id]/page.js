import { Suspense } from "react";
import Link from "next/link";
import { notFound } from "next/navigation";
import { loadScenario } from "../../lib/ftbCatalog";
import { Badge, Chip } from "../../../design-system/Badge";
import { formLabel, stateLabel, stateTone } from "../../lib/ftbStates";
import FtbScenarioTabs from "./FtbScenarioTabs";
import "./ftb-scenario.css";

export const dynamic = "force-dynamic";

const ATTENTION_STATES = new Set(["rejected", "accepted_with_errors", "needs_recovery"]);

function ledgerLine(ledger) {
  const parts = [`State ${ledger.state}`];
  if (ledger.receiptId) parts.push(`ReceiptId ${ledger.receiptId}`);
  if (ledger.utid) parts.push(`UTID ${ledger.utid}`);
  if (ledger.updatedAt) parts.push(`updated ${ledger.updatedAt}`);
  return `${parts.join(". ")}.`;
}

export default function FtbScenarioPage({ params }) {
  const loaded = loadScenario(params.id);
  if (!loaded) notFound();
  const { scenario, meta } = loaded;
  const failed = scenario.checks.some((check) => !check.ok);
  const attention = ATTENTION_STATES.has(scenario.state) || failed;
  const waiting = scenario.state === "blocked" || scenario.blocked;
  const calloutClass = `callout${attention ? " attention" : waiting ? " waiting" : ""}`;

  return (
    <div className="page">
      <p className="crumbs">
        <Link href="/ftb/scenarios">FTB test scenarios</Link>
        {" / "}
        Scenario {scenario.id}
      </p>
      <header className="page-head">
        <p className="eyebrow">FTB</p>
        <h1>Scenario {scenario.id}</h1>
        <p className="intro">{scenario.purpose}</p>
      </header>
      <section className="card ftb-scenario-summary" aria-label="Scenario summary">
        <div className="row">
          <Badge tone={stateTone(scenario.state)}>{stateLabel(scenario.state)}</Badge>
          <Chip>{formLabel(scenario.formKind)}</Chip>
          <Chip>{scenario.recordIds.length} records</Chip>
          {scenario.correctionOf ? <Chip>Corrects {scenario.correctionOf}</Chip> : null}
        </div>
        <p className={calloutClass}>{scenario.nextAction}</p>
        {meta.ledgerNote ? <p className="callout attention">{meta.ledgerNote}</p> : null}
        {scenario.ledger ? (
          <p className="muted">{ledgerLine(scenario.ledger)}</p>
        ) : (
          <p className="muted">No ledger row yet.</p>
        )}
      </section>
      <Suspense fallback={<p className="muted ftb-scenario-loading">Loading…</p>}>
        <FtbScenarioTabs scenario={scenario} meta={meta} />
      </Suspense>
    </div>
  );
}
