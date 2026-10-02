// Pure AIR run model shared by the server store and the client pages.
// A run is one directory of artifacts; its state is derived from which artifacts exist.

export const RUN_ID_RE = /^[0-9]{8}T[0-9]{6}Z-[a-z0-9-]{1,40}-[0-9a-f]{6}$/;

export function isRunId(value) {
  return typeof value === "string" && RUN_ID_RE.test(value);
}

// Every file a run directory may hold. The key is the only name callers use.
// `download` marks the files an operator may fetch byte-exact (the AATS UI upload takes form + manifest).
export const RUN_FILES = {
  form: { path: "form.xml", label: "Form data", kind: "xml", download: true },
  manifest: { path: "manifest.xml", label: "Manifest", kind: "xml", download: true },
  session: { path: "session.json", label: "Session", kind: "json", download: false },
  compose: { path: "compose.json", label: "Compose record", kind: "json", download: false },
  validate: { path: "validate.json", label: "Validation", kind: "json", download: false },
  previewMime: { path: "preview/request.mime", label: "Request MIME", kind: "text", download: false },
  previewHeaders: { path: "preview/request.headers.txt", label: "Request headers", kind: "text", download: false },
  submit: { path: "submit.json", label: "Submit result", kind: "json", download: false },
  submitResponse: { path: "submit.response.xml", label: "Submit response", kind: "xml", download: false },
};

export const STATUS_CHECK_RE = /^status-[0-9]{8}T[0-9]{9}Z-[0-9a-f]{4}\.json$/;

// stamp: yyyymmddThhmmssmmmZ, suffix: 4 hex chars, so two checks in one second never collide.
export function statusCheckFiles(stamp, suffix) {
  const base = `status-${stamp}-${suffix}`;
  return { record: `${base}.json`, response: `${base}.response.xml` };
}

// Java CLI exit codes (redesign/air-a2a/java/README.md).
export const EXIT = { TOOK: 0, CHANNEL_FAILURE: 1, LOCAL_GUARD: 2, REJECTED_OR_NOT_FOUND: 3 };

// One row per state a run can be in. `sent` means the bytes may have reached IRS, so the UTID is spent.
export const STAGES = {
  composed: { label: "Composed, not sent", tone: "waiting", sent: false, next: "Validate, preview, then submit to AATS." },
  blocked: { label: "Blocked before send", tone: "attention", sent: false, next: "A local guard stopped the send. Read the message, fix the setup, submit again." },
  sending: { label: "Sending", tone: "waiting", sent: true, next: "A submit is running. If it never finishes, compose a new run; this UTID may have reached IRS." },
  fault: { label: "Failed, may have reached IRS", tone: "attention", sent: true, next: "Read the message and any TPE code. Compose a new run before sending again." },
  PROCESSING: { label: "Processing", tone: "waiting", sent: true, next: "Wait 10 minutes, then check status. Do not resubmit." },
  ACCEPTED: { label: "Accepted", tone: "safe", sent: true, next: "Record the Receipt ID. The scenario is done." },
  ACCEPTED_WITH_ERRORS: { label: "Accepted with errors", tone: "attention", sent: true, next: "Read the errors, fix the data, follow the AATS correction steps." },
  PARTIALLY_ACCEPTED: { label: "Partially accepted", tone: "attention", sent: true, next: "Read the errors, fix the data, follow the AATS correction steps." },
  REJECTED: { label: "Rejected", tone: "attention", sent: true, next: "Read the errors, fix the data, compose a new run, submit it." },
  NOT_FOUND: { label: "Not found", tone: "attention", sent: true, next: "Check the Receipt ID and that this run's session was the one submitted." },
};

// TransmissionStatus.java, the only values the Java CLI prints in `status`.
export const IRS_STATUSES = ["PROCESSING", "ACCEPTED", "ACCEPTED_WITH_ERRORS", "PARTIALLY_ACCEPTED", "REJECTED", "NOT_FOUND"];

export function stageInfo(stage) {
  return STAGES[stage] || STAGES.composed;
}

