import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

export const FORMAT_CONTENT_TYPES = {
  ".xml": "application/xml; charset=utf-8",
  ".tsv": "text/tab-separated-values; charset=utf-8",
  ".yml": "text/plain; charset=utf-8",
  ".pdf": "application/pdf",
};

const ALLOWED_STATUS_STATES = new Set(["accepted", "ready", "partial"]);

// id is digits, or a 3-digit form number, a hyphen, and digits. The slug starts with a letter.
const SCENARIO_DIR = /^scenario-([0-9]{3}-[0-9]+|[0-9]+)-([A-Za-z][A-Za-z0-9-]*)$/;
const CANONICAL_ID = /^(?:[1-9][0-9]{2}-[1-9][0-9]*|[1-9][0-9]*)$/;

export function repoRoot() {
  if (process.env.MEF_REPO_ROOT) return process.env.MEF_REPO_ROOT;
  // Next bundles this file under c2s-web/.next-build, so a fixed ".." count is wrong there.
  // Walk up from this module until the repo that holds both trees.
  let dir = path.dirname(fileURLToPath(import.meta.url));
  while (true) {
    if (
      fs.existsSync(path.join(dir, "test-scenarios")) &&
      fs.existsSync(path.join(dir, "c2s-web"))
    ) {
      return dir;
    }
    const parent = path.dirname(dir);
    if (parent === dir) {
      throw new Error("Cannot resolve the repo root from scenarioData.js. Set MEF_REPO_ROOT.");
    }
    dir = parent;
  }
}

function parseScenarioDirName(name) {
  const match = SCENARIO_DIR.exec(name);
  if (!match) return null;
  return { id: match[1], slug: match[2] };
}

/** Plain numeric ids (1, 2, 3, 4) sort before form-prefixed ids (940-1). */
function compareIds(left, right) {
  const leftPrefixed = left.includes("-");
  const rightPrefixed = right.includes("-");
  if (leftPrefixed !== rightPrefixed) return leftPrefixed ? 1 : -1;
  const [leftMajor, leftMinor] = left.split("-");
  const [rightMajor, rightMinor] = right.split("-");
  const major = Number(leftMajor) - Number(rightMajor);
  if (major !== 0 || leftMinor == null) return major;
  return Number(leftMinor) - Number(rightMinor);
}

function readJson(filePath) {
  let raw;
  try {
    raw = fs.readFileSync(filePath, "utf8");
  } catch (err) {
    if (err && err.code === "ENOENT") {
      throw new Error(`${filePath} not found. Set MEF_REPO_ROOT.`);
    }
    throw err;
  }
  return JSON.parse(raw);
}

function validateScenario(entry, id) {
  const label = `scenario ${id}`;
  if (entry == null || typeof entry !== "object") {
    throw new Error(`${label}: scenario.json is missing`);
  }
  if (!entry.title) {
    throw new Error(`${label}: missing title`);
  }
  if (!entry.pdf) {
    throw new Error(`${label}: missing pdf`);
  }
  const state = entry.status && entry.status.state;
  if (!ALLOWED_STATUS_STATES.has(state)) {
    throw new Error(`${label}: missing or invalid status.state`);
  }
}

function loadBundles() {
  const root = path.join(repoRoot(), "test-scenarios");
  let tyNames;
  try {
    tyNames = fs.readdirSync(root);
  } catch (err) {
    if (err && err.code === "ENOENT") {
      throw new Error(`${root} not found. Set MEF_REPO_ROOT.`);
    }
    throw err;
  }
  const bundles = [];
  for (const tyName of tyNames.sort()) {
    const tyPath = path.join(root, tyName);
    if (!fs.statSync(tyPath).isDirectory()) continue;
    const scenarioNames = fs
      .readdirSync(tyPath, { withFileTypes: true })
      .filter((entry) => entry.isDirectory() && entry.name.startsWith("scenario-"))
      .map((entry) => entry.name);
    if (scenarioNames.length === 0) continue;
    const formatsPath = path.join(tyPath, "formats.json");
    const formatsDoc = readJson(formatsPath);
    if (!Array.isArray(formatsDoc.formats)) {
      throw new Error(`${formatsPath}: formats is missing`);
    }
    const scenarios = scenarioNames.map((dirName) => {
      const parsed = parseScenarioDirName(dirName);
      if (!parsed) {
        throw new Error(`${path.join(tyPath, dirName)} does not match scenario-<id>-<slug>`);
      }
      const entry = readJson(path.join(tyPath, dirName, "scenario.json"));
      validateScenario(entry, parsed.id);
      return {
        ...entry,
        id: parsed.id,
        slug: parsed.slug,
        dir: dirName,
        tyDir: tyName,
      };
    });
    bundles.push({
      tyDir: tyName,
      taxYear: formatsDoc.taxYear,
      returnVersion: formatsDoc.returnVersion,
      formats: formatsDoc.formats,
      legacy: formatsDoc.legacy || [],
      scenarios,
    });
  }
  return bundles;
}

