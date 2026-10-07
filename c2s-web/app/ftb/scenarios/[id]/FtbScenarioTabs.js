"use client";

import { Fragment, useRef, useState } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { prettyXml } from "../../../send-inspector/prettyXml";
import XmlCode from "../../../design-system/XmlCode";
import { Badge, Chip } from "../../../design-system/Badge";
import { formLabel } from "../../lib/ftbStates";

const TABS = [
  { id: "overview", label: "Overview" },
  { id: "spec", label: "FTB narrative" },
  { id: "send", label: "Our data" },
  { id: "checks", label: "Checks" },
  { id: "form", label: "Form XML" },
  { id: "manifest", label: "Manifest" },
  { id: "answer", label: "Answer key" },
  { id: "pdf", label: "Spec PDF" },
];

const XML_TABS = new Set(["form", "manifest", "answer"]);
const MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];

function xmlForTab(scenario, tab) {
  if (tab === "form") return scenario.preview.formXml;
  if (tab === "manifest") return scenario.preview.manifestXml;
  return scenario.preview.answerKeyXml;
}

function KvPairs({ rows }) {
  if (!rows || rows.length === 0) return null;
  return (
    <dl className="kv">
      {rows.map(([label, value], index) => (
        <Fragment key={`${index}-${label}`}>
          <dt>{label}</dt>
          <dd>{value == null || value === "" ? "" : String(value)}</dd>
        </Fragment>
      ))}
    </dl>
  );
}

function MonthGrid({ values, kind, label }) {
  return (
    <div className="table-wrap">
      <div className="ftb-scenario-months" role="group" aria-label={label}>
        {MONTHS.map((month, index) => {
          const value = values[index];
          const on = kind === "codes" ? typeof value === "string" && value !== "" : value === true;
          return (
            <span key={month} className={`ftb-scenario-month${on ? " ftb-scenario-month-on" : ""}`}>
              <b>{month}</b>
              <span>{kind === "codes" ? value || "" : on ? "yes" : ""}</span>
            </span>
          );
        })}
      </div>
    </div>
  );
}

function Coverage({ months }) {
  if (months === "all year") return "All year";
  if (Array.isArray(months) && months.length === 12) {
    return <MonthGrid values={months} kind="flags" label="Coverage by month" />;
  }
  // The holder row of a 1095-B or 1095-C carries no coverage months; covered individuals do.
  return null;
}

function personId(person) {
  return [person.idLabel, person.id].filter(Boolean).join(" ");
}

