"use client";

import { Fragment, useRef, useState } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { prettyXml } from "../../send-inspector/prettyXml";
import XmlCode from "../../design-system/XmlCode";

const TABS = [
  { id: "overview", label: "Overview" },
  { id: "lineMapping", label: "Line mapping" },
  { id: "clientBody", label: "Client body" },
  { id: "composedReturn", label: "Composed Return" },
  { id: "manifest", label: "Manifest" },
  { id: "pdf", label: "Source PDF" },
];

const XML_TABS = new Set(["clientBody", "composedReturn", "manifest"]);

function formatById(formats, id) {
  return formats.find((format) => format.id === id) || null;
}

function ElementPathCell({ path }) {
  const segments = String(path).split("/");
  return (
    <td className="scenario-path">
      {segments.map((segment, index) => (
        <Fragment key={index}>
          {index > 0 ? (
            <>
              /
              <wbr />
            </>
          ) : null}
          {segment}
        </Fragment>
      ))}
    </td>
  );
}

export default function ScenarioTabs({ number, scenario, formats, documentGroups, hasPdf }) {
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

  let body = null;
  if (active === "overview") {
    body = (
      <>
        <p className="scenario-summary">{scenario.summary}</p>
        <h2 className="section-label">What this scenario exercises</h2>
        <ul className="scenario-list">
          {scenario.tests.map((item) => (
            <li key={item}>{item}</li>
          ))}
        </ul>
        <h2 className="section-label">Key figures</h2>
        <div className="table-wrap">
          <table className="data">
            <thead>
              <tr>
                <th>Label</th>
                <th>Line</th>
                <th className="num">Value</th>
              </tr>
            </thead>
            <tbody>
              {scenario.keyFigures.map((row, index) => (
                <tr key={`${index}-${row.label}`}>
                  <td>{row.label}</td>
                  <td>{row.line}</td>
                  <td className="num">{row.value}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <h2 className="section-label">Notes</h2>
        {scenario.notes.map((note, index) => (
          <p key={`${index}-${note}`} className="callout">
            {note}
          </p>
        ))}
      </>
    );
  } else if (active === "lineMapping") {
    const format = formatById(formats, "lineMapping");
    body =
      documentGroups.length === 0 ? (
        <p className="callout waiting">Not prepared for this scenario</p>
      ) : (
        <>
          {format ? (
            <>
              <p className="scenario-description">{format.description}</p>
              <p className="scenario-produced muted">{format.producedBy}</p>
            </>
          ) : null}
          <div className="actions end">
            <a href={`/scenarios/${number}/files/lineMapping?download=1`} className="btn-mini">
              Download file
            </a>
          </div>
          {documentGroups.map((group) => (
            <section key={group.document} className="scenario-doc">
              <h2>{group.document}</h2>
              <div className="table-wrap">
                <table className="data wide">
                  <thead>
                    <tr>
                      <th className="nowrap">Form line</th>
                      <th>Element path</th>
                      <th className="num">Value</th>
                      <th className="nowrap">PDF page</th>
                    </tr>
                  </thead>
                  <tbody>
                    {group.rows.map((row, index) => (
                      <tr key={`${row.elementPath}-${index}`}>
                        <td className="nowrap">{row.formLine}</td>
                        <ElementPathCell path={row.elementPath} />
                        <td className="num">{row.value}</td>
                        <td className="nowrap">{row.pdfPage}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </section>
          ))}
        </>
      );
  } else if (XML_TABS.has(active)) {
    const format = formatById(formats, active);
    if (!format || format.content == null) {
      body = <p className="callout waiting">Not prepared for this scenario</p>;
    } else {
      const pretty = prettyXml(format.content);
      body = (
        <>
          <p className="scenario-description">{format.description}</p>
          <p className="scenario-produced muted">{format.producedBy}</p>
          {active === "composedReturn" ? (
            <p className="callout attention">
              Originator values in this snapshot are schema-valid stand-ins, not Pyramos
              enrollment values.
            </p>
          ) : null}
          <div className="actions end">
            <button type="button" className="btn-mini" onClick={() => copyText(pretty)}>
              {copied ? "Copied" : "Copy formatted"}
            </button>
            <a href={`/scenarios/${number}/files/${active}?download=1`} className="btn-mini">
              Download file
            </a>
          </div>
          <XmlCode
            text={pretty}
            className="tall"
            label={TABS.find((tab) => tab.id === active).label}
          />
        </>
      );
    }
  } else if (active === "pdf") {
    body = hasPdf ? (
      <>
        <div className="actions">
          <a href={`/scenarios/${number}/files/pdf`} target="_blank" rel="noreferrer" className="btn-mini">
            Open in new tab
          </a>
        </div>
        <iframe title="Source PDF" src={`/scenarios/${number}/files/pdf`} className="frame" />
      </>
    ) : (
      <p className="callout waiting">Not prepared for this scenario</p>
    );
  }

  return (
    <div>
      <div
        ref={tabListRef}
        className="tabs"
        role="tablist"
        onKeyDown={onTabListKeyDown}
      >
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
        className="card scenario-panel"
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
