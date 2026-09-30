import fs from "node:fs";
import path from "node:path";

export const FORMAT_CONTENT_TYPES = {
  ".xml": "application/xml; charset=utf-8",
  ".tsv": "text/tab-separated-values; charset=utf-8",
  ".yml": "text/plain; charset=utf-8",
  ".pdf": "application/pdf",
};

const ALLOWED_STATUS_STATES = new Set(["accepted", "ready"]);

export function repoRoot() {
  return process.env.MEF_REPO_ROOT || path.resolve(process.cwd(), "..");
}

function registryPath() {
  return path.join(repoRoot(), "test-scenarios", "ats-ty2026", "scenarios.json");
}

function scenarioDirPath(dir) {
  return path.join(repoRoot(), "test-scenarios", "ats-ty2026", dir);
}

function validateRegistry(registry) {
  const scenarios = registry && registry.scenarios;
  if (!Array.isArray(scenarios)) {
    throw new Error("scenarios.json: scenarios is missing");
  }
  for (const scenario of scenarios) {
    const label =
      scenario && scenario.number != null ? `scenario ${scenario.number}` : "scenario (unknown)";
    if (scenario == null || scenario.number == null) {
      throw new Error(`${label}: missing number`);
    }
    if (!scenario.dir) {
      throw new Error(`${label}: missing dir`);
    }
    if (!scenario.title) {
      throw new Error(`${label}: missing title`);
    }
    if (!scenario.pdf) {
      throw new Error(`${label}: missing pdf`);
    }
    const state = scenario.status && scenario.status.state;
    if (!ALLOWED_STATUS_STATES.has(state)) {
      throw new Error(`${label}: missing or invalid status.state`);
    }
  }
  return registry;
}

export function loadRegistry() {
  const filePath = registryPath();
  let raw;
  try {
    raw = fs.readFileSync(filePath, "utf8");
  } catch (err) {
    if (err && err.code === "ENOENT") {
      throw new Error(
        `${filePath} not found. Run from c2s-web or set MEF_REPO_ROOT.`
      );
    }
    throw err;
  }
  return validateRegistry(JSON.parse(raw));
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

function asScenarioNumber(value) {
  if (typeof value === "number") {
    if (!Number.isInteger(value) || value < 1) return null;
    return value;
  }
  if (typeof value !== "string" || !/^[1-9][0-9]*$/.test(value)) return null;
  return parseInt(value, 10);
}

export function sendClientId(scenario) {
  return `ats-scenario-${scenario.number}-${scenario.slug}`;
}

export function loadScenario(number) {
  const num = asScenarioNumber(number);
  if (num == null) return null;
  const registry = loadRegistry();
  const scenario = registry.scenarios.find((item) => item.number === num);
  if (!scenario) return null;
  const dirPath = scenarioDirPath(scenario.dir);
  const formats = registry.formats.map((format) => ({
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

export function resolveScenarioFile(number, kind) {
  if (typeof kind !== "string") return null;
  const num = asScenarioNumber(number);
  if (num == null) return null;
  const registry = loadRegistry();
  const scenario = registry.scenarios.find((item) => item.number === num);
  if (!scenario) return null;
  let fileName = null;
  if (kind === "pdf") {
    fileName = scenario.pdf;
  } else {
    const format = registry.formats.find((item) => item.id === kind);
    if (!format) return null;
    fileName = format.file;
  }
  const contentType = contentTypeForFileName(fileName);
  if (!contentType) return null;
  return {
    absPath: path.join(scenarioDirPath(scenario.dir), fileName),
    contentType,
    fileName,
  };
}

export function resolveLegacyFile(id) {
  if (typeof id !== "string") return null;
  const registry = loadRegistry();
  const item = (registry.legacy || []).find((entry) => entry.id === id);
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
  const item = (registry.legacy || []).find((entry) => entry.id === id);
  if (!item) return null;
  const resolved = resolveLegacyFile(id);
  const exists = Boolean(resolved && fs.existsSync(resolved.absPath));
  let content = null;
  if (exists && item.kind !== "pdf") {
    content = fs.readFileSync(resolved.absPath, "utf8");
  }
  return { item, resolved, content, hasFile: exists };
}
