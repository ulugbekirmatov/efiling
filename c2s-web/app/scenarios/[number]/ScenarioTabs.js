"use client";

import { useState } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { prettyXml } from "../../send-inspector/prettyXml";

const TABS = [
  { id: "overview", label: "Overview" },
  { id: "lineMapping", label: "Line mapping" },
  { id: "clientBody", label: "Client body" },
  { id: "composedReturn", label: "Composed Return" },
  { id: "manifest", label: "Manifest" },
  { id: "pdf", label: "Source PDF" },
];

const XML_TABS = new Set(["clientBody", "composedReturn", "manifest"]);

const styles = {
  tabBar: { display: "flex", flexWrap: "wrap", gap: 6, marginBottom: 14 },
  tab: {
    border: "1px solid #d8dee8",
    background: "#fff",
    borderRadius: 6,
    padding: "4px 10px",
    font: "inherit",
    fontSize: 13,
    cursor: "pointer",
  },
  panel: {
    background: "#fff",
    border: "1px solid #d8dee8",
    borderRadius: 10,
    padding: 16,
    minWidth: 0,
  },
  summary: { margin: "0 0 16px", fontSize: 15 },
  heading: {
    margin: "0 0 10px",
    fontSize: 12,
    fontWeight: 650,
    letterSpacing: "0.06em",
    textTransform: "uppercase",
    color: "#5b677a",
  },
  list: { margin: "0 0 18px", paddingLeft: 18, fontSize: 14 },
  tableWrap: { overflow: "auto", maxWidth: "100%", marginBottom: 18 },
  table: {
    width: "100%",
    borderCollapse: "collapse",
    fontSize: 13,
  },
  th: {
    textAlign: "left",
    borderBottom: "1px solid #d8dee8",
    padding: "6px 8px",
    color: "#5b677a",
    fontWeight: 650,
  },
  td: {
    borderBottom: "1px solid #eef1f5",
    padding: "6px 8px",
    verticalAlign: "top",
    wordBreak: "break-word",
  },
  value: {
    textAlign: "right",
    fontVariantNumeric: "tabular-nums",
    fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace',
    whiteSpace: "nowrap",
  },
  mono: {
    fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace',
    fontSize: 12,
    wordBreak: "break-word",
  },
  callout: {
    background: "#eef4f8",
    border: "1px solid #d8dee8",
    borderLeft: "4px solid #274869",
    borderRadius: 8,
    padding: "10px 12px",
    fontSize: 14,
    marginBottom: 8,
  },
  missing: {
    background: "#fff8e8",
    border: "1px solid #f0d9a0",
    color: "#9a6700",
    borderRadius: 8,
    padding: "10px 12px",
    fontSize: 13,
    margin: 0,
  },
  notice: {
    background: "#fff8e8",
    border: "1px solid #f0d9a0",
    color: "#9a6700",
    borderRadius: 8,
    padding: "10px 12px",
    fontSize: 13,
    margin: "0 0 12px",
  },
  producedBy: { margin: "0 0 8px", fontSize: 13, color: "#5b677a" },
  description: { margin: "0 0 12px", fontSize: 14 },
  actions: { display: "flex", flexWrap: "wrap", gap: 8, justifyContent: "flex-end", marginBottom: 8 },
  toggle: {
    border: "1px solid #d8dee8",
    background: "#fff",
    borderRadius: 6,
    padding: "4px 10px",
    font: "inherit",
    fontSize: 13,
    cursor: "pointer",
    color: "inherit",
    textDecoration: "none",
    display: "inline-block",
  },
  pre: {
    margin: 0,
    padding: 12,
    background: "#0f1724",
    color: "#e8eef7",
    borderRadius: 8,
    fontSize: 12,
    lineHeight: 1.45,
    overflow: "auto",
    maxHeight: "70vh",
    whiteSpace: "pre",
  },
  groupTitle: { margin: "0 0 8px", fontSize: 15, fontWeight: 600 },
  iframe: {
    width: "100%",
    height: "80vh",
    border: "1px solid #d8dee8",
    borderRadius: 8,
    background: "#fff",
  },
};

function formatById(formats, id) {
  return formats.find((format) => format.id === id) || null;
}

