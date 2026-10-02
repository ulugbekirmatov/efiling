import { execFile } from "node:child_process";
import { createRequire } from "node:module";
import { readdir, readFile } from "node:fs/promises";
import path from "node:path";
import { promisify } from "node:util";
import { airConfig } from "./airConfig.js";
import { redactDocument } from "./redact.js";

const execFileAsync = promisify(execFile);
// webpack rewrites createRequire into its bundle require, which cannot load the AIR package by absolute path.
const requireFromHere = typeof __non_webpack_require__ === "function"
  ? __non_webpack_require__
  : createRequire(import.meta.url);

const SCENARIO_FILE_RE = /^(scenario-[0-9]+[a-z]?)\.json$/;
const SCENARIO_ID_RE = /^scenario-[0-9]+[a-z]?$/;
const SCENARIO_NUMBER_RE = /^scenario-([0-9]+)/;
const PDF_BUFFER_LIMIT = 64 * 1024 * 1024;
const UNZIP_TIMEOUT_MS = 30_000;

async function listPdfEntries(pdfZip) {
  try {
    const { stdout } = await execFileAsync("unzip", ["-Z1", pdfZip], {
      maxBuffer: PDF_BUFFER_LIMIT,
      timeout: UNZIP_TIMEOUT_MS,
    });
    return stdout.split("\n").filter(Boolean);
  } catch {
    return [];
  }
}

function pdfsForScenario(entries, id) {
  const number = SCENARIO_NUMBER_RE.exec(id)[1];
  const markers = [`ty2026 scenario ${number}-`, `ty2026 scenario ${number}c-`];
  return entries.filter((name) => markers.some((marker) => name.includes(marker)));
}

function offerStyleOf(forms1095C) {
  return forms1095C.some((form) => form.annualOfferOfCoverageCd) ? "annual" : "monthly";
}

function summarize(id, scenario, pdfs) {
  const forms1095C = scenario.forms1095C || [];
  const form1094C = scenario.form1094C || {};
  return {
    id,
    testScenarioId: scenario.testScenarioId,
    taxYear: scenario.taxYear,
    form1095CCount: forms1095C.length,
    otherAleMemberCount: (form1094C.otherAleMembers || []).length,
    offerStyle: offerStyleOf(forms1095C),
    pdfs,
  };
}

async function scenarioIds(scenariosDir) {
  let names;
  try {
    names = await readdir(scenariosDir);
  } catch (error) {
    if (error.code === "ENOENT") return [];
    throw error;
  }
  return names
    .map((name) => SCENARIO_FILE_RE.exec(name))
    .filter(Boolean)
    .map((match) => match[1])
    .sort((a, b) => a.localeCompare(b, undefined, { numeric: true }));
}

async function readScenarioJson(file) {
  return JSON.parse(await readFile(file, "utf8"));
}

export async function scenarioFile(id) {
  if (typeof id !== "string" || !SCENARIO_ID_RE.test(id)) return null;
  const { scenariosDir } = airConfig();
  const ids = await scenarioIds(scenariosDir);
  return ids.includes(id) ? path.join(scenariosDir, `${id}.json`) : null;
}

export async function listScenarios() {
  const { scenariosDir, pdfZip } = airConfig();
  const [ids, entries] = await Promise.all([scenarioIds(scenariosDir), listPdfEntries(pdfZip)]);
  return Promise.all(
    ids.map(async (id) =>
      summarize(id, await readScenarioJson(path.join(scenariosDir, `${id}.json`)), pdfsForScenario(entries, id)),
    ),
  );
}

export async function loadScenario(id) {
  const file = await scenarioFile(id);
  if (!file) return null;
  const { airRoot, transmitterConfig, pdfZip } = airConfig();
  const scenario = await readScenarioJson(file);
  const transmitter = await readScenarioJson(transmitterConfig);
  const { buildTransmission } = requireFromHere(path.join(airRoot, "lib", "transmission.js"));
  const built = buildTransmission(scenario, transmitter, new Date());
  const pdfs = pdfsForScenario(await listPdfEntries(pdfZip), id);
  return {
    summary: { ...summarize(id, scenario, pdfs), utid: built.utid, fileName: built.fileName },
    formXml: redactDocument(built.formXml),
    manifestXml: redactDocument(built.manifestXml),
  };
}

export async function readScenarioPdf(id, entryName) {
  const file = await scenarioFile(id);
  if (!file || typeof entryName !== "string") return null;
  const { pdfZip } = airConfig();
  const pdfs = pdfsForScenario(await listPdfEntries(pdfZip), id);
  if (!pdfs.includes(entryName)) return null;
  try {
    const { stdout } = await execFileAsync("unzip", ["-p", pdfZip, entryName], {
      encoding: "buffer",
      maxBuffer: PDF_BUFFER_LIMIT,
      timeout: UNZIP_TIMEOUT_MS,
    });
    return stdout;
  } catch {
    return null;
  }
}
