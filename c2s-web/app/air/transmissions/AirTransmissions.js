"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { Badge } from "../../design-system/Badge";
import { TONES, toneClass } from "../../design-system/tones";
import XmlCode from "../../design-system/XmlCode";
import { confirmCode } from "../lib/runModel.js";
import {
  checkRunStatus,
  getReadiness,
  getRun,
  isUnreachableError,
  listRuns,
  previewRun,
  submitRun,
  validateRun,
} from "./api";
import {
  actionState,
  displayDoc,
  displayStage,
  downloadHref,
  errorRows,
  formatBytes,
  formatLocalTime,
  formatPriorYear,
  formatValue,
  noticeFrom,
  previewStderr,
  readinessItems,
  runStage,
  SENT_TABS,
  statusHistoryRows,
  submitArmed,
  validationRows,
  xmlForDisplay,
} from "./view";

function Field({ label, value, mono = false, breakAll = false }) {
  const classes = [mono ? "mono" : "", breakAll ? "air-transmissions-break" : ""]
    .filter(Boolean)
    .join(" ");
  return (
    <>
      <dt>{label}</dt>
      <dd className={classes || undefined}>{value == null || value === "" ? "—" : value}</dd>
    </>
  );
}

function ActionReason({ action }) {
  if (action.enabled || !action.reason) return null;
  return <span className="muted air-transmissions-action-reason">{action.reason}</span>;
}

function StderrBlock({ text }) {
  const shown = previewStderr(text);
  if (!shown.full) return <span className="muted">—</span>;
  return (
    <div className="air-transmissions-stderr">
      <div className="code wrap">
        <pre>{shown.preview}</pre>
      </div>
      {shown.truncated ? (
        <details className="air-transmissions-stderr-full">
          <summary>Full stderr</summary>
          <div className="code wrap">
            <pre>{shown.full}</pre>
          </div>
        </details>
      ) : null}
    </div>
  );
}

