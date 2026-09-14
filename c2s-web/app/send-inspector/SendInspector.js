"use client";

import { useMemo, useState } from "react";
import { SAMPLE_SENDS, getSend } from "./fixtures";
import { prettyXml, redactCredentials } from "./prettyXml";

const styles = {
  page: {
    maxWidth: 1280,
    margin: "0 auto",
    padding: "28px 20px 64px",
    fontFamily:
      '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif',
  },
  banner: {
    background: "#eef4f8",
    border: "1px solid #d8dee8",
    borderRadius: 10,
    padding: "12px 16px",
    fontSize: 14,
    color: "#274869",
    marginBottom: 20,
  },
  header: { margin: "0 0 6px", fontSize: 28, fontWeight: 600, letterSpacing: "-0.02em" },
  lead: { margin: "0 0 18px", color: "#5b677a", maxWidth: 720 },
  layout: {
    display: "grid",
    gridTemplateColumns: "minmax(240px, 280px) 1fr",
    gap: 16,
    alignItems: "start",
  },
  list: {
    background: "#fff",
    border: "1px solid #d8dee8",
    borderRadius: 10,
    overflow: "hidden",
  },
  listHead: {
    padding: "10px 14px",
    fontSize: 12,
    letterSpacing: "0.06em",
    textTransform: "uppercase",
    color: "#5b677a",
    borderBottom: "1px solid #d8dee8",
    background: "#f6f8fb",
  },
  sendBtn: {
    display: "block",
    width: "100%",
    textAlign: "left",
    border: 0,
    borderBottom: "1px solid #eef1f5",
    background: "transparent",
    padding: "12px 14px",
    cursor: "pointer",
    font: "inherit",
  },
  sendId: { fontFamily: "ui-monospace, SFMono-Regular, Menlo, monospace", fontSize: 12 },
  meta: { fontSize: 12, color: "#5b677a", marginTop: 4 },
  detail: {
    background: "#fff",
    border: "1px solid #d8dee8",
    borderRadius: 10,
    minWidth: 0,
  },
  detailHead: {
    display: "flex",
    justifyContent: "space-between",
    gap: 12,
    alignItems: "baseline",
    padding: "14px 16px",
    borderBottom: "1px solid #d8dee8",
  },
  panes: {
    display: "grid",
    gridTemplateColumns: "1fr 1fr",
    gap: 0,
    minHeight: 420,
  },
  pane: { padding: 16, minWidth: 0, borderRight: "1px solid #eef1f5" },
  paneTitle: { margin: "0 0 8px", fontSize: 13, fontWeight: 650, letterSpacing: "0.01em" },
  pre: {
    margin: 0,
    padding: 12,
    background: "#0f1724",
    color: "#e8eef7",
    borderRadius: 8,
    fontSize: 12,
    lineHeight: 1.45,
    overflow: "auto",
    maxHeight: 520,
    whiteSpace: "pre",
  },
  mimeLine: {
    fontFamily: "ui-monospace, SFMono-Regular, Menlo, monospace",
    fontSize: 12,
    background: "#f6f8fb",
    border: "1px solid #e6ebf2",
    borderRadius: 8,
    padding: "8px 10px",
    marginBottom: 10,
  },
  missing: {
    background: "#fff8e8",
    border: "1px solid #f0d9a0",
    color: "#9a6700",
    borderRadius: 8,
    padding: "10px 12px",
    fontSize: 13,
  },
  toggle: {
    border: "1px solid #d8dee8",
    background: "#fff",
    borderRadius: 6,
    padding: "4px 10px",
    font: "inherit",
    fontSize: 13,
    cursor: "pointer",
  },
  empty: { padding: 28, color: "#5b677a" },
  collapsed: { padding: "0 16px 16px" },
  details: { marginTop: 8, fontSize: 14 },
};

