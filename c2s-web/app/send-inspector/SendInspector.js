"use client";

import { useEffect, useRef, useState } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import {
  fetchAck,
  getSend,
  getSession,
  isUnreachableError,
  listSends,
  login,
  logout,
} from "./api";
import { prettyXml, redactCredentials } from "./prettyXml";

const TABS = [
  { id: "returnXml", label: "Return XML", docKey: "returnXml" },
  { id: "manifest", label: "Manifest", docKey: "manifestXml" },
  { id: "soapRequest", label: "SOAP request", docKey: "soapRequest" },
  { id: "soapResponse", label: "SOAP response", docKey: "soapResponse" },
  { id: "mime", label: "MIME attachments" },
];

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
    gridTemplateColumns: "minmax(240px, 300px) 1fr",
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
  badges: { display: "flex", gap: 6, marginTop: 6, flexWrap: "wrap" },
  detail: {
    background: "#fff",
    border: "1px solid #d8dee8",
    borderRadius: 10,
    minWidth: 0,
  },
  card: {
    padding: "14px 16px",
    borderBottom: "1px solid #d8dee8",
  },
  ackCard: {
    padding: "16px",
    borderBottom: "1px solid #d8dee8",
    background: "#f6f8fb",
  },
  cardTitle: {
    margin: "0 0 10px",
    fontSize: 12,
    fontWeight: 650,
    letterSpacing: "0.06em",
    textTransform: "uppercase",
    color: "#5b677a",
  },
  dl: {
    display: "grid",
    gridTemplateColumns: "180px 1fr",
    gap: "6px 12px",
    fontSize: 13,
    margin: 0,
  },
  dt: { color: "#5b677a" },
  dd: { margin: 0, minWidth: 0, wordBreak: "break-word" },
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
  missing: {
    background: "#fff8e8",
    border: "1px solid #f0d9a0",
    color: "#9a6700",
    borderRadius: 8,
    padding: "10px 12px",
    fontSize: 13,
  },
  fault: {
    background: "#fdecec",
    border: "1px solid #f2b8b5",
    color: "#b42318",
    borderRadius: 8,
    padding: "10px 12px",
    fontSize: 13,
    marginTop: 10,
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
  primary: {
    border: "1px solid #274869",
    background: "#274869",
    color: "#fff",
    borderRadius: 6,
    padding: "8px 14px",
    font: "inherit",
    fontSize: 14,
    cursor: "pointer",
  },
  empty: { padding: 28, color: "#5b677a" },
  tabBar: { display: "flex", flexWrap: "wrap", gap: 6, marginBottom: 12 },
  tab: {
    border: "1px solid #d8dee8",
    background: "#fff",
    borderRadius: 6,
    padding: "4px 10px",
    font: "inherit",
    fontSize: 13,
    cursor: "pointer",
  },
  sentHead: {
    display: "flex",
    justifyContent: "space-between",
    gap: 12,
    alignItems: "center",
    marginBottom: 10,
  },
  actions: { display: "flex", flexWrap: "wrap", gap: 8, alignItems: "center", marginTop: 12 },
  ackHero: {
    display: "inline-block",
    fontSize: 22,
    fontWeight: 650,
    letterSpacing: "0.02em",
    borderRadius: 8,
    padding: "8px 16px",
    marginBottom: 12,
  },
  table: {
    width: "100%",
    borderCollapse: "collapse",
    fontSize: 12,
    marginTop: 10,
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
    wordBreak: "break-all",
    fontFamily: "ui-monospace, SFMono-Regular, Menlo, monospace",
  },
  mimeLine: {
    fontFamily: "ui-monospace, SFMono-Regular, Menlo, monospace",
    fontSize: 12,
    background: "#fff",
    border: "1px solid #e6ebf2",
    borderRadius: 8,
    padding: "8px 10px",
    marginBottom: 10,
  },
  session: { fontSize: 13, color: "#274869" },
};

function formatLocalTime(iso) {
  if (!iso) return "—";
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return String(iso);
  return date.toLocaleString();
}

function badgeStyle(kind) {
  const base = {
    display: "inline-block",
    fontSize: 11,
    fontWeight: 650,
    letterSpacing: "0.04em",
    textTransform: "uppercase",
    borderRadius: 999,
    padding: "2px 8px",
    lineHeight: 1.4,
  };
  if (/^accept/i.test(kind || "")) return { ...base, background: "#e6f6ec", color: "#1f7a4d" };
  if (/^reject/i.test(kind || "")) return { ...base, background: "#fdecec", color: "#b42318" };
  if (kind === "state") return { ...base, background: "#eef4f8", color: "#274869" };
  return { ...base, background: "#eef1f5", color: "#5b677a" };
}