export default function ScenarioTabs({ number, scenario, formats, documentGroups, hasPdf }) {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const requested = searchParams.get("tab");
  const active = TABS.some((tab) => tab.id === requested) ? requested : "overview";
  const [copied, setCopied] = useState(false);

  function selectTab(id) {
    const params = new URLSearchParams(searchParams.toString());
    params.set("tab", id);
    router.replace(`${pathname}?${params.toString()}`, { scroll: false });
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
        <p style={styles.summary}>{scenario.summary}</p>
        <h2 style={styles.heading}>What this scenario exercises</h2>
        <ul style={styles.list}>
          {scenario.tests.map((item) => (
            <li key={item}>{item}</li>
          ))}
        </ul>
        <h2 style={styles.heading}>Key figures</h2>
        <div style={styles.tableWrap}>
          <table style={styles.table}>
            <thead>
              <tr>
                <th style={styles.th}>Label</th>
                <th style={styles.th}>Line</th>
                <th style={{ ...styles.th, textAlign: "right" }}>Value</th>
              </tr>
            </thead>
            <tbody>
              {scenario.keyFigures.map((row) => (
                <tr key={`${row.line}-${row.label}`}>
                  <td style={styles.td}>{row.label}</td>
                  <td style={styles.td}>{row.line}</td>
                  <td style={{ ...styles.td, ...styles.value }}>{row.value}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <h2 style={styles.heading}>Notes</h2>
        {scenario.notes.map((note) => (
          <p key={note} style={styles.callout}>
            {note}
          </p>
        ))}
      </>
    );
  } else if (active === "lineMapping") {
    body =
      documentGroups.length === 0 ? (
        <p style={styles.missing}>Not prepared for this scenario</p>
      ) : (
        documentGroups.map((group) => (
          <section key={group.document} style={{ marginBottom: 22 }}>
            <h2 style={styles.groupTitle}>{group.document}</h2>
            <div style={styles.tableWrap}>
              <table style={styles.table}>
                <thead>
                  <tr>
                    <th style={styles.th}>Form line</th>
                    <th style={styles.th}>Element path</th>
                    <th style={{ ...styles.th, textAlign: "right" }}>Value</th>
                    <th style={styles.th}>PDF page</th>
                  </tr>
                </thead>
                <tbody>
                  {group.rows.map((row, index) => (
                    <tr key={`${row.elementPath}-${index}`}>
                      <td style={styles.td}>{row.formLine}</td>
                      <td style={{ ...styles.td, ...styles.mono }}>{row.elementPath}</td>
                      <td style={{ ...styles.td, ...styles.value }}>{row.value}</td>
                      <td style={styles.td}>{row.pdfPage}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>
        ))
      );
  } else if (XML_TABS.has(active)) {
    const format = formatById(formats, active);
    if (!format || format.content == null) {
      body = <p style={styles.missing}>Not prepared for this scenario</p>;
    } else {
      const pretty = prettyXml(format.content);
      body = (
        <>
          <p style={styles.description}>{format.description}</p>
          <p style={styles.producedBy}>{format.producedBy}</p>
          {active === "composedReturn" ? (
            <p style={styles.notice}>
              Originator values in this snapshot are schema-valid stand-ins, not Pyramos
              enrollment values.
            </p>
          ) : null}
          <div style={styles.actions}>
            <button type="button" style={styles.toggle} onClick={() => copyText(pretty)}>
              {copied ? "Copied" : "Copy"}
            </button>
            <a href={`/scenarios/${number}/files/${active}?download=1`} style={styles.toggle}>
              Download
            </a>
          </div>
          <pre style={styles.pre}>{pretty}</pre>
        </>
      );
    }
  } else if (active === "pdf") {
    body = hasPdf ? (
      <>
        <div style={{ ...styles.actions, justifyContent: "flex-start" }}>
          <a href={`/scenarios/${number}/files/pdf`} target="_blank" rel="noreferrer" style={styles.toggle}>
            Open in new tab
          </a>
        </div>
        <iframe title="Source PDF" src={`/scenarios/${number}/files/pdf`} style={styles.iframe} />
      </>
    ) : (
      <p style={styles.missing}>Not prepared for this scenario</p>
    );
  }

  return (
    <div>
      <div style={styles.tabBar} role="tablist">
        {TABS.map((tab) => {
          const selected = tab.id === active;
          return (
            <button
              key={tab.id}
              type="button"
              role="tab"
              aria-selected={selected}
              onClick={() => selectTab(tab.id)}
              style={{
                ...styles.tab,
                background: selected ? "#eef4f8" : "#fff",
                borderColor: selected ? "#274869" : "#d8dee8",
                color: selected ? "#274869" : "inherit",
              }}
            >
              {tab.label}
            </button>
          );
        })}
      </div>
      <div style={styles.panel}>{body}</div>
    </div>
  );
}