export function loadRegistry() {
  const bundles = loadBundles();
  const scenarios = bundles
    .flatMap((bundle) => bundle.scenarios)
    .sort((left, right) => compareIds(left.id, right.id));
  return { bundles, scenarios };
}

export function parseLineMapping(tsvText) {
  const rows = [];
  let skippedHeader = false;
  for (const line of String(tsvText || "").split(/\r?\n/)) {
    if (!line.trim()) continue;
    if (!skippedHeader) {
      skippedHeader = true;
      continue;
    }
    const parts = line.split("\t");
    rows.push({
      document: parts[0] ?? "",
      formLine: parts[1] ?? "",
      elementPath: parts[2] ?? "",
      value: parts[3] ?? "",
      pdfPage: parts[4] ?? "",
    });
  }
  return rows;
}

export function groupByDocument(rows) {
  const groups = [];
  const indexByDocument = new Map();
  for (const row of rows) {
    let group = indexByDocument.get(row.document);
    if (!group) {
      group = { document: row.document, rows: [] };
      indexByDocument.set(row.document, group);
      groups.push(group);
    }
    group.rows.push(row);
  }
  return groups;
}

function readUtf8IfPresent(absPath) {
  try {
    return fs.readFileSync(absPath, "utf8");
  } catch (err) {
    if (err && err.code === "ENOENT") return null;
    throw err;
  }
}

function asScenarioId(value) {
  if (typeof value === "number") {
    if (!Number.isInteger(value) || value < 1) return null;
    return String(value);
  }
  if (typeof value !== "string" || !CANONICAL_ID.test(value)) return null;
  return value;
}

export function sendClientId(scenario) {
  return `ats-scenario-${scenario.id}-${scenario.slug}`;
}

function scenarioDirPath(scenario) {
  return path.join(repoRoot(), "test-scenarios", scenario.tyDir, scenario.dir);
}

function bundleFor(registry, scenario) {
  return registry.bundles.find((bundle) => bundle.tyDir === scenario.tyDir);
}

export function loadScenario(rawId) {
  const id = asScenarioId(rawId);
  if (id == null) return null;
  const registry = loadRegistry();
  const scenario = registry.scenarios.find((item) => item.id === id);
  if (!scenario) return null;
  const bundle = bundleFor(registry, scenario);
  const dirPath = scenarioDirPath(scenario);
  const formats = bundle.formats.map((format) => ({
    ...format,
    content: readUtf8IfPresent(path.join(dirPath, format.file)),
  }));
  const mapping = formats.find((format) => format.id === "lineMapping");
  const lineMapping = mapping && mapping.content ? parseLineMapping(mapping.content) : [];
  return {
    scenario,
    formats,
    lineMapping,
    hasPdf: fs.existsSync(path.join(dirPath, scenario.pdf)),
  };
}

function contentTypeForFileName(fileName) {
  const ext = path.extname(fileName).toLowerCase();
  return FORMAT_CONTENT_TYPES[ext] || null;
}

export function resolveScenarioFile(rawId, kind) {
  if (typeof kind !== "string") return null;
  const id = asScenarioId(rawId);
  if (id == null) return null;
  const registry = loadRegistry();
  const scenario = registry.scenarios.find((item) => item.id === id);
  if (!scenario) return null;
  const bundle = bundleFor(registry, scenario);
  let fileName = null;
  if (kind === "pdf") {
    fileName = scenario.pdf;
  } else {
    const format = bundle.formats.find((item) => item.id === kind);
    if (!format) return null;
    fileName = format.file;
  }
  const contentType = contentTypeForFileName(fileName);
  if (!contentType) return null;
  return {
    absPath: path.join(scenarioDirPath(scenario), fileName),
    contentType,
    fileName,
  };
}

function legacyItems(registry) {
  return registry.bundles.flatMap((bundle) => bundle.legacy);
}

export function resolveLegacyFile(id) {
  if (typeof id !== "string") return null;
  const registry = loadRegistry();
  const item = legacyItems(registry).find((entry) => entry.id === id);
  if (!item) return null;
  const fileName = path.basename(item.path);
  const contentType = contentTypeForFileName(fileName);
  if (!contentType) return null;
  return {
    absPath: path.join(repoRoot(), item.path),
    contentType,
    fileName,
  };
}

export function loadLegacy(id) {
  const registry = loadRegistry();
  const item = legacyItems(registry).find((entry) => entry.id === id);
  if (!item) return null;
  const resolved = resolveLegacyFile(id);
  const exists = Boolean(resolved && fs.existsSync(resolved.absPath));
  let content = null;
  if (exists && item.kind !== "pdf") {
    content = fs.readFileSync(resolved.absPath, "utf8");
  }
  return { item, resolved, content, hasFile: exists };
}
