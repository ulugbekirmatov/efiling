import Link from "next/link";
import { loadRegistry, loadScenario } from "./scenarioData";
import { Chip } from "../design-system/Badge";
import { StatusBadge, SourceBadge, formatEin } from "./ui";
import "./scenarios.css";

export const dynamic = "force-dynamic";

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
    <div className="page">
      <header className="page-head">
        <p className="eyebrow">Form 94x</p>
        <h1>ATS test scenarios</h1>
        <p className="intro">
          TY{registry.taxYear} Form 94x ATS scenarios Pyramos files as a Reporting Agent.
          Return version {registry.returnVersion}.
        </p>
      </header>

      <div className="scenario-grid">
        {cards.map(({ scenario, loaded }) => {
          const kinds = loaded ? preparedKinds(loaded) : [];
          return (
            <Link
              key={scenario.number}
              href={`/scenarios/${scenario.number}`}
              className="card lift scenario-card"
            >
              <p className="section-label">Scenario {scenario.number}</p>
              <h2 className="card-title">{scenario.title}</h2>
              <p>{scenario.employer.name}</p>
              <p className="mono">{formatEin(scenario.employer.ein)}</p>
              <p className="muted">
                {scenario.returnTypeCd} · {scenario.quarter}
              </p>
              <div className="row">
                {scenario.documents.map((doc) => (
                  <Chip key={doc}>
                    {doc}
                  </Chip>
                ))}
              </div>
              <div className="row scenario-status">
                <StatusBadge status={scenario.status} />
              </div>
              <div className="row scenario-chips">
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

      <section className="scenario-section" aria-labelledby="formats-heading">
        <h2 id="formats-heading">
          Formats
        </h2>
        <p className="mono muted scenario-pipeline">
          client body → composed Return + manifest → submission archive
        </p>
        <div className="scenario-grid">
          {registry.formats.map((format) => (
            <article key={format.id} className="card">
              <h3 className="card-title">{format.title}</h3>
              <p className="muted">{format.producedBy}</p>
              <p>{format.description}</p>
            </article>
          ))}
        </div>
      </section>

      <section className="scenario-section" aria-labelledby="header-fields-heading">
        <h2 id="header-fields-heading">
          ReturnHeader fields
        </h2>
        <div className="table-wrap">
          <table className="data header-fields">
            <thead>
              <tr>
                <th>Element</th>
                <th>Source</th>
                <th>From</th>
              </tr>
            </thead>
            <tbody>
              {headerFields.map((field) => (
                <tr key={field.element}>
                  <td className="mono">{field.element}</td>
                  <td>
                    <SourceBadge source={field.source} />
                  </td>
                  <td>{field.from}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      <section className="scenario-section" aria-labelledby="legacy-heading">
        <h2 id="legacy-heading">
          Legacy and source-only
        </h2>
        <div className="scenario-grid">
          {registry.legacy.map((item) => (
            <article key={item.id} className="card">
              <h3 className="card-title">
                <Link href={`/scenarios/legacy/${item.id}`}>{item.title}</Link>
              </h3>
              <p>{item.description}</p>
              <p className="file-path">{item.path}</p>
            </article>
          ))}
        </div>
      </section>
    </div>
  );
}
