"use client";

import { useRef, useState } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { prettyXml } from "../../../send-inspector/prettyXml";
import XmlCode from "../../../design-system/XmlCode";

const TABS = [
  { id: "overview", label: "Overview" },
  { id: "form", label: "Form data" },
  { id: "manifest", label: "Manifest" },
  { id: "pdfs", label: "PDFs" },
];

const XML_TABS = new Set(["form", "manifest"]);

function pdfLabel(entryName) {
  return entryName.replace(/\.pdf$/, "");
}

export default function AirScenarioTabs({ summary, formXml, manifestXml }) {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const tabListRef = useRef(null);
  const requested = searchParams.get("tab");
  const active = TABS.some((tab) => tab.id === requested) ? requested : "overview";
  const [selectedPdf, setSelectedPdf] = useState(summary.pdfs[0] || null);

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

  let body = null;
  if (active === "overview") {
    body = (
      <>
        <h2 className="section-label">Facts</h2>
        <ul className="air-scenario-facts">
          <li>Test scenario {summary.testScenarioId}</li>
          <li>Tax year {summary.taxYear}</li>
          <li>{summary.form1095CCount} Form 1095-C</li>
          <li>{summary.otherAleMemberCount} other ALE members</li>
          <li>Offer style: {summary.offerStyle}</li>
          <li>{summary.pdfs.length} IRS PDFs</li>
        </ul>
        <h2 className="section-label">Preview</h2>
        <p className="callout">
          Form data and manifest are composed in memory on each render. The UTID and timestamp
          change when you reload. Compose run saves one fixed copy.
        </p>
        <p className="muted air-scenario-note">Preview UTID {summary.utid}</p>
      </>
    );
  } else if (XML_TABS.has(active)) {
    const label = TABS.find((tab) => tab.id === active).label;
    body = (
      <>
        <p className="muted air-scenario-note">
          Composed in memory per render, so the UTID and timestamp change on reload. SSNs are
          masked to the last four digits.
        </p>
        <XmlCode text={prettyXml(active === "form" ? formXml : manifestXml)} className="tall" label={label} />
      </>
    );
  } else if (active === "pdfs") {
    body =
      summary.pdfs.length === 0 ? (
        <p className="callout waiting">No IRS PDFs found for this scenario.</p>
      ) : (
        <>
          <ul className="air-scenario-pdf-list">
            {summary.pdfs.map((entry) => (
              <li key={entry}>
                <button
                  type="button"
                  className={entry === selectedPdf ? "btn-mini" : "btn-mini air-scenario-pdf-idle"}
                  aria-pressed={entry === selectedPdf}
                  onClick={() => setSelectedPdf(entry)}
                >
                  {pdfLabel(entry)}
                </button>
              </li>
            ))}
          </ul>
          {selectedPdf ? (
            <iframe
              title={pdfLabel(selectedPdf)}
              src={`/air/scenarios/${summary.id}/pdf?entry=${encodeURIComponent(selectedPdf)}`}
              className="frame"
            />
          ) : null}
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
        className="card air-scenario-panel"
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
