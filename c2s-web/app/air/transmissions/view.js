import { prettyXml } from "../../send-inspector/prettyXml.js";
import { isUnreachableError } from "../../send-inspector/api.js";
import {
  canCheckStatus,
  canSubmit,
  confirmCode,
  confirmMatches,
  irsAnswer,
  stageInfo,
  statusCheckOpensAt,
  STAGES,
} from "../lib/runModel.js";

export const SENT_TABS = [
  { id: "form", label: "Form data", docKey: "form", kind: "xml", download: "form" },
  { id: "manifest", label: "Manifest", docKey: "manifest", kind: "xml", download: "manifest" },
  { id: "session", label: "Session", docKey: "session", kind: "text" },
  { id: "previewMime", label: "Request MIME", docKey: "previewMime", kind: "text" },
  { id: "previewHeaders", label: "Request headers", docKey: "previewHeaders", kind: "text" },
  { id: "submitResponse", label: "Submit response", docKey: "submitResponse", kind: "xml" },
  { id: "statusResponse", label: "Status response", kind: "xml", fromStatusResponses: true },
];

const READINESS_FLAGS = [
  "repoRoot",
  "jar",
  "java",
  "xmllint",
  "pkcs12",
  "passwordEnvSet",
  "asid",
];

export const JAVA_READY_FLAGS = ["repoRoot", "jar", "java", "pkcs12", "passwordEnvSet", "asid"];
export const VALIDATE_READY_FLAGS = ["repoRoot", "xmllint"];
export const STDERR_PREVIEW_LIMIT = 400;

export const ACTION_NOTICES = {
  NOT_SUBMITTABLE: "This run cannot be submitted to AATS.",
  CONFIRM_MISMATCH: "That confirmation code does not match this UTID.",
  SUBMIT_IN_PROGRESS: "A submit is already in progress for this run.",
  STATUS_NOT_OPEN: "Status check is not open yet.",
  AIR_NOT_CONFIGURED: "AIR is not configured.",
  COMPOSE_FAILED: "Compose failed.",
};

export function formatValue(value) {
  if (value == null || value === "") return "—";
  return String(value);
}

export function formatLocalTime(value) {
  if (value == null || value === "") return "—";
  const date = typeof value === "number" ? new Date(value) : new Date(value);
  if (Number.isNaN(date.getTime())) return String(value);
  return date.toLocaleString();
}

export function formatBytes(value) {
  if (value == null || value === "") return "—";
  const n = Number(value);
  if (!Number.isFinite(n)) return String(value);
  return n.toLocaleString("en-US");
}

export function formatPriorYear(value) {
  if (value == null || value === "") return "—";
  if (typeof value === "boolean") return value ? "yes" : "no";
  return String(value);
}

export function errorRows(errors) {
  if (!Array.isArray(errors)) return [];
  return errors.map((err) => ({
    code: (err && err.code) || "",
    text: (err && err.text) || "",
    xpath: (err && err.xpath) || "",
  }));
}

export function statusHistoryRows(run) {
  if (!run) return [];
  const records = [];
  if (run.submit) records.push(run.submit);
  for (const check of run.statusChecks || []) records.push(check);
  return records.map((record) => ({
    at: record.at || null,
    exitCode: record.pending || record.exitCode == null ? "—" : record.exitCode,
    status: irsAnswer(record) || "no answer",
    stderr: typeof record.stderr === "string" ? record.stderr : "",
  }));
}

export function previewStderr(text) {
  if (text == null || text === "") return { preview: "", full: "", truncated: false };
  const full = String(text);
  const truncated = full.length > STDERR_PREVIEW_LIMIT;
  return {
    preview: truncated ? full.slice(0, STDERR_PREVIEW_LIMIT) : full,
    full,
    truncated,
  };
}

export function validationRows(validation) {
  if (!validation || !Array.isArray(validation.files)) return [];
  return validation.files.map((file) => ({
    path: file.path || "",
    kind: file.kind || "",
    result: file.ok ? "PASS" : "FAIL",
    errors: Array.isArray(file.errors) ? file.errors : [],
  }));
}

export function isReady(readiness) {
  return Boolean(readiness && READINESS_FLAGS.every((key) => readiness[key]));
}

export function missingReadyReason(readiness, flags) {
  const items = readinessItems(readiness);
  const byKey = Object.fromEntries(items.map((item) => [item.key, item]));
  for (const key of flags) {
    const item = byKey[key];
    if (!item || item.ok) continue;
    return item.envVar ? `Set ${item.envVar}.` : `${item.label} is not available.`;
  }
  return null;
}

export function readinessItems(readiness) {
  const env = (readiness && readiness.env) || {};
  return [
    { key: "repoRoot", label: "repoRoot", ok: Boolean(readiness && readiness.repoRoot), envVar: "AIR_REPO_ROOT" },
    { key: "jar", label: "jar", ok: Boolean(readiness && readiness.jar), envVar: "AIR_JAR" },
    { key: "java", label: "java", ok: Boolean(readiness && readiness.java), envVar: "AIR_JAVA_HOME" },
    { key: "xmllint", label: "xmllint", ok: Boolean(readiness && readiness.xmllint), envVar: null },
    {
      key: "pkcs12",
      label: "pkcs12",
      ok: Boolean(readiness && readiness.pkcs12),
      envVar: env.pkcs12Var || "AIR_PKCS12",
    },
    {
      key: "passwordEnvSet",
      label: "password env",
      ok: Boolean(readiness && readiness.passwordEnvSet),
      envVar: env.passwordEnv || "AIR_P12_PASSWORD",
    },
    {
      key: "asid",
      label: "ASID",
      ok: Boolean(readiness && readiness.asid),
      envVar: env.asidVar || "AIR_ASID",
    },
  ];
}