export default function AirTransmissions() {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const selectedId = searchParams.get("id") || "";
  const currentIdRef = useRef(selectedId);
  currentIdRef.current = selectedId;
  const requestGenRef = useRef(0);
  const tabListRef = useRef(null);

  const [runs, setRuns] = useState([]);
  const [selected, setSelected] = useState(null);
  const [readiness, setReadiness] = useState(null);
  const [phase, setPhase] = useState("loading");
  const [detailError, setDetailError] = useState(null);
  const [busy, setBusy] = useState(null);
  const [notice, setNotice] = useState(null);
  const [tab, setTab] = useState("form");
  const [packed, setPacked] = useState(false);
  const [copied, setCopied] = useState(false);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [confirmTyped, setConfirmTyped] = useState("");
  const [nowMs, setNowMs] = useState(() => Date.now());

  function selectId(id) {
    const params = new URLSearchParams(searchParams.toString());
    if (id) params.set("id", id);
    else params.delete("id");
    const query = params.toString();
    router.replace(query ? `${pathname}?${query}` : pathname, { scroll: false });
  }

  function isLatestForId(id, gen) {
    return requestGenRef.current === gen && currentIdRef.current === id;
  }

  async function loadListAndReadiness() {
    setPhase("loading");
    setNotice(null);
    try {
      const [list, ready] = await Promise.all([
        listRuns(),
        getReadiness().catch((err) => {
          if (isUnreachableError(err)) throw err;
          return null;
        }),
      ]);
      const rows = Array.isArray(list) ? list : [];
      setRuns(rows);
      setReadiness(ready);
      if (rows.length === 0) {
        setSelected(null);
        setPhase("empty");
        return;
      }
      setPhase("ready");
      if (!selectedId) {
        selectId(rows[0].id);
      }
    } catch (err) {
      setRuns([]);
      setSelected(null);
      setPhase("unreachable");
    }
  }

  async function reloadAfterAction(id) {
    const gen = ++requestGenRef.current;
    const [detail, list, ready] = await Promise.all([
      getRun(id),
      listRuns(),
      getReadiness().catch((err) => {
        if (isUnreachableError(err)) throw err;
        return readiness;
      }),
    ]);
    setRuns(Array.isArray(list) ? list : []);
    setReadiness(ready);
    if (!isLatestForId(id, gen)) return;
    setSelected(detail);
  }

  useEffect(() => {
    void loadListAndReadiness();
  }, []);

  useEffect(() => {
    const timer = setInterval(() => setNowMs(Date.now()), 15000);
    return () => clearInterval(timer);
  }, []);

  useEffect(() => {
    if (phase !== "ready" || selectedId || runs.length === 0) return;
    selectId(runs[0].id);
  }, [phase, selectedId, runs]);

  useEffect(() => {
    setConfirmOpen(false);
    setConfirmTyped("");
    setNotice(null);
    setDetailError(null);
  }, [selectedId]);

  useEffect(() => {
    if (phase !== "ready") return undefined;
    if (!selectedId) {
      requestGenRef.current += 1;
      setSelected(null);
      setDetailError(null);
      return undefined;
    }
    const id = selectedId;
    const gen = ++requestGenRef.current;
    let cancelled = false;
    setDetailError(null);
    setNotice(null);
    getRun(id)
      .then((data) => {
        if (cancelled || !isLatestForId(id, gen)) return;
        setSelected(data);
      })
      .catch((err) => {
        if (cancelled || !isLatestForId(id, gen)) return;
        setSelected(null);
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

  async function runAction(id, kind, fn) {
    if (!id) return;
    setBusy(kind);
    setNotice(null);
    try {
      await fn();
      try {
        await reloadAfterAction(id);
      } catch (err) {
        if (currentIdRef.current !== id) return;
        setNotice(noticeFrom(err));
      }
    } catch (err) {
      if (currentIdRef.current !== id) return;
      setNotice(noticeFrom(err));
    } finally {
      setBusy(null);
      setNowMs(Date.now());
    }
  }

  function onValidate() {
    const id = selectedId;
    void runAction(id, "validate", () => validateRun(id));
  }

  function onPreview() {
    const id = selectedId;
    void runAction(id, "preview", () => previewRun(id));
  }

  function onSubmit() {
    const id = selectedId;
    const typed = confirmTyped;
    setConfirmOpen(false);
    setConfirmTyped("");
    void runAction(id, "submit", () => submitRun(id, typed));
  }

  function onCheckStatus() {
    const id = selectedId;
    void runAction(id, "status", () => checkRunStatus(id));
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
    const currentIndex = SENT_TABS.findIndex((item) => item.id === tab);
    let nextIndex = currentIndex < 0 ? 0 : currentIndex;
    if (event.key === "Home") nextIndex = 0;
    else if (event.key === "End") nextIndex = SENT_TABS.length - 1;
    else if (event.key === "ArrowLeft") {
      nextIndex = (nextIndex - 1 + SENT_TABS.length) % SENT_TABS.length;
    } else {
      nextIndex = (nextIndex + 1) % SENT_TABS.length;
    }
    const nextId = SENT_TABS[nextIndex].id;
    setTab(nextId);
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

  const actions = actionState(selected, readiness, nowMs);
  const stage = displayStage(selected, busy);
  const docs = (selected && selected.documents) || {};
  const statusResponses = (selected && selected.statusResponses) || [];
  const activeTab = SENT_TABS.find((item) => item.id === tab) || SENT_TABS[0];
  const activeDoc = displayDoc(activeTab, docs, statusResponses);
  const errors = errorRows(selected && selected.errors);
  const history = statusHistoryRows(selected);
  const validations = validationRows(selected && selected.validation);
  const working = Boolean(busy);

  let body = null;
  if (phase === "loading") {
    body = <p className="empty">Loading transmissions…</p>;
  } else if (phase === "unreachable") {
    body = (
      <div className="empty">
        <p>Cannot reach the AIR operator API.</p>
        <button type="button" className="btn btn-primary" onClick={loadListAndReadiness}>
          Retry
        </button>
      </div>
    );
  } else if (phase === "empty") {
    body = (
      <div className="empty">
        <p>
          No runs yet. Compose one from <Link href="/air/scenarios">AATS scenarios</Link>.
        </p>
        <button type="button" className="btn btn-primary" onClick={loadListAndReadiness}>
          Retry
        </button>
      </div>
    );
  } else {
    body = (
      <div className="air-transmissions-layout">
        <aside className="panel" aria-label="Runs">
          <div className="panel-head">Runs</div>
          <div className="select-list air-transmissions-runs">
            {runs.map((run) => {
              const active = run.id === selectedId;
              const info = runStage(run);
              return (
                <button
                  key={run.id}
                  type="button"
                  className="select-item"
                  onClick={() => selectId(run.id)}
                  aria-current={active ? "true" : undefined}
                >
                  <div className="mono">{run.id}</div>
                  <div className="muted air-transmissions-meta">
                    {run.scenarioId} {run.paymentYear}
                    <br />
                    {formatLocalTime(run.composedAt)}
                  </div>
                  <div className="row air-transmissions-badges">
                    <Badge tone={info.tone}>{info.label}</Badge>
                  </div>
                </button>
              );
            })}
          </div>
        </aside>

        <article className="panel">
          {detailError ? (
            <p className="panel-section muted">
              {detailError.code === "RUN_NOT_FOUND" || detailError.status === 404
                ? "That run is not on disk."
                : detailError.message || "Could not load this run."}
            </p>
          ) : !selectedId ? (
            <p className="panel-section muted">Select a run from the list.</p>
          ) : !selected || selected.id !== selectedId ? (
            <p className="panel-section muted">Loading this run…</p>
          ) : (
            <>
              <section className="panel-section" aria-label="Run summary">
                <h2 className="section-label">Summary</h2>
                <dl className="kv">
                  <Field label="Run ID" value={selected.id} mono />
                  <Field label="Scenario" value={selected.scenarioId} />
                  <Field label="Environment" value={selected.environment} />
                  <Field label="Test file code" value={selected.testFileCd} />
                  <Field label="TCC" value={selected.tcc} mono />
                  <Field label="Transmitter" value={selected.transmitterName} />
                  <Field label="Tax year" value={selected.paymentYear} />
                  <Field label="Prior-year data" value={formatPriorYear(selected.priorYearData)} />
                  <Field label="Payees" value={formatValue(selected.payeeCount)} />
                  <Field label="UTID" value={selected.utid} mono breakAll />
                  <Field label="File name" value={selected.fileName} mono breakAll />
                  <Field label="Checksum" value={selected.checksum} mono breakAll />
                  <Field label="Byte size" value={formatBytes(selected.byteSize)} />
                  <Field label="Receipt ID" value={selected.receiptId} mono />
                  <Field label="Composed at" value={formatLocalTime(selected.composedAt)} />
                </dl>
              </section>

              <section className="panel-section tinted" aria-label="IRS answer">
                <h2 className="section-label">IRS answer</h2>
                <span className={`badge lg air-transmissions-hero ${toneClass(stage.tone)}`}>
                  {stage.label}
                </span>
                <p className="air-transmissions-next">{stage.next}</p>
                {selected.submit && selected.submit.stderr ? (
                  <p className="callout attention air-transmissions-gap">{selected.submit.stderr}</p>
                ) : null}
                {errors.length > 0 ? (
                  <div className="table-wrap air-transmissions-gap">
                    <table className="data">
                      <thead>
                        <tr>
                          <th>Code</th>
                          <th>Text</th>
                          <th>XPath</th>
                        </tr>
                      </thead>
                      <tbody>
                        {errors.map((row, index) => (
                          <tr key={`${row.code}-${index}`}>
                            <td className="nowrap mono">{row.code}</td>
                            <td>{row.text}</td>
                            <td className="mono air-transmissions-break">{row.xpath}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                ) : null}
                {history.length > 0 ? (
                  <div className="table-wrap air-transmissions-gap">
                    <table className="data">
                      <thead>
                        <tr>
                          <th>At</th>
                          <th>Exit code</th>
                          <th>Status</th>
                          <th>Stderr</th>
                        </tr>
                      </thead>
                      <tbody>
                        {history.map((row, index) => (
                          <tr key={`${row.at}-${index}`}>
                            <td className="nowrap">{formatLocalTime(row.at)}</td>
                            <td className="num">{row.exitCode}</td>
                            <td>{row.status}</td>
                            <td>
                              <StderrBlock text={row.stderr} />
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                ) : null}
                {validations.length > 0 ? (
                  <div className="table-wrap air-transmissions-gap">
                    <table className="data">
                      <thead>
                        <tr>
                          <th>File</th>
                          <th>Result</th>
                          <th>Errors</th>
                        </tr>
                      </thead>
                      <tbody>
                        {validations.map((row, index) => (
                          <tr key={`${row.path}-${index}`}>
                            <td className="mono">{row.path}</td>
                            <td>{row.result}</td>
                            <td>{row.errors.length > 0 ? row.errors.join("; ") : "—"}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                ) : null}

                <div className="row air-transmissions-readiness" aria-label="Readiness">
                  {readinessItems(readiness).map((item) => (
                    <Badge key={item.key} tone={item.ok ? TONES.safe : TONES.waiting}>
                      {item.ok || !item.envVar ? item.label : `${item.label} · ${item.envVar}`}
                    </Badge>
                  ))}
                </div>

                {notice ? (
                  <p role="alert" className="callout attention air-transmissions-gap">
                    {notice.text}
                  </p>
                ) : null}

                <div className="row air-transmissions-actions">
                  <div className="air-transmissions-action">
                    <button
                      type="button"
                      className="btn btn-secondary"
                      onClick={onValidate}
                      disabled={working || !actions.validate.enabled}
                    >
                      {busy === "validate" ? "Validating…" : "Validate"}
                    </button>
                    <ActionReason action={actions.validate} />
                  </div>
                  <div className="air-transmissions-action">
                    <button
                      type="button"
                      className="btn btn-secondary"
                      onClick={onPreview}
                      disabled={working || !actions.preview.enabled}
                    >
                      {busy === "preview" ? "Previewing…" : "Preview"}
                    </button>
                    <ActionReason action={actions.preview} />
                  </div>
                  <div className="air-transmissions-action">
                    <button
                      type="button"
                      className="btn btn-primary"
                      onClick={() => setConfirmOpen(true)}
                      disabled={working || !actions.submit.enabled}
                    >
                      {busy === "submit" ? "Sending…" : "Submit to AATS"}
                    </button>
                    <ActionReason action={actions.submit} />
                  </div>
                  <div className="air-transmissions-action">
                    <button
                      type="button"
                      className="btn btn-secondary"
                      onClick={onCheckStatus}
                      disabled={working || !actions.checkStatus.enabled}
                    >
                      {busy === "status" ? "Checking…" : "Check status"}
                    </button>
                    <ActionReason action={actions.checkStatus} />
                  </div>
                </div>

                {confirmOpen ? (
                  <form
                    className="air-transmissions-confirm"
                    onSubmit={(event) => {
                      event.preventDefault();
                      if (!working && submitArmed(selected, confirmTyped)) onSubmit();
                    }}
                  >
                    <label
                      htmlFor="air-transmissions-confirm"
                      className="air-transmissions-confirm-prompt"
                    >
                      Type <code>{confirmCode(selected)}</code> to send this UTID to IRS AATS
                    </label>
                    <input
                      id="air-transmissions-confirm"
                      className="air-transmissions-confirm-input"
                      type="text"
                      name="confirm"
                      autoComplete="off"
                      spellCheck={false}
                      autoFocus
                      value={confirmTyped}
                      onChange={(event) => setConfirmTyped(event.target.value)}
                    />
                    <button
                      type="submit"
                      className="btn btn-primary"
                      disabled={working || !submitArmed(selected, confirmTyped)}
                    >
                      {busy === "submit" ? "Sending…" : "Send"}
                    </button>
                    <button
                      type="button"
                      className="btn btn-secondary"
                      onClick={() => {
                        setConfirmOpen(false);
                        setConfirmTyped("");
                      }}
                      disabled={working}
                    >
                      Cancel
                    </button>
                  </form>
                ) : null}
              </section>

              <section className="panel-section" aria-label="What we sent">
                <div className="air-transmissions-sent-head">
                  <h2 className="section-label">What we sent</h2>
                  {activeTab.kind === "xml" ? (
                    <button type="button" className="btn-mini" onClick={() => setPacked((v) => !v)}>
                      {packed ? "Show pretty XML" : "Show as packed"}
                    </button>
                  ) : null}
                </div>
                <div
                  ref={tabListRef}
                  className="tabs"
                  role="tablist"
                  onKeyDown={onTabListKeyDown}
                >
                  {SENT_TABS.map((item) => {
                    const active = item.id === tab;
                    return (
                      <button
                        key={item.id}
                        id={`tab-${item.id}`}
                        type="button"
                        role="tab"
                        className="tab"
                        aria-selected={active}
                        aria-controls={`panel-${item.id}`}
                        tabIndex={active ? 0 : -1}
                        onClick={() => setTab(item.id)}
                      >
                        {item.label}
                      </button>
                    );
                  })}
                </div>
                <div
                  className="air-transmissions-tabpanel"
                  role="tabpanel"
                  id={`panel-${activeTab.id}`}
                  aria-labelledby={`tab-${activeTab.id}`}
                  tabIndex={0}
                >
                  {activeDoc.missingReason ? (
                    <p className="callout waiting">{activeDoc.missingReason}</p>
                  ) : activeDoc.stderr ? (
                    <p className="callout attention">{activeDoc.stderr}</p>
                  ) : activeDoc.text && activeTab.kind === "xml" ? (
                    <>
                      <div className="actions end">
                        {activeTab.download ? (
                          <a
                            href={downloadHref(selected.id, activeTab.download)}
                            className="btn-mini"
                          >
                            Download exact bytes
                          </a>
                        ) : null}
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
                  ) : activeDoc.text ? (
                    <>
                      <div className="actions end">
                        {activeTab.download ? (
                          <a
                            href={downloadHref(selected.id, activeTab.download)}
                            className="btn-mini"
                          >
                            Download exact bytes
                          </a>
                        ) : null}
                        <button
                          type="button"
                          className="btn-mini"
                          onClick={() => copyText(activeDoc.text)}
                        >
                          {copied ? "Copied" : "Copy"}
                        </button>
                      </div>
                      <div className="code tall">
                        <pre>{activeDoc.text}</pre>
                      </div>
                    </>
                  ) : (
                    <p className="callout waiting">Not produced yet.</p>
                  )}
                </div>
              </section>
            </>
          )}
        </article>
      </div>
    );
  }

  return body;
}
