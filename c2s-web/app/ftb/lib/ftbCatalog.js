import { readFile } from "node:fs/promises";
import { ftbConfig } from "./ftbConfig.js";
import { maskKnownSsns, maskSsn, maskedDigits } from "../../lib/redact.js";
import { nodeRequire } from "../../lib/nodeRequire.js";

// A person's TIN is their SSN or ITIN. Only an EIN names a business and stays readable.
function isPersonalId(person) {
  return Boolean(person.id) && person.idLabel !== "EIN";
}

function scenarioSsns(scenario) {
  const fromPeople = scenario.summary.returns.flatMap((r) =>
    r.people.filter(isPersonalId).map((p) => String(p.id).replace(/\D/g, "")),
  );
  const xml = [scenario.preview.formXml, scenario.preview.answerKeyXml].filter(Boolean).join("");
  const fromXml = [...xml.matchAll(/<(?:[\w.-]+:)?SSN>(\d{9})<\/(?:[\w.-]+:)?SSN>/g)].map((m) => m[1]);
  return [...new Set([...fromPeople, ...fromXml])];
}

function redactScenario(scenario) {
  const ssns = scenarioSsns(scenario);
  const text = (value) => maskKnownSsns(value, ssns);
  const xml = (value) => (value ? text(maskSsn(value)) : value);
  return {
    ...scenario,
    narrative: scenario.narrative.map((line) => ({ ...line, text: text(line.text) })),
    summary: {
      transmittal: scenario.summary.transmittal,
      returns: scenario.summary.returns.map((r) => ({
        ...r,
        people: r.people.map((p) => (isPersonalId(p) ? { ...p, id: maskedDigits(p.id) } : p)),
      })),
    },
    checks: scenario.checks.map((check) => ({ ...check, details: check.details.map(text) })),
    preview: {
      ...scenario.preview,
      formXml: xml(scenario.preview.formXml),
      manifestXml: xml(scenario.preview.manifestXml),
      answerKeyXml: xml(scenario.preview.answerKeyXml),
    },
  };
}

// The ftb-fx model composes every scenario in dry run and reads the ledger with fs only. It never
// sends and never writes the ledger, so calling it per request is safe.
export function loadModel() {
  const { operatorPageScript, previewRoot } = ftbConfig();
  const { buildModel } = nodeRequire(operatorPageScript);
  const model = buildModel({ previewRoot });
  return { ...model, scenarios: model.scenarios.map(redactScenario) };
}

export function scenarioState(scenario) {
  if (scenario.ledger) return scenario.ledger.state;
  return scenario.blocked ? "blocked" : "not_sent";
}

export function listScenarios() {
  const { scenarios, ...meta } = loadModel();
  return {
    meta,
    scenarios: scenarios.map((scenario) => ({
      id: scenario.id,
      formKind: scenario.formKind,
      correctionOf: scenario.correctionOf,
      purpose: scenario.purpose,
      recordCount: scenario.recordIds.length,
      state: scenarioState(scenario),
      blocked: scenario.blocked,
      nextAction: scenario.nextAction,
      checksPassed: scenario.checks.filter((check) => check.ok).length,
      checksTotal: scenario.checks.length,
    })),
  };
}

export function loadScenario(id) {
  const model = loadModel();
  const scenario = model.scenarios.find((s) => s.id === id);
  if (!scenario) return null;
  const { scenarios, ...meta } = model;
  return { meta, scenario: { ...scenario, state: scenarioState(scenario) } };
}

export async function readTestingSpecPdf() {
  try {
    return await readFile(ftbConfig().testingSpecPdf);
  } catch (error) {
    if (error.code === "ENOENT") return null;
    throw error;
  }
}