function submitDisabledReason(run) {
  if (!run) return "No run selected.";
  if (canSubmit(run)) return null;
  if (!run.files || !run.files.includes("form") || !run.files.includes("session")) {
    return "Form or session is missing.";
  }
  if (!run.utid) return "This run has no UTID.";
  if (run.testFileCd !== "T") return "Only AATS (test file code T) can be submitted.";
  if (STAGES[run.stage] && STAGES[run.stage].sent) return "This run was already sent.";
  return "This run cannot be submitted.";
}

function statusDisabledReason(run, nowMs) {
  if (!run) return "No run selected.";
  if (run.stage !== "PROCESSING") return "Status can be checked only while IRS is processing.";
  if (!run.receiptId) return "No Receipt ID.";
  if (canCheckStatus(run, nowMs)) return null;
  const opensAt = statusCheckOpensAt(run);
  return opensAt == null ? "Status check is not open yet." : `opens at ${formatLocalTime(opensAt)}`;
}

export function actionState(run, readiness, nowMs) {
  const composed = Boolean(run);
  const validateReason = composed ? missingReadyReason(readiness, VALIDATE_READY_FLAGS) : "No run selected.";
  const previewReason = composed ? missingReadyReason(readiness, JAVA_READY_FLAGS) : "No run selected.";
  const submitReadyReason = missingReadyReason(readiness, JAVA_READY_FLAGS);
  const statusReadyReason = missingReadyReason(readiness, JAVA_READY_FLAGS);
  const submitDomainReason = submitDisabledReason(run);
  const statusDomainReason = statusDisabledReason(run, nowMs);
  return {
    validate: {
      enabled: composed && !validateReason,
      reason: validateReason,
    },
    preview: {
      enabled: composed && !previewReason,
      reason: previewReason,
    },
    submit: {
      enabled: composed && canSubmit(run) && !submitReadyReason,
      reason: submitDomainReason || submitReadyReason,
    },
    checkStatus: {
      enabled: composed && canCheckStatus(run, nowMs) && !statusReadyReason,
      reason: statusDomainReason || statusReadyReason,
      opensAt: composed ? statusCheckOpensAt(run) : null,
    },
  };
}

export function submitArmed(run, typed) {
  return confirmMatches(run, typed);
}

export function confirmPrompt(run) {
  const code = run ? confirmCode(run) : null;
  return code ? `Type ${code} to send this UTID to IRS AATS` : null;
}

export function runStage(run) {
  return stageInfo(run && run.stage);
}

export function displayStage(run, busyKind) {
  if (busyKind === "submit") return stageInfo("sending");
  return runStage(run);
}

export function xmlForDisplay(xml, packed) {
  if (!xml) return "";
  return packed ? xml : prettyXml(xml);
}

export function latestStatusResponse(statusResponses) {
  if (!Array.isArray(statusResponses) || statusResponses.length === 0) return null;
  return [...statusResponses].sort((a, b) => (Date.parse(a.at) || 0) - (Date.parse(b.at) || 0)).at(-1);
}

export function displayDoc(tab, documents, statusResponses) {
  if (tab && tab.fromStatusResponses) {
    const latest = latestStatusResponse(statusResponses);
    if (!latest) return { text: null, missingReason: "Not produced yet.", stderr: null };
    if (latest.text) return { text: latest.text, missingReason: null, stderr: null };
    if (latest.stderr) return { text: null, missingReason: null, stderr: latest.stderr };
    return { text: null, missingReason: "Not produced yet.", stderr: null };
  }
  const doc = documents && tab && documents[tab.docKey];
  if (!doc) return { text: null, missingReason: "Not produced yet.", stderr: null };
  if (doc.missingReason) return { text: null, missingReason: doc.missingReason, stderr: null };
  if (doc.text) return { text: doc.text, missingReason: null, stderr: null };
  return { text: null, missingReason: "Not produced yet.", stderr: null };
}

export function downloadHref(runId, key) {
  return `/air/api/runs/${encodeURIComponent(runId)}/files/${key}`;
}

export function noticeText(err) {
  if (!err) return "Request failed.";
  if (err.code === "AIR_NOT_CONFIGURED" || err.code === "STATUS_NOT_OPEN") {
    return err.message || ACTION_NOTICES[err.code];
  }
  return ACTION_NOTICES[err.code] || err.message || "Request failed.";
}

export function noticeFrom(err) {
  if (!err) return { kind: "error", text: "Request failed." };
  if (isUnreachableError(err)) {
    return { kind: "error", text: "Cannot reach the AIR operator API." };
  }
  return { kind: "error", text: noticeText(err) };
}

export function statusOpensLabel(opensAtMs, nowMs) {
  if (opensAtMs == null || nowMs == null || nowMs >= opensAtMs) return null;
  return `opens at ${formatLocalTime(opensAtMs)}`;
}
