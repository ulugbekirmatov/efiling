import Link from "next/link";
import { loadRegistry, loadScenario } from "./scenarioData";
import { Chip } from "../design-system/Badge";
import { StatusBadge, SourceBadge, formatEin } from "./ui";

export const dynamic = "force-dynamic";

const styles = {
  page: {
    maxWidth: 1280,
    margin: "0 auto",
    padding: "28px 20px 64px",
  },
  header: {
    margin: "0 0 6px",
    fontSize: 28,
    fontWeight: 600,
    letterSpacing: "-0.02em",
  },
  lead: { margin: "0 0 22px", color: "#5b677a", maxWidth: 720 },
  grid: {
    display: "grid",
    gridTemplateColumns: "repeat(auto-fill, minmax(280px, 1fr))",
    gap: 16,
    marginBottom: 36,
  },
  card: {
    background: "#fff",
    border: "1px solid #d8dee8",
    borderRadius: 10,
    padding: 16,
    textDecoration: "none",
    color: "inherit",
    display: "block",
    minWidth: 0,
  },
  kicker: {
    margin: 0,
    fontSize: 12,
    fontWeight: 650,
    letterSpacing: "0.06em",
    textTransform: "uppercase",
    color: "#5b677a",
  },
  title: { margin: "6px 0 10px", fontSize: 18, fontWeight: 600 },
  employer: { margin: "0 0 4px", fontSize: 14 },
  ein: {
    margin: "0 0 8px",
    fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace',
    fontSize: 13,
  },
  meta: { margin: "0 0 10px", fontSize: 13, color: "#5b677a" },
  chips: { display: "flex", flexWrap: "wrap", gap: 6, margin: "8px 0 0" },
  section: { marginTop: 8, marginBottom: 32 },
  sectionTitle: {
    margin: "0 0 12px",
    fontSize: 20,
    fontWeight: 600,
    letterSpacing: "-0.02em",
  },
  pipeline: {
    margin: "0 0 16px",
    fontSize: 13,
    color: "#5b677a",
    fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace',
  },
  formatCard: {
    background: "#fff",
    border: "1px solid #d8dee8",
    borderRadius: 10,
    padding: 16,
    minWidth: 0,
  },
  formatTitle: { margin: "0 0 6px", fontSize: 16, fontWeight: 600 },
  producedBy: { margin: "0 0 8px", fontSize: 13, color: "#5b677a" },
  description: { margin: 0, fontSize: 14 },
  tableWrap: { overflow: "auto", maxWidth: "100%" },
  table: {
    width: "100%",
    borderCollapse: "collapse",
    fontSize: 13,
    background: "#fff",
    border: "1px solid #d8dee8",
    borderRadius: 10,
  },
  th: {
    textAlign: "left",
    borderBottom: "1px solid #d8dee8",
    padding: "8px 10px",
    color: "#5b677a",
    fontWeight: 650,
    background: "#f6f8fb",
  },
  td: {
    borderBottom: "1px solid #eef1f5",
    padding: "8px 10px",
    verticalAlign: "top",
    wordBreak: "break-word",
  },
  mono: {
    fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace',
    fontSize: 12,
  },
  legacyCard: {
    background: "#fff",
    border: "1px solid #d8dee8",
    borderRadius: 10,
    padding: 16,
    minWidth: 0,
  },
  legacyTitle: { margin: "0 0 8px", fontSize: 16, fontWeight: 600 },
  path: {
    margin: "8px 0 0",
    fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace',
    fontSize: 12,
    color: "#5b677a",
    wordBreak: "break-word",
  },
};

function preparedKinds(loaded) {
  const kinds = [];
  for (const format of loaded.formats) {
    if (format.content != null) kinds.push(format);
  }
  if (loaded.hasPdf) kinds.push({ id: "pdf", title: "Source PDF" });
  return kinds;
}

