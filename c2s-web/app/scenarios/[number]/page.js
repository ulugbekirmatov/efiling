import { Suspense } from "react";
import Link from "next/link";
import { notFound } from "next/navigation";
import { groupByDocument, loadScenario, sendClientId } from "../scenarioData";
import { CHIP, StatusBadge, formatEin } from "../ui";
import ScenarioTabs from "./ScenarioTabs";

export const dynamic = "force-dynamic";

const styles = {
  page: {
    maxWidth: 1280,
    margin: "0 auto",
    padding: "28px 20px 64px",
  },
  crumb: { margin: "0 0 14px", fontSize: 13 },
  header: {
    margin: "0 0 8px",
    fontSize: 28,
    fontWeight: 600,
    letterSpacing: "-0.02em",
  },
  kicker: {
    margin: "0 0 4px",
    fontSize: 12,
    fontWeight: 650,
    letterSpacing: "0.06em",
    textTransform: "uppercase",
    color: "#5b677a",
  },
  block: {
    background: "#fff",
    border: "1px solid #d8dee8",
    borderRadius: 10,
    padding: 16,
    marginBottom: 16,
    minWidth: 0,
  },
  employerName: { margin: "0 0 4px", fontSize: 16, fontWeight: 600 },
  ein: {
    margin: "0 0 4px",
    fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace',
    fontSize: 13,
  },
  address: { margin: "0 0 8px", fontSize: 14, color: "#5b677a" },
  meta: { margin: "0 0 8px", fontSize: 14 },
  chips: { display: "flex", flexWrap: "wrap", gap: 6, margin: "8px 0" },
  note: { margin: "10px 0 0", fontSize: 14, color: "#5b677a" },
  journal: { margin: "10px 0 0", fontSize: 14 },
  mono: {
    fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace',
    fontSize: 13,
  },
  fallback: { padding: "12px 0", color: "#5b677a" },
};

export default function ScenarioDetailPage({ params }) {
  const loaded = loadScenario(params.number);
  if (!loaded) notFound();
  const { scenario, formats, lineMapping, hasPdf } = loaded;

  return (
    <div style={styles.page}>
      <p style={styles.crumb}>
        <Link href="/scenarios">ATS test scenarios</Link>
        {" / "}
        Scenario {scenario.number}
      </p>
      <p style={styles.kicker}>Scenario {scenario.number}</p>
      <h1 style={styles.header}>{scenario.title}</h1>
      <section style={styles.block} aria-label="Employer and status">
        <p style={styles.employerName}>{scenario.employer.name}</p>
        <p style={styles.ein}>
          {formatEin(scenario.employer.ein)}
          {scenario.employer.nameControl ? ` · ${scenario.employer.nameControl}` : ""}
        </p>
        <p style={styles.address}>{scenario.employer.address}</p>
        <p style={styles.meta}>
          {scenario.returnTypeCd} · {scenario.quarter}
        </p>
        <div style={styles.chips}>
          {scenario.documents.map((doc) => (
            <span key={doc} style={CHIP}>
              {doc}
            </span>
          ))}
        </div>
        <StatusBadge status={scenario.status} />
        {scenario.status.note ? <p style={styles.note}>{scenario.status.note}</p> : null}
        <p style={styles.journal}>
          Send journal client id{" "}
          <span style={styles.mono}>{sendClientId(scenario)}</span>
          {" · "}
          <Link href="/send-inspector">Open send inspector</Link>
        </p>
      </section>
      <Suspense fallback={<p style={styles.fallback}>Loading…</p>}>
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
