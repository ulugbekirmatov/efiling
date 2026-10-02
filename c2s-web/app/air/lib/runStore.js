import { randomBytes } from "node:crypto";
import fs from "node:fs/promises";
import path from "node:path";
import { airConfig } from "./airConfig.js";
import { redactDocument, redactMessage, redactSecrets } from "./redact.js";
import {
  EXIT,
  RUN_FILES,
  STATUS_CHECK_RE,
  deriveRun,
  isRunId,
  statusCheckFiles,
} from "./runModel.js";

const STAGING_DIR_NAME = ".staging";
const RUNS_DIR_MODE = 0o700;
const MISSING_REASON = "Not created yet.";
const MAX_SLUG_LENGTH = 40;
const SUBMIT_LOCK_NAME = "submit.lock";

const submitting = new Set();

function runsDir() {
  return airConfig().runsDir;
}

function invalidRunId() {
  const error = new Error("Invalid run id.");
  error.code = "INVALID_RUN_ID";
  return error;
}

function isInside(parent, child) {
  const relative = path.relative(parent, child);
  return relative !== "" && !relative.startsWith("..") && !path.isAbsolute(relative);
}

export function runDir(id) {
  if (!isRunId(id)) throw invalidRunId();
  const root = runsDir();
  const dir = path.join(root, id);
  if (!isInside(root, dir)) throw invalidRunId();
  return dir;
}

function runFilePath(id, key) {
  const dir = runDir(id);
  const file = path.join(dir, RUN_FILES[key].path);
  if (!isInside(dir, file)) throw invalidRunId();
  return file;
}

export function secretValues() {
  const { passwordEnv, pkcs12 } = airConfig();
  return [process.env[passwordEnv], pkcs12];
}

function redactDeep(value, secrets) {
  if (typeof value === "string") return redactMessage(value, secrets);
  if (Array.isArray(value)) return value.map((item) => redactDeep(item, secrets));
  if (value && typeof value === "object") {
    return Object.fromEntries(Object.entries(value).map(([key, item]) => [key, redactDeep(item, secrets)]));
  }
  return value;
}

function redactErrorList(errors, secrets) {
  if (!Array.isArray(errors)) return errors;
  return errors.map((error) => (error && typeof error === "object"
    ? { ...error, text: redactMessage(error.text, secrets), xpath: redactMessage(error.xpath, secrets) }
    : redactMessage(error, secrets)));
}

// A CLI record is { exitCode, result, stderr, at }; result.errors carry IRS text and xpaths.
function redactCliRecord(record, secrets) {
  if (!record || typeof record !== "object") return record;
  const redacted = { ...record, stderr: redactMessage(record.stderr, secrets) };
  if (record.result && typeof record.result === "object") {
    redacted.result = { ...record.result, errors: redactErrorList(record.result.errors, secrets) };
  }
  return redacted;
}

function redactValidation(record, secrets) {
  if (!record || !Array.isArray(record.files)) return record;
  return {
    ...record,
    files: record.files.map((file) => ({ ...file, errors: redactErrorList(file.errors, secrets) })),
  };
}

// Every CLI-derived JSON record the browser can see goes through here, keyed like RUN_FILES.
const RECORD_REDACTORS = {
  compose: redactDeep,
  validate: redactValidation,
  submit: redactCliRecord,
};

function redactRecord(key, record, secrets) {
  const redactor = RECORD_REDACTORS[key];
  return redactor && record ? redactor(record, secrets) : record;
}

function exists(file) {
  return fs.access(file).then(() => true, () => false);
}

function isMissing(error) {
  return error && error.code === "ENOENT";
}

async function readText(file) {
  try {
    return await fs.readFile(file, "utf8");
  } catch (error) {
    if (isMissing(error)) return null;
    throw error;
  }
}

async function readJson(file) {
  const text = await readText(file);
  if (text == null) return null;
  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}