function ackHeroStyle(ackType) {
  if (/^accept/i.test(ackType || "")) return { ...styles.ackHero, background: "#e6f6ec", color: "#1f7a4d" };
  if (/^reject/i.test(ackType || "")) return { ...styles.ackHero, background: "#fdecec", color: "#b42318" };
  return { ...styles.ackHero, background: "#eef1f5", color: "#5b677a" };
}

function errorRows(ack) {
  const codes = (ack && ack.errorCodes) || [];
  const messages = (ack && ack.errorMessages) || [];
  const n = Math.max(codes.length, messages.length);
  const rows = [];
  for (let i = 0; i < n; i += 1) {
    rows.push({ code: codes[i] || "", message: messages[i] || "" });
  }
  return rows;
}

function xmlForDisplay(xml, packed) {
  const redacted = redactCredentials(xml);
  return packed ? redacted : prettyXml(redacted);
}

function Field({ label, value }) {
  return (
    <>
      <dt style={styles.dt}>{label}</dt>
      <dd style={styles.dd}>{value == null || value === "" ? "—" : value}</dd>
    </>
  );
}

function yesNo(value) {
  if (value == null) return null;
  return value ? "yes" : "no";
}

export default function SendInspector() {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const selectedId = searchParams.get("id") || "";
  // Ack fetches outlive the selection; skip setSelected/setAckNotice if this no longer matches.
  const currentIdRef = useRef(selectedId);
  currentIdRef.current = selectedId;

  const [sends, setSends] = useState([]);
  const [selected, setSelected] = useState(null);
  const [session, setSession] = useState(null);
  const [phase, setPhase] = useState("loading");
  const [detailError, setDetailError] = useState(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [ackBusy, setAckBusy] = useState(false);
  const [authBusy, setAuthBusy] = useState(false);
  const [ackNotice, setAckNotice] = useState(null);
  const [tab, setTab] = useState("returnXml");
  const [packed, setPacked] = useState(false);
  const [copied, setCopied] = useState(false);

  function selectId(id) {
    const params = new URLSearchParams(searchParams.toString());
    if (id) params.set("id", id);
    else params.delete("id");
    const query = params.toString();
    router.replace(query ? `${pathname}?${query}` : pathname, { scroll: false });
  }

  async function loadListAndSession() {
    setPhase("loading");
    setAckNotice(null);
    try {
      const [list, sess] = await Promise.all([
        listSends(),
        getSession().catch((err) => {
          if (isUnreachableError(err)) throw err;
          return null;
        }),
      ]);
      const rows = Array.isArray(list) ? list : [];
      setSends(rows);
      setSession(sess);
      if (rows.length === 0) {
        setSelected(null);
        setPhase("empty");
        return;
      }
      setPhase("ready");
      if (!selectedId) {
        selectId(rows[0].submissionId);
      }
    } catch (err) {
      setSends([]);
      setSelected(null);
      setPhase("unreachable");
    }
  }

  async function reloadAfterAck(id) {
    const [detail, list] = await Promise.all([getSend(id), listSends()]);
    setSends(Array.isArray(list) ? list : []);
    if (currentIdRef.current !== id) return;
    setSelected(detail);
  }

  useEffect(() => {
    void loadListAndSession();
  }, []);

  useEffect(() => {
    if (phase !== "ready" || selectedId || sends.length === 0) return;
    selectId(sends[0].submissionId);
  }, [phase, selectedId, sends]);

  useEffect(() => {
    if (phase !== "ready") return undefined;
    if (!selectedId) {
      setSelected(null);
      setDetailError(null);
      return undefined;
    }
    let cancelled = false;
    setDetailLoading(true);
    setDetailError(null);
    setAckNotice(null);
    getSend(selectedId)
      .then((data) => {
        if (cancelled) return;
        setSelected(data);
        setDetailLoading(false);
      })
      .catch((err) => {
        if (cancelled) return;
        setSelected(null);
        setDetailLoading(false);
        if (isUnreachableError(err)) {
          setPhase("unreachable");
          return;
        }
        setDetailError(err);
      });
    return () => {
      cancelled = true;
    };
  }, [selectedId, phase]);

  async function onLogin() {
    setAuthBusy(true);
    setAckNotice(null);
    try {
      await login();
      setSession(await getSession());
    } catch (err) {
      setAckNotice({
        kind: "error",
        text: isUnreachableError(err)
          ? "Cannot reach the MeF backend."
          : err.message || "Login failed.",
      });
    } finally {
      setAuthBusy(false);
    }
  }

  async function onLogout() {
    setAuthBusy(true);
    setAckNotice(null);
    try {
      await logout();
      setSession(await getSession());
    } catch (err) {
      setAckNotice({
        kind: "error",
        text: isUnreachableError(err)
          ? "Cannot reach the MeF backend."
          : err.message || "Logout failed.",
      });
    } finally {
      setAuthBusy(false);
    }
  }

  async function refreshSession() {
    try {
      setSession(await getSession());
    } catch {
      // 409 already forced logged out; keep the last known session otherwise
    }
  }

  async function onFetchAck() {
    const id = selectedId;
    if (!id) return;
    setAckBusy(true);
    setAckNotice(null);
    try {
      const ack = await fetchAck(id);
      if (currentIdRef.current === id) {
        setSelected((s) => (s && s.submissionId === id ? { ...s, ack } : s));
      }
      try {
        await reloadAfterAck(id);
      } catch {
        // StoredAck is already on screen; a later detail/list miss is not an ack failure.
      }
    } catch (err) {
      if (err.code === "NOT_LOGGED_IN" || err.status === 409) {
        setSession((s) => (s ? { ...s, loggedIn: false } : { loggedIn: false }));
      }
      if (currentIdRef.current !== id) return;
      if (err.code === "NOT_LOGGED_IN" || err.status === 409) {
        setAckNotice({ kind: "warn", text: "Log in to IRS first, then fetch the acknowledgment." });
      } else if (err.code === "ACK_NOT_FOUND") {
        setAckNotice({
          kind: "warn",
          text: "IRS has no ack yet. IRS needs about 2 to 5 minutes after a send.",
        });
      } else if (err.status >= 500 && err.json !== true) {
        setAckNotice({
          kind: "error",
          text: "Request timed out or the proxy failed; reload to check whether the ack was stored.",
        });
      } else if (isUnreachableError(err)) {
        setAckNotice({ kind: "error", text: "Cannot reach the MeF backend." });
      } else {
        setAckNotice({ kind: "error", text: err.message || "Could not fetch ack." });
      }
    } finally {
      setAckBusy(false);
      await refreshSession();
    }
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

  const loggedIn = Boolean(session && session.loggedIn);
  const transmission = selected && selected.transmission;
  const ack = selected && selected.ack;
  const docs = (selected && selected.documents) || {};
  const mime = (selected && selected.mime) || { attachments: [] };
  const activeTab = TABS.find((item) => item.id === tab) || TABS[0];
  const activeDoc = activeTab.docKey ? docs[activeTab.docKey] : null;

  let body = null;
  if (phase === "loading") {
    body = <p style={styles.empty}>Loading captured sends…</p>;
  } else if (phase === "unreachable") {
    body = (
      <div style={styles.empty}>
        <p style={{ marginTop: 0 }}>Cannot reach the MeF backend.</p>
        <p>Start it on :8080, then retry.</p>
        <button type="button" style={styles.primary} onClick={loadListAndSession}>
          Retry
        </button>
      </div>
    );
  } else if (phase === "empty") {
    body = (
      <div style={styles.empty}>
        <p style={{ marginTop: 0 }}>No sends captured yet.</p>
        <p>
          The backend must be running on :8080 via{" "}
          <code>cd ../mef-spring-boot-integration && mvn spring-boot:run</code>.
        </p>
        <button type="button" style={styles.primary} onClick={loadListAndSession}>
          Retry
        </button>
      </div>
    );
  } else {
    body = (
      <div style={styles.layout} className="inspector-layout">
        <aside style={styles.list} aria-label="Captured sends">
          <div style={styles.listHead}>Captured sends</div>
          {sends.map((send) => {
            const active = send.submissionId === selectedId;
            return (
              <button
                key={send.submissionId}
                type="button"
                onClick={() => selectId(send.submissionId)}
                style={{
                  ...styles.sendBtn,
                  background: active ? "#eef4f8" : "transparent",
                }}
                aria-current={active ? "true" : undefined}
              >
                <div style={styles.sendId}>{send.submissionId}</div>
                <div style={styles.meta}>
                  {send.formCode} {send.periodLabel}
                  <br />
                  {formatLocalTime(send.capturedAt)}
                </div>
                <div style={styles.badges}>
                  <span style={badgeStyle("state")}>{send.state || "no state"}</span>
                  <span style={badgeStyle(send.ackType)}>
                    {send.ackType || "no ack"}
                  </span>
                </div>
              </button>
            );
          })}
        </aside>

        <article style={styles.detail}>
          {detailError ? (
            <p style={styles.empty}>
              {detailError.code === "SEND_NOT_FOUND"
                ? "That send is not in the capture ring."
                : detailError.message || "Could not load this send."}
            </p>
          ) : !selectedId ? (
            <p style={styles.empty}>Select a send from the list.</p>
          ) : !selected || selected.submissionId !== selectedId ? (
            <p style={styles.empty}>Loading this send…</p>
          ) : (
            <>
              <section style={styles.card} aria-label="Send summary">
                <h2 style={styles.cardTitle}>Summary</h2>
                <dl style={styles.dl}>
                  <Field label="Submission ID" value={selected.submissionId} />
                  <Field label="Environment" value={selected.environment} />
                  <Field label="EIN" value={selected.einMasked} />
                  <Field label="Form" value={selected.formType} />
                  <Field
                    label="Tax period"
                    value={
                      selected.taxPeriodBegin || selected.taxPeriodEnd
                        ? `${selected.taxPeriodBegin || "—"} to ${selected.taxPeriodEnd || "—"}`
                        : null
                    }
                  />
                  <Field label="Client request ID" value={selected.clientRequestId} />
                  <Field label="Deposit ID" value={transmission && transmission.depositId} />
                  <Field
                    label="Receipt timestamp"
                    value={formatLocalTime(transmission && transmission.receiptTimestamp)}
                  />
                  <Field label="State" value={transmission && transmission.state} />
                </dl>
                {transmission && (transmission.faultCode || transmission.faultMessage) ? (
                  <p style={styles.fault}>
                    {transmission.faultCode ? `${transmission.faultCode}: ` : ""}
                    {transmission.faultMessage || ""}
                  </p>
                ) : null}
              </section>

              <section style={styles.ackCard} aria-label="IRS acknowledgment">
                <h2 style={styles.cardTitle}>IRS acknowledgment</h2>
                {ack ? (
                  <>
                    <div style={ackHeroStyle(ack.ackType)}>{ack.ackType || "unknown"}</div>
                    <dl style={styles.dl}>
                      <Field label="Retrieved at" value={formatLocalTime(ack.retrievedAt)} />
                      <Field label="Source" value={ack.source} />
                      <Field
                        label="Electronic postmark"
                        value={formatLocalTime(ack.electronicPostmarkTs)}
                      />
                      <Field label="IRS received date" value={ack.irsReceivedDate} />
                      <Field label="Receipt ID" value={ack.receiptId} />
                      <Field label="Tax year" value={ack.taxYear} />
                      <Field label="Submission type" value={ack.submissionType} />
                      <Field label="Validation errors" value={yesNo(ack.hasValidationErrors)} />
                      <Field label="Validation alerts" value={yesNo(ack.hasValidationAlerts)} />
                    </dl>
                    {errorRows(ack).length > 0 ? (
                      <table style={styles.table}>
                        <thead>
                          <tr>
                            <th style={styles.th}>Code</th>
                            <th style={styles.th}>Message</th>
                          </tr>
                        </thead>
                        <tbody>
                          {errorRows(ack).map((row, index) => (
                            <tr key={`${row.code}-${index}`}>
                              <td style={{ ...styles.td, whiteSpace: "nowrap" }}>{row.code}</td>
                              <td style={{ ...styles.td, fontFamily: "inherit" }}>{row.message}</td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    ) : null}
                    {ack.hasValidationErrors && ack.details ? (
                      <pre style={{ ...styles.pre, whiteSpace: "pre-wrap", marginTop: 10 }}>
                        {ack.details}
                      </pre>
                    ) : null}
                  </>
                ) : (
                  <p style={{ marginTop: 0, fontSize: 14 }}>
                    No acknowledgment is stored for this send yet. Fetch it from IRS after the
                    return has had time to process.
                  </p>
                )}
                {ackNotice ? (
                  <p role="alert" style={ackNotice.kind === "error" ? styles.fault : styles.missing}>
                    {ackNotice.text}
                  </p>
                ) : null}
                <div style={styles.actions}>
                  <span style={styles.session}>
                    IRS session: {loggedIn ? "logged in" : "not logged in"}
                    {session && session.message ? ` — ${session.message}` : ""}
                  </span>
                  <button type="button" style={styles.primary} onClick={onFetchAck} disabled={ackBusy || authBusy}>
                    {ackBusy ? "Fetching…" : ack ? "Refresh ack" : "Fetch ack from IRS"}
                  </button>
                  <button
                    type="button"
                    style={styles.toggle}
                    onClick={onLogin}
                    disabled={authBusy || ackBusy || loggedIn}
                  >
                    Log in to IRS
                  </button>
                  <button
                    type="button"
                    style={styles.toggle}
                    onClick={onLogout}
                    disabled={authBusy || ackBusy || !loggedIn}
                  >
                    Log out
                  </button>
                </div>
              </section>

              <section style={styles.card} aria-label="What we sent">
                <div style={styles.sentHead}>
                  <h2 style={{ ...styles.cardTitle, margin: 0 }}>What we sent</h2>
                  {tab !== "mime" ? (
                    <button type="button" style={styles.toggle} onClick={() => setPacked((v) => !v)}>
                      {packed ? "Show pretty XML" : "Show as packed"}
                    </button>
                  ) : null}
                </div>
                <div style={styles.tabBar} role="tablist">
                  {TABS.map((item) => {
                    const active = item.id === tab;
                    return (
                      <button
                        key={item.id}
                        type="button"
                        role="tab"
                        aria-selected={active}
                        onClick={() => setTab(item.id)}
                        style={{
                          ...styles.tab,
                          background: active ? "#eef4f8" : "#fff",
                          borderColor: active ? "#274869" : "#d8dee8",
                          color: active ? "#274869" : "inherit",
                        }}
                      >
                        {item.label}
                      </button>
                    );
                  })}
                </div>
                {tab === "mime" ? (
                  <>
                    {mime.contentType ? (
                      <div style={styles.mimeLine}>Content-Type: {mime.contentType}</div>
                    ) : null}
                    {(mime.attachments || []).length === 0 ? (
                      <p style={styles.missing}>No MIME attachments captured.</p>
                    ) : (
                      <table style={styles.table}>
                        <thead>
                          <tr>
                            <th style={styles.th}>contentId</th>
                            <th style={styles.th}>contentType</th>
                            <th style={styles.th}>byteLength</th>
                            <th style={styles.th}>sha256</th>
                          </tr>
                        </thead>
                        <tbody>
                          {(mime.attachments || []).map((part, index) => (
                            <tr key={part.contentId || index}>
                              <td style={styles.td}>{part.contentId}</td>
                              <td style={styles.td}>{part.contentType}</td>
                              <td style={styles.td}>{part.byteLength}</td>
                              <td style={styles.td}>{part.sha256}</td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    )}
                  </>
                ) : activeDoc && activeDoc.missingReason ? (
                  <p style={styles.missing}>{activeDoc.missingReason}</p>
                ) : activeDoc && activeDoc.text ? (
                  <>
                    <div style={{ display: "flex", justifyContent: "flex-end", marginBottom: 8 }}>
                      <button
                        type="button"
                        style={styles.toggle}
                        onClick={() => copyText(xmlForDisplay(activeDoc.text, packed))}
                      >
                        {copied ? "Copied" : "Copy"}
                      </button>
                    </div>
                    <pre style={styles.pre}>{xmlForDisplay(activeDoc.text, packed)}</pre>
                  </>
                ) : (
                  <p style={styles.missing}>Not captured.</p>
                )}
              </section>
            </>
          )}
        </article>
      </div>
    );
  }

  return (
    <div style={styles.page}>
      <p style={styles.banner}>
        Live captures from this backend — what we sent to IRS, and the acknowledgment IRS
        returned. SAML / UsernameToken are redacted if present.
      </p>
      <h1 style={styles.header}>MeF send inspector</h1>
      <p style={styles.lead}>
        Pick a send to read the Return XML, manifest, SOAP, MIME attachments, and IRS
        acknowledgment.
      </p>
      {body}
      <style>{`
        @media (max-width: 900px) {
          .inspector-layout { grid-template-columns: 1fr !important; }
        }
      `}</style>
    </div>
  );
}