export default function SendInspector() {
  const sends = SAMPLE_SENDS;
  const [selectedId, setSelectedId] = useState(sends[0]?.submissionId || "");
  const [packed, setPacked] = useState(false);
  const selected = useMemo(() => getSend(selectedId), [selectedId]);

  function xmlForDisplay(xml) {
    const redacted = redactCredentials(xml);
    return packed ? redacted : prettyXml(redacted);
  }

  return (
    <div style={styles.page}>
      <p style={styles.banner}>
        Sample payloads only — not live IRS. MIME SendSubmissions (not MTOM).
        SAML / UsernameToken are not shown. The ZIP attachment is described, not replayed.
      </p>
      <h1 style={styles.header}>MeF send inspector</h1>
      <p style={styles.lead}>
        Pick a send, then read the packed Return XML and the SendSubmissions MIME request
        in a readable layout. This is not a raw <code>a2a_sdk.log</code> dump.
      </p>

      <div style={styles.layout} className="inspector-layout">
        <aside style={styles.list} aria-label="Captured sends">
          <div style={styles.listHead}>Last sample sends</div>
          {sends.length === 0 ? (
            <p style={styles.empty}>
              Nothing captured on this host yet. File through NewSend, then refresh.
              This is the last 20 wire snapshots on this process, not an IRS archive.
            </p>
          ) : (
            sends.map((send) => {
              const active = send.submissionId === selectedId;
              return (
                <button
                  key={send.submissionId}
                  type="button"
                  onClick={() => setSelectedId(send.submissionId)}
                  style={{
                    ...styles.sendBtn,
                    background: active ? "#eef4f8" : "transparent",
                  }}
                  aria-current={active ? "true" : undefined}
                >
                  <div style={styles.sendId}>{send.submissionId}</div>
                  <div style={styles.meta}>
                    {send.formCode} {send.periodLabel} · {send.einMasked} ·{" "}
                    {send.soapRequestCaptured ? "SOAP captured" : "SOAP missing"}
                  </div>
                </button>
              );
            })
          )}
        </aside>

        <article style={styles.detail}>
          {!selected ? (
            <p style={styles.empty}>Select a send from the list.</p>
          ) : (
            <>
              <div style={styles.detailHead}>
                <div>
                  <div style={styles.sendId}>{selected.submissionId}</div>
                  <div style={styles.meta}>
                    {selected.environment} · {selected.capturedAt}
                  </div>
                </div>
                <button type="button" style={styles.toggle} onClick={() => setPacked((v) => !v)}>
                  {packed ? "Show pretty XML" : "Show as packed"}
                </button>
              </div>
              <div style={styles.panes} className="inspector-panes">
                <section style={styles.pane} aria-label="Return XML">
                  <h2 style={styles.paneTitle}>Return.xml</h2>
                  <pre style={styles.pre}>{xmlForDisplay(selected.returnXml)}</pre>
                </section>
                <section style={{ ...styles.pane, borderRight: 0 }} aria-label="MIME and SOAP">
                  <h2 style={styles.paneTitle}>SendSubmissions MIME</h2>
                  {selected.mime.contentType ? (
                    <div style={styles.mimeLine}>Content-Type: {selected.mime.contentType}</div>
                  ) : null}
                  {selected.mime.attachments.map((part) => (
                    <div key={part.contentId} style={styles.mimeLine}>
                      {part.contentType} omitted, {part.byteLength} bytes, sha256={part.sha256}
                    </div>
                  ))}
                  {selected.mime.soapPart ? (
                    <pre style={styles.pre}>{xmlForDisplay(selected.mime.soapPart)}</pre>
                  ) : (
                    <p style={styles.missing}>{selected.mime.missingReason}</p>
                  )}
                </section>
              </div>
              <div style={styles.collapsed}>
                <details style={styles.details}>
                  <summary>SOAP response</summary>
                  {selected.soapResponse ? (
                    <pre style={{ ...styles.pre, marginTop: 8, maxHeight: 240 }}>
                      {xmlForDisplay(selected.soapResponse)}
                    </pre>
                  ) : (
                    <p style={{ ...styles.missing, marginTop: 8 }}>No SOAP response on this sample.</p>
                  )}
                </details>
                <details style={styles.details}>
                  <summary>manifest.xml (as packed)</summary>
                  <pre style={{ ...styles.pre, marginTop: 8, maxHeight: 240 }}>
                    {xmlForDisplay(selected.manifestXml)}
                  </pre>
                </details>
              </div>
            </>
          )}
        </article>
      </div>
      <style>{`
        @media (max-width: 900px) {
          .inspector-layout { grid-template-columns: 1fr !important; }
          .inspector-panes { grid-template-columns: 1fr !important; }
        }
      `}</style>
    </div>
  );
}