async function writeJson(file, value) {
  const temp = `${file}.${randomBytes(4).toString("hex")}.tmp`;
  await fs.writeFile(temp, `${JSON.stringify(value, null, 2)}\n`, { mode: 0o600 });
  await fs.rename(temp, file);
}

async function listStatusRecordNames(dir) {
  try {
    const names = await fs.readdir(dir);
    return names.filter((name) => STATUS_CHECK_RE.test(name)).sort();
  } catch (error) {
    if (isMissing(error)) return [];
    throw error;
  }
}

export async function readRun(id) {
  const dir = runDir(id);
  const session = await readJson(path.join(dir, RUN_FILES.session.path));
  if (!session) return null;
  const [compose, validate, submit, statusNames] = await Promise.all([
    readJson(path.join(dir, RUN_FILES.compose.path)),
    readJson(path.join(dir, RUN_FILES.validate.path)),
    readJson(path.join(dir, RUN_FILES.submit.path)),
    listStatusRecordNames(dir),
  ]);
  const secrets = secretValues();
  const statusChecks = (await Promise.all(statusNames.map((name) => readJson(path.join(dir, name)))))
    .filter(Boolean)
    .map((record) => redactCliRecord(record, secrets));
  const present = new Set();
  for (const [key, entry] of Object.entries(RUN_FILES)) {
    if (await exists(path.join(dir, entry.path))) present.add(key);
  }
  const files = {
    session,
    compose: redactRecord("compose", compose, secrets),
    validate: redactRecord("validate", validate, secrets),
    submit: redactRecord("submit", submit, secrets),
    statusChecks,
  };
  return deriveRun(id, files, present);
}

export async function listRuns() {
  let names;
  try {
    names = await fs.readdir(runsDir());
  } catch (error) {
    if (isMissing(error)) return [];
    throw error;
  }
  const runs = await Promise.all(names.filter(isRunId).map((id) => readRun(id)));
  // The id starts with a fixed-width UTC stamp, so descending id order is newest first.
  return runs.filter(Boolean).sort((a, b) => (a.id < b.id ? 1 : a.id > b.id ? -1 : 0));
}

function prettyJsonOrText(text, key, kind, secrets) {
  if (kind !== "json") return text;
  let parsed;
  try {
    parsed = JSON.parse(text);
  } catch {
    return text;
  }
  return JSON.stringify(redactRecord(key, parsed, secrets), null, 2);
}

export async function readDocuments(id) {
  const dir = runDir(id);
  const secrets = secretValues();
  const documents = {};
  for (const [key, entry] of Object.entries(RUN_FILES)) {
    const raw = await readText(path.join(dir, entry.path));
    documents[key] = raw == null
      ? { text: null, missingReason: MISSING_REASON }
      : { text: redactDocument(prettyJsonOrText(raw, key, entry.kind, secrets)), missingReason: null };
  }
  const statusResponses = [];
  for (const name of await listStatusRecordNames(dir)) {
    const record = await readJson(path.join(dir, name));
    if (!record) continue;
    const response = await readText(path.join(dir, name.replace(/\.json$/, ".response.xml")));
    statusResponses.push({
      at: record.at || null,
      exitCode: record.exitCode ?? null,
      text: response == null ? null : redactDocument(response),
      stderr: redactMessage(record.stderr || "", secrets),
    });
  }
  statusResponses.sort((a, b) => (Date.parse(a.at) || 0) - (Date.parse(b.at) || 0));
  return { documents, statusResponses };
}

function pad(value, width) {
  return String(value).padStart(width, "0");
}

function utcStamp(date, withMillis) {
  return `${date.getUTCFullYear()}${pad(date.getUTCMonth() + 1, 2)}${pad(date.getUTCDate(), 2)}`
    + `T${pad(date.getUTCHours(), 2)}${pad(date.getUTCMinutes(), 2)}${pad(date.getUTCSeconds(), 2)}`
    + `${withMillis ? pad(date.getUTCMilliseconds(), 3) : ""}Z`;
}

