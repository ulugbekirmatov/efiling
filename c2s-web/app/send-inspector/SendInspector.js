"use client";

import { useEffect, useRef, useState } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { Badge } from "../design-system/Badge";
import { TONES, toneClass } from "../design-system/tones";
import XmlCode from "../design-system/XmlCode";
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

function formatLocalTime(iso) {
  if (!iso) return "—";
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return String(iso);
  return date.toLocaleString();
}

// A warn asks the operator to do something next (log in, wait); an error is a failed call.
const NOTICE_CLASS = { error: "attention", warn: "" };

function stateTone(state) {
  if (!state) return TONES.waiting;
  if (/fault/i.test(state)) return TONES.attention;
  return TONES.neutral;
}

function ackTone(ackType) {
  if (/^accept/i.test(ackType || "")) return TONES.safe;
  if (/^reject/i.test(ackType || "")) return TONES.attention;
  return TONES.waiting;
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

function Field({ label, value, mono = false }) {
  return (
    <>
      <dt>{label}</dt>
      <dd className={mono ? "mono" : undefined}>
        {value == null || value === "" ? "—" : value}
      </dd>
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
    body = <p className="empty">Loading captured sends…</p>;
  } else if (phase === "unreachable") {
    body = (
      <div className="empty">
        <p>Cannot reach the MeF backend.</p>
        <p>Start it on :8080, then retry.</p>
        <button type="button" className="btn btn-primary" onClick={loadListAndSession}>
          Retry
        </button>
      </div>
    );
  } else if (phase === "empty") {
    body = (
      <div className="empty">
        <p>No sends captured yet.</p>
        <p>
          The backend must be running on :8080 via{" "}
          <code>cd ../mef-spring-boot-integration && mvn spring-boot:run</code>.
        </p>
        <button type="button" className="btn btn-primary" onClick={loadListAndSession}>
          Retry
        </button>
      </div>
    );
  } else {
    body = (
      <div className="inspector-layout">
        <aside className="panel" aria-label="Captured sends">
          <div className="panel-head">Captured sends</div>
          <div className="select-list inspector-sends">
            {sends.map((send) => {
              const active = send.submissionId === selectedId;
              return (
                <button
                  key={send.submissionId}
                  type="button"
                  className="select-item"
                  onClick={() => selectId(send.submissionId)}
                  aria-current={active ? "true" : undefined}
                >
                  <div className="mono">{send.submissionId}</div>
                  <div className="muted inspector-meta">
                    {send.formCode} {send.periodLabel}
                    <br />
                    {formatLocalTime(send.capturedAt)}
                  </div>
                  <div className="row inspector-badges">
                    <Badge tone={stateTone(send.state)}>
                      {send.state || "no state"}
                    </Badge>
                    <Badge tone={ackTone(send.ackType)}>{send.ackType || "no ack"}</Badge>
                  </div>
                </button>
              );
            })}
          </div>
        </aside>

        <article className="panel">
          {detailError ? (
            <p className="panel-section muted">
              {detailError.code === "SEND_NOT_FOUND"
                ? "That send is not in the capture ring."
                : detailError.message || "Could not load this send."}
            </p>
          ) : !selectedId ? (
            <p className="panel-section muted">Select a send from the list.</p>
          ) : !selected || selected.submissionId !== selectedId ? (
            <p className="panel-section muted">Loading this send…</p>
          ) : (
            <>
              <section className="panel-section" aria-label="Send summary">
                <h2 className="section-label">Summary</h2>
                <dl className="kv">
                  <Field label="Submission ID" value={selected.submissionId} mono />
                  <Field label="Environment" value={selected.environment} />
                  <Field label="EIN" value={selected.einMasked} mono />
                  <Field label="Form" value={selected.formType} />
                  <Field
                    label="Tax period"
                    value={
                      selected.taxPeriodBegin || selected.taxPeriodEnd
                        ? `${selected.taxPeriodBegin || "—"} to ${selected.taxPeriodEnd || "—"}`
                        : null
                    }
                  />
                  <Field label="Client request ID" value={selected.clientRequestId} mono />
                  <Field label="Deposit ID" value={transmission && transmission.depositId} mono />
                  <Field
                    label="Receipt timestamp"
                    value={formatLocalTime(transmission && transmission.receiptTimestamp)}
                  />
                  <Field label="State" value={transmission && transmission.state} />
                </dl>
                {transmission && (transmission.faultCode || transmission.faultMessage) ? (
                  <p className="callout attention inspector-gap">
                    {transmission.faultCode ? `${transmission.faultCode}: ` : ""}
                    {transmission.faultMessage || ""}
                  </p>
                ) : null}
              </section>

              <section className="panel-section tinted" aria-label="IRS acknowledgment">
                <h2 className="section-label">IRS acknowledgment</h2>
                {ack ? (
                  <>
                    <span className={`badge lg inspector-hero ${toneClass(ackTone(ack.ackType))}`}>
                      {ack.ackType || "unknown"}
                    </span>
                    <dl className="kv">
                      <Field label="Retrieved at" value={formatLocalTime(ack.retrievedAt)} />
                      <Field label="Source" value={ack.source} />
                      <Field
                        label="Electronic postmark"
                        value={formatLocalTime(ack.electronicPostmarkTs)}
                      />
                      <Field label="IRS received date" value={ack.irsReceivedDate} />
                      <Field label="Receipt ID" value={ack.receiptId} mono />
                      <Field label="Tax year" value={ack.taxYear} />
                      <Field label="Submission type" value={ack.submissionType} />
                      <Field label="Validation errors" value={yesNo(ack.hasValidationErrors)} />
                      <Field label="Validation alerts" value={yesNo(ack.hasValidationAlerts)} />
                    </dl>
                    {errorRows(ack).length > 0 ? (
                      <div className="table-wrap inspector-gap">
                        <table className="data">
                          <thead>
                            <tr>
                              <th>Code</th>
                              <th>Message</th>
                            </tr>
                          </thead>
                          <tbody>
                            {errorRows(ack).map((row, index) => (
                              <tr key={`${row.code}-${index}`}>
                                <td className="nowrap mono">{row.code}</td>
                                <td>{row.message}</td>
                              </tr>
                            ))}
                          </tbody>
                        </table>
                      </div>
                    ) : null}
                    {ack.hasValidationErrors && ack.details ? (
                      <div className="code wrap tall inspector-gap">
                        <pre>{ack.details}</pre>
                      </div>
                    ) : null}
                  </>
                ) : (
                  <p>
                    No acknowledgment is stored for this send yet. Fetch it from IRS after the
                    return has had time to process.
                  </p>
                )}
                {ackNotice ? (
                  <p
                    role="alert"
                    className={`callout inspector-gap ${NOTICE_CLASS[ackNotice.kind] || ""}`}
                  >
                    {ackNotice.text}
                  </p>
                ) : null}
                <div className="row inspector-actions">
                  <span>
                    IRS session:{" "}
                    <Badge tone={loggedIn ? TONES.safe : TONES.waiting}>
                      {loggedIn ? "logged in" : "not logged in"}
                    </Badge>
                    {session && session.message ? ` — ${session.message}` : ""}
                  </span>
                  <button
                    type="button"
                    className="btn btn-primary"
                    onClick={onFetchAck}
                    disabled={ackBusy || authBusy}
                  >
                    {ackBusy ? "Fetching…" : ack ? "Refresh ack" : "Fetch ack from IRS"}
                  </button>
                  <button
                    type="button"
                    className="btn btn-secondary"
                    onClick={onLogin}
                    disabled={authBusy || ackBusy || loggedIn}
                  >
                    Log in to IRS
                  </button>
                  <button
                    type="button"
                    className="btn btn-secondary"
                    onClick={onLogout}
                    disabled={authBusy || ackBusy || !loggedIn}
                  >
                    Log out
                  </button>
                </div>
              </section>

              <section className="panel-section" aria-label="What we sent">
                <div className="inspector-sent-head">
                  <h2 className="section-label">What we sent</h2>
                  {tab !== "mime" ? (
                    <button type="button" className="btn-mini" onClick={() => setPacked((v) => !v)}>
                      {packed ? "Show pretty XML" : "Show as packed"}
                    </button>
                  ) : null}
                </div>
                <div className="tabs" role="tablist">
                  {TABS.map((item) => {
                    const active = item.id === tab;
                    return (
                      <button
                        key={item.id}
                        type="button"
                        role="tab"
                        className="tab"
                        aria-selected={active}
                        onClick={() => setTab(item.id)}
                      >
                        {item.label}
                      </button>
                    );
                  })}
                </div>
                {tab === "mime" ? (
                  <>
                    {mime.contentType ? (
                      <div className="chip inspector-mime">Content-Type: {mime.contentType}</div>
                    ) : null}
                    {(mime.attachments || []).length === 0 ? (
                      <p className="callout waiting">No MIME attachments captured.</p>
                    ) : (
                      <div className="table-wrap">
                        <table className="data wide">
                          <thead>
                            <tr>
                              <th>contentId</th>
                              <th>contentType</th>
                              <th className="num">byteLength</th>
                              <th>sha256</th>
                            </tr>
                          </thead>
                          <tbody>
                            {(mime.attachments || []).map((part, index) => (
                              <tr key={part.contentId || index}>
                                <td className="mono">{part.contentId}</td>
                                <td className="mono">{part.contentType}</td>
                                <td className="num">{part.byteLength}</td>
                                <td className="mono inspector-hash">{part.sha256}</td>
                              </tr>
                            ))}
                          </tbody>
                        </table>
                      </div>
                    )}
                  </>
                ) : activeDoc && activeDoc.missingReason ? (
                  <p className="callout waiting">{activeDoc.missingReason}</p>
                ) : activeDoc && activeDoc.text ? (
                  <>
                    <div className="actions end">
                      <button
                        type="button"
                        className="btn-mini"
                        onClick={() => copyText(xmlForDisplay(activeDoc.text, packed))}
                      >
                        {copied ? "Copied" : "Copy"}
                      </button>
                    </div>
                    <XmlCode
                      text={xmlForDisplay(activeDoc.text, packed)}
                      className="tall"
                      label={activeTab.label}
                    />
                  </>
                ) : (
                  <p className="callout waiting">Not captured.</p>
                )}
              </section>
            </>
          )}
        </article>
      </div>
    );
  }

  return (
    <div className="page">
      <header className="page-head">
        <p className="eyebrow">Live captures</p>
        <h1>MeF send inspector</h1>
        <p className="intro">
          Pick a send to read the Return XML, manifest, SOAP, MIME attachments, and IRS
          acknowledgment.
        </p>
        <p className="callout inspector-banner">
          Live captures from this backend — what we sent to IRS, and the acknowledgment IRS
          returned. SAML / UsernameToken are redacted if present.
        </p>
      </header>
      {body}
    </div>
  );
}
