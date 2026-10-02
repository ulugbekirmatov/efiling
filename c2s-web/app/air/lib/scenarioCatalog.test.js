const { test } = require("node:test");
const assert = require("node:assert/strict");

test("listScenarios reports scenario-2 and scenario-3 with their measured counts", async () => {
  const { listScenarios } = await import("./scenarioCatalog.js");
  const scenarios = await listScenarios();
  const byId = Object.fromEntries(scenarios.map((scenario) => [scenario.id, scenario]));
  const summarize = ({ testScenarioId, taxYear, form1095CCount, otherAleMemberCount, offerStyle }) => ({
    testScenarioId,
    taxYear,
    form1095CCount,
    otherAleMemberCount,
    offerStyle,
  });
  assert.deepEqual(summarize(byId["scenario-2"]), {
    testScenarioId: "2-0",
    taxYear: "2026",
    form1095CCount: 2,
    otherAleMemberCount: 1,
    offerStyle: "annual",
  });
  assert.deepEqual(summarize(byId["scenario-3"]), {
    testScenarioId: "3-0",
    taxYear: "2026",
    form1095CCount: 1,
    otherAleMemberCount: 0,
    offerStyle: "monthly",
  });
});

test("listScenarios keeps only the PDFs that belong to each scenario", async () => {
  const { listScenarios } = await import("./scenarioCatalog.js");
  const byId = Object.fromEntries((await listScenarios()).map((scenario) => [scenario.id, scenario]));
  assert.deepEqual(byId["scenario-2"].pdfs, [
    "ty2026 scenario 2-0 f1094c.pdf",
    "ty2026 scenario 2-1 f1095c.pdf",
    "ty2026 scenario 2-2 f1095c.pdf",
    "ty2026 scenario 2c-0 f1094c.pdf",
    "ty2026 scenario 2c-2 f1095c.pdf",
  ]);
  assert.deepEqual(byId["scenario-3"].pdfs, [
    "ty2026 scenario 3-0 f1094c.pdf",
    "ty2026 scenario 3-1 f1095c.pdf",
    "ty2026 scenario 3c-0 f1094c.pdf",
  ]);
});

test("scenarioFile accepts listed ids and rejects traversal and unknown ids", async () => {
  const { scenarioFile } = await import("./scenarioCatalog.js");
  assert.match(await scenarioFile("scenario-2"), /fixtures\/aats\/scenario-2\.json$/);
  assert.equal(await scenarioFile("../x"), null);
  assert.equal(await scenarioFile("scenario-9"), null);
});

test("loadScenario composes redacted form and manifest XML", async () => {
  const { loadScenario } = await import("./scenarioCatalog.js");
  const loaded = await loadScenario("scenario-2");
  assert.equal(loaded.summary.id, "scenario-2");
  assert.match(loaded.manifestXml, /<\?xml/);
  const ssnTexts = [...loaded.formXml.matchAll(/<(?:\w+:)?SSN>([^<]*)<\/(?:\w+:)?SSN>/g)].map((m) => m[1]);
  assert.ok(ssnTexts.length > 0);
  assert.equal(ssnTexts.filter((text) => /[0-9]{9}/.test(text)).length, 0);
  assert.equal(await loadScenario("scenario-9"), null);
});

test("readScenarioPdf serves a listed entry and rejects one outside the list", async () => {
  const { readScenarioPdf } = await import("./scenarioCatalog.js");
  const pdf = await readScenarioPdf("scenario-3", "ty2026 scenario 3-0 f1094c.pdf");
  assert.equal(pdf.subarray(0, 5).toString("latin1"), "%PDF-");
  assert.equal(await readScenarioPdf("scenario-3", "ty2026 scenario 2-0 f1094c.pdf"), null);
  assert.equal(await readScenarioPdf("scenario-3", "../../etc/passwd"), null);
});

test("listScenarios returns an empty list when the scenarios directory is missing", async () => {
  const fs = require("node:fs");
  const os = require("node:os");
  const path = require("node:path");
  const emptyRoot = fs.mkdtempSync(path.join(os.tmpdir(), "air-empty-"));
  const previous = process.env.AIR_REPO_ROOT;
  process.env.AIR_REPO_ROOT = emptyRoot;
  try {
    const { listScenarios } = await import("./scenarioCatalog.js");
    assert.deepEqual(await listScenarios(), []);
  } finally {
    if (previous === undefined) delete process.env.AIR_REPO_ROOT;
    else process.env.AIR_REPO_ROOT = previous;
    fs.rmSync(emptyRoot, { recursive: true, force: true });
  }
});