export function newRunId(scenarioId, now) {
  const slug = String(scenarioId || "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .slice(0, MAX_SLUG_LENGTH)
    .replace(/-+$/g, "") || "run";
  return `${utcStamp(new Date(now), false)}-${slug}-${randomBytes(3).toString("hex")}`;
}

export async function stagingDir() {
  const staging = path.join(runsDir(), STAGING_DIR_NAME);
  await fs.mkdir(staging, { recursive: true, mode: RUNS_DIR_MODE });
  return fs.mkdtemp(path.join(staging, "run-"));
}

export async function commitComposedRun(id, tmpDir, composeRecord) {
  const target = runDir(id);
  const staging = path.join(runsDir(), STAGING_DIR_NAME);
  if (!isInside(staging, path.resolve(tmpDir))) throw new Error("Staging directory is outside the run store.");
  await writeJson(path.join(tmpDir, RUN_FILES.compose.path), composeRecord);
  await fs.rename(tmpDir, target);
}

export async function writeValidate(id, record) {
  await writeJson(runFilePath(id, "validate"), record);
}

// The exclusive lock makes read-check-write atomic across processes; a crash mid-replace leaves
// submit.lock behind, which refuses further submits until an operator removes it.
async function replaceGuardBlock(file, marker) {
  let lock;
  try {
    lock = await fs.open(path.join(path.dirname(file), SUBMIT_LOCK_NAME), "wx", 0o600);
  } catch (error) {
    if (error.code === "EEXIST") return false;
    throw error;
  }
  try {
    const existing = await readJson(file);
    if (!existing || existing.exitCode !== EXIT.LOCAL_GUARD) return false;
    await writeJson(file, marker);
    return true;
  } finally {
    await lock.close();
    await fs.rm(path.join(path.dirname(file), SUBMIT_LOCK_NAME), { force: true });
  }
}

export async function beginSubmit(id) {
  const file = runFilePath(id, "submit");
  if (submitting.has(id)) return false;
  submitting.add(id);
  const marker = { pending: true, at: new Date().toISOString() };
  try {
    let handle;
    try {
      handle = await fs.open(file, "wx", 0o600);
    } catch (error) {
      if (error.code !== "EEXIST") throw error;
      if (!(await replaceGuardBlock(file, marker))) {
        submitting.delete(id);
        return false;
      }
      return true;
    }
    try {
      await handle.writeFile(`${JSON.stringify(marker, null, 2)}\n`);
    } finally {
      await handle.close();
    }
    return true;
  } catch (error) {
    submitting.delete(id);
    throw error;
  }
}

function withRedactedStderr(record) {
  if (typeof record.stderr !== "string") return record;
  return { ...record, stderr: redactSecrets(record.stderr, secretValues()) };
}

export async function finishSubmit(id, record) {
  try {
    await writeJson(runFilePath(id, "submit"), withRedactedStderr(record));
  } finally {
    submitting.delete(id);
  }
}

export function statusCheckPaths(id, now) {
  const dir = runDir(id);
  const stamp = utcStamp(new Date(now), true);
  const files = statusCheckFiles(stamp, randomBytes(2).toString("hex"));
  return { recordPath: path.join(dir, files.record), responsePath: path.join(dir, files.response), stamp };
}

export async function writeStatusCheck(recordPath, record) {
  const resolved = path.resolve(recordPath);
  const dir = path.dirname(resolved);
  if (path.dirname(dir) !== runsDir() || !isRunId(path.basename(dir)) || !STATUS_CHECK_RE.test(path.basename(resolved))) {
    throw new Error("Status record path is outside the run store.");
  }
  await writeJson(resolved, withRedactedStderr(record));
}

export async function readRawFile(id, key) {
  if (!Object.hasOwn(RUN_FILES, key) || RUN_FILES[key].download !== true) return null;
  try {
    return await fs.readFile(runFilePath(id, key));
  } catch (error) {
    if (isMissing(error)) return null;
    throw error;
  }
}