export default function ScenariosIndexPage() {
  const registry = loadRegistry();
  const cards = registry.scenarios.map((scenario) => ({
    scenario,
    loaded: loadScenario(scenario.number),
  }));
  const composed = registry.formats.find((format) => format.headerFields);
  const headerFields = (composed && composed.headerFields) || [];

  return (
    <div style={styles.page}>
      <h1 style={styles.header}>ATS test scenarios</h1>
      <p style={styles.lead}>
        TY{registry.taxYear} Form 94x ATS scenarios Pyramos files as a Reporting Agent.
        Return version {registry.returnVersion}.
      </p>

      <div style={styles.grid}>
        {cards.map(({ scenario, loaded }) => {
          const kinds = loaded ? preparedKinds(loaded) : [];
          return (
            <Link key={scenario.number} href={`/scenarios/${scenario.number}`} style={styles.card}>
              <p style={styles.kicker}>Scenario {scenario.number}</p>
              <h2 style={styles.title}>{scenario.title}</h2>
              <p style={styles.employer}>{scenario.employer.name}</p>
              <p style={styles.ein}>{formatEin(scenario.employer.ein)}</p>
              <p style={styles.meta}>
                {scenario.returnTypeCd} · {scenario.quarter}
              </p>
              <div style={styles.chips}>
                {scenario.documents.map((doc) => (
                  <Chip key={doc}>
                    {doc}
                  </Chip>
                ))}
              </div>
              <div style={{ marginTop: 10 }}>
                <StatusBadge status={scenario.status} />
              </div>
              <div style={styles.chips}>
                {registry.formats.map((format) => {
                  const ready = kinds.some((kind) => kind.id === format.id);
                  return (
                    <Chip key={format.id} tone={ready ? undefined : "waiting"}>
                      {format.title}
                    </Chip>
                  );
                })}
                <Chip tone={loaded && loaded.hasPdf ? undefined : "waiting"}>Source PDF</Chip>
              </div>
            </Link>
          );
        })}
      </div>

      <section style={styles.section} aria-labelledby="formats-heading">
        <h2 id="formats-heading" style={styles.sectionTitle}>
          Formats
        </h2>
        <p style={styles.pipeline}>
          client body → composed Return + manifest → submission archive
        </p>
        <div style={styles.grid}>
          {registry.formats.map((format) => (
            <article key={format.id} style={styles.formatCard}>
              <h3 style={styles.formatTitle}>{format.title}</h3>
              <p style={styles.producedBy}>{format.producedBy}</p>
              <p style={styles.description}>{format.description}</p>
            </article>
          ))}
        </div>
      </section>

      <section style={styles.section} aria-labelledby="header-fields-heading">
        <h2 id="header-fields-heading" style={styles.sectionTitle}>
          ReturnHeader fields
        </h2>
        <div style={styles.tableWrap}>
          <table style={{ ...styles.table, minWidth: 560 }}>
            <thead>
              <tr>
                <th style={styles.th}>Element</th>
                <th style={styles.th}>Source</th>
                <th style={styles.th}>From</th>
              </tr>
            </thead>
            <tbody>
              {headerFields.map((field) => (
                <tr key={field.element}>
                  <td style={{ ...styles.td, ...styles.mono }}>{field.element}</td>
                  <td style={styles.td}>
                    <SourceBadge source={field.source} />
                  </td>
                  <td style={styles.td}>{field.from}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      <section style={styles.section} aria-labelledby="legacy-heading">
        <h2 id="legacy-heading" style={styles.sectionTitle}>
          Legacy and source-only
        </h2>
        <div style={styles.grid}>
          {registry.legacy.map((item) => (
            <article key={item.id} style={styles.legacyCard}>
              <h3 style={styles.legacyTitle}>
                <Link href={`/scenarios/legacy/${item.id}`}>{item.title}</Link>
              </h3>
              <p style={styles.description}>{item.description}</p>
              <p style={styles.path}>{item.path}</p>
            </article>
          ))}
        </div>
      </section>
    </div>
  );
}