// A CLI record is { exitCode, result, stderr, at } or the pre-spawn marker { pending: true, at }.
// An IRS answer is a record whose exit code says IRS replied and whose status is one we know.
export function irsAnswer(record) {
  if (!record || record.pending) return null;
  if (record.exitCode !== EXIT.TOOK && record.exitCode !== EXIT.REJECTED_OR_NOT_FOUND) return null;
  const status = record.result && record.result.status;
  return IRS_STATUSES.includes(status) ? status : null;
}

function submitStage(submit) {
  if (!submit) return "composed";
  if (submit.pending) return "sending";
  if (submit.exitCode === EXIT.LOCAL_GUARD) return "blocked";
  return irsAnswer(submit) || "fault";
}

function byTime(a, b) {
  return (Date.parse(a.at) || 0) - (Date.parse(b.at) || 0);
}

// files: { session, compose, validate, submit, statusChecks: [record...] } parsed JSON, missing = null.
// present: Set of RUN_FILES keys that exist on disk.
export function deriveRun(id, files, present) {
  const session = files.session || {};
  const compose = files.compose || {};
  const submit = files.submit || null;
  const statusChecks = [...(files.statusChecks || [])].sort(byTime);

  // Only an IRS answer moves the stage; a failed or blocked check stays in history and changes nothing.
  let stage = submitStage(submit);
  let answer = irsAnswer(submit) ? submit : null;
  for (const check of statusChecks) {
    if (stage === "PROCESSING" && irsAnswer(check)) {
      stage = irsAnswer(check);
      answer = check;
    }
  }

  const testFileCd = session.testFileCd || null;
  return {
    id,
    scenarioId: compose.scenarioId || null,
    composedAt: compose.composedAt || null,
    utid: session.utid || null,
    fileName: session.fileName || null,
    checksum: session.checksum || null,
    byteSize: session.byteSize ?? null,
    paymentYear: session.paymentYear ?? null,
    priorYearData: session.priorYearData ?? null,
    testFileCd,
    environment: testFileCd === "T" ? "aats" : testFileCd === "P" ? "production" : "unknown",
    transmissionType: session.transmissionType || null,
    payeeCount: session.payeeCount ?? null,
    tcc: (session.transmitter && session.transmitter.tcc) || null,
    transmitterName: (session.transmitter && session.transmitter.transmitterName) || null,
    validation: files.validate || null,
    previewed: present.has("previewMime"),
    submit,
    statusChecks,
    receiptId: (submit && submit.result && submit.result.receiptId) || null,
    answerAt: answer ? answer.at : null,
    errors: (answer && answer.result && answer.result.errors) || [],
    stage,
    files: Object.keys(RUN_FILES).filter((key) => present.has(key)),
  };
}

export function canSubmit(run) {
  return run.testFileCd === "T"
    && Boolean(run.utid)
    && run.files.includes("form")
    && run.files.includes("session")
    && !STAGES[run.stage].sent;
}

// The operator types the last 6 characters of the UTID's UUID part to arm a live submit.
export const CONFIRM_LENGTH = 6;

export function confirmCode(run) {
  if (!run.utid) return null;
  const uuid = run.utid.split(":")[0];
  return uuid.length >= CONFIRM_LENGTH ? uuid.slice(-CONFIRM_LENGTH) : null;
}

export function confirmMatches(run, typed) {
  const code = confirmCode(run);
  return typeof typed === "string" && code != null && typed.trim().toLowerCase() === code.toLowerCase();
}

// TESTING.md: ask again only while PROCESSING, and only 10 minutes after the last IRS answer.
export const STATUS_WAIT_MS = 10 * 60 * 1000;

export function statusCheckOpensAt(run) {
  const at = run.answerAt || (run.submit && run.submit.at) || null;
  const last = at ? Date.parse(at) : NaN;
  return Number.isNaN(last) ? null : last + STATUS_WAIT_MS;
}

export function canCheckStatus(run, nowMs) {
  if (run.stage !== "PROCESSING" || !run.receiptId) return false;
  const opensAt = statusCheckOpensAt(run);
  return opensAt == null || nowMs >= opensAt;
}