function ReturnBlock({ record }) {
  return (
    <section className="ftb-scenario-return">
      <div className="ftb-scenario-return-head">
        <h2 className="ftb-scenario-return-title">
          Record {record.recordId} · {record.testScenarioId}
        </h2>
        <Chip>CorrectedInd {record.correctedInd}</Chip>
      </div>
      {record.original ? (
        <>
          <h3 className="section-label">Original submission</h3>
          <KvPairs rows={record.original} />
        </>
      ) : null}
      <KvPairs rows={[...record.fields, ...record.codes]} />
      {record.offerGrid ? (
        <>
          <h3 className="section-label">Offer of coverage</h3>
          <MonthGrid values={record.offerGrid} kind="codes" label="Offer codes by month" />
        </>
      ) : null}
      <div className="table-wrap">
        <table className="data">
          <thead>
            <tr>
              <th>Role</th>
              <th>Name</th>
              <th>ID</th>
              <th>DOB</th>
              <th>Coverage</th>
            </tr>
          </thead>
          <tbody>
            {record.people.map((person, index) => (
              <tr key={`${person.role}-${person.name}-${index}`}>
                <td>{person.role}</td>
                <td>{person.name}</td>
                <td className="mono">{personId(person)}</td>
                <td className="nowrap">{person.dob || ""}</td>
                <td>
                  <Coverage months={person.months} />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}

export default function FtbScenarioTabs({ scenario, meta }) {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const tabListRef = useRef(null);
  const requested = searchParams.get("tab");
  const active = TABS.some((tab) => tab.id === requested) ? requested : "overview";
  const [copied, setCopied] = useState(false);

  function selectTab(id) {
    const params = new URLSearchParams(searchParams.toString());
    params.set("tab", id);
    router.replace(`${pathname}?${params.toString()}`, { scroll: false });
  }

  function onTabListKeyDown(event) {
    if (
      event.key !== "ArrowLeft" &&
      event.key !== "ArrowRight" &&
      event.key !== "Home" &&
      event.key !== "End"
    ) {
      return;
    }
    event.preventDefault();
    const currentIndex = TABS.findIndex((tab) => tab.id === active);
    let nextIndex = currentIndex < 0 ? 0 : currentIndex;
    if (event.key === "Home") nextIndex = 0;
    else if (event.key === "End") nextIndex = TABS.length - 1;
    else if (event.key === "ArrowLeft") {
      nextIndex = (nextIndex - 1 + TABS.length) % TABS.length;
    } else {
      nextIndex = (nextIndex + 1) % TABS.length;
    }
    const nextId = TABS[nextIndex].id;
    selectTab(nextId);
    const button = tabListRef.current && tabListRef.current.querySelector(`#tab-${nextId}`);
    if (button) button.focus();
  }

  async function copyText(text) {
    try {
      await navigator.clipboard.writeText(text);
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    } catch {
      setCopied(false);
    }
  }

  const pdfHref = `/ftb/scenarios/${scenario.id}/spec-pdf`;
  const firstNarrativeLine = scenario.narrative[0] && scenario.narrative[0].line;
  let body = null;

  if (active === "overview") {
    body = (
      <>
        <KvPairs
          rows={[
            ["Record ids", scenario.recordIds.join(", ")],
            ["Form", formLabel(scenario.formKind)],
            ["Correction of", scenario.correctionOf || "None"],
            ["CA-TCC", `${meta.caTcc} from ${meta.caTccSource}`],
            ["Generated at", meta.generatedAt],
          ]}
        />
        {meta.knownDifferences.length > 0 ? (
          <div className="callout">
            <ul className="ftb-scenario-notes">
              {meta.knownDifferences.map((item) => (
                <li key={item}>{item}</li>
              ))}
            </ul>
          </div>
        ) : null}
      </>
    );
  } else if (active === "spec") {
    body =
      scenario.narrative.length === 0 ? (
        <p className="callout waiting">Spec text not found in ftb-fx/spec/text.</p>
      ) : (
        <div className="ftb-scenario-narrative">
          {scenario.narrative.map((line, index) => (
            <div key={`${line.line}-${index}`} className="ftb-scenario-narr-line">
              <span className="muted mono ftb-scenario-narr-gutter">T:{line.line}</span>
              <span>{line.text}</span>
            </div>
          ))}
        </div>
      );
  } else if (active === "send") {
    body = (
      <>
        <KvPairs rows={scenario.summary.transmittal} />
        {scenario.summary.returns.map((record) => (
          <ReturnBlock key={record.testScenarioId} record={record} />
        ))}
      </>
    );
  } else if (active === "checks") {
    body = (
      <>
        {scenario.checks.map((check) => (
          <div key={check.name} className="ftb-scenario-check">
            <div className="row ftb-scenario-check-head">
              <Badge tone={check.ok ? "safe" : "attention"}>{check.ok ? "Pass" : "Fail"}</Badge>
              <span>{check.name}</span>
            </div>
            {check.details.length > 0 ? (
              <ul>
                {check.details.map((detail) => (
                  <li key={detail} className="mono">
                    {detail}
                  </li>
                ))}
              </ul>
            ) : null}
          </div>
        ))}
        <h2 className="section-label">Gotchas</h2>
        <ol className="ftb-scenario-gotchas">
          {scenario.gotchas.map((gotcha) => (
            <li key={gotcha.number} value={gotcha.number}>
              Rule {gotcha.number}. {gotcha.text}
            </li>
          ))}
        </ol>
      </>
    );
  } else if (XML_TABS.has(active)) {
    const xml = xmlForTab(scenario, active);
    const label = TABS.find((tab) => tab.id === active).label;
    if (xml == null) {
      body = (
        <>
          {active === "answer" && scenario.preview.receiptSource ? (
            <p className="muted ftb-scenario-note">ReceiptId from {scenario.preview.receiptSource}</p>
          ) : null}
          <p className="callout waiting">Not produced. See the Checks tab.</p>
        </>
      );
    } else {
      const pretty = prettyXml(xml);
      body = (
        <>
          {active === "answer" && scenario.preview.receiptSource ? (
            <p className="muted ftb-scenario-note">ReceiptId from {scenario.preview.receiptSource}</p>
          ) : null}
          <div className="actions end">
            <button type="button" className="btn-mini" onClick={() => copyText(pretty)}>
              {copied ? "Copied" : "Copy formatted"}
            </button>
          </div>
          <XmlCode text={pretty} className="tall" label={label} />
        </>
      );
    }
  } else if (active === "pdf") {
    body = (
      <>
        {firstNarrativeLine != null ? (
          <p className="muted ftb-scenario-note">
            The narrative for this scenario starts at T:{firstNarrativeLine}, the first line number
            on the FTB narrative tab.
          </p>
        ) : null}
        <div className="actions ftb-scenario-pdf-open">
          <a href={pdfHref} target="_blank" rel="noreferrer" className="btn-mini">
            Open in new tab
          </a>
        </div>
        <iframe title="FTB testing spec PDF" src={pdfHref} className="frame" />
      </>
    );
  }

  return (
    <div>
      <div ref={tabListRef} className="tabs" role="tablist" onKeyDown={onTabListKeyDown}>
        {TABS.map((tab) => {
          const selected = tab.id === active;
          return (
            <button
              key={tab.id}
              id={`tab-${tab.id}`}
              type="button"
              role="tab"
              aria-selected={selected}
              aria-controls={`panel-${tab.id}`}
              tabIndex={selected ? 0 : -1}
              onClick={() => selectTab(tab.id)}
              className="tab"
            >
              {tab.label}
            </button>
          );
        })}
      </div>
      <div
        className="card ftb-scenario-panel"
        role="tabpanel"
        id={`panel-${active}`}
        aria-labelledby={`tab-${active}`}
        tabIndex={0}
      >
        {body}
      </div>
    </div>
  );
}
