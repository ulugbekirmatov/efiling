const { test } = require("node:test");
const assert = require("node:assert/strict");
const os = require("node:os");
const path = require("node:path");

const OPERATOR_PAGE = path.join(os.homedir(), "Documents", "aca", "ftb-fx", "scripts", "operator-page.js");

function rawSsns(id) {
  const model = require(OPERATOR_PAGE).buildModel({ previewRoot: path.join(os.tmpdir(), "c2s-web-ftb-test") });
  const scenario = model.scenarios.find((s) => s.id === id);
  const ssns = scenario.summary.returns.flatMap((r) => r.people).filter((p) => p.idLabel === "SSN").map((p) => String(p.id));
  assert.ok(ssns.length > 0 || id === "5C", `scenario ${id} has SSNs to check`);
  return ssns.flatMap((ssn) => [ssn, `${ssn.slice(0, 3)}-${ssn.slice(3, 5)}-${ssn.slice(5)}`]);
}

test("listScenarios returns the five FTB scenarios in testing order with every check passing", async () => {
  const { listScenarios } = await import("./ftbCatalog.js");
  const { meta, scenarios } = listScenarios();
  assert.equal(meta.knownDifferences.length, 4);
  const rows = scenarios.map(({ id, formKind, correctionOf, recordCount, checksPassed, checksTotal }) => ({
    id, formKind, correctionOf, recordCount, checksPassed, checksTotal,
  }));
  assert.deepEqual(rows, [
    { id: "1", formKind: "B", correctionOf: null, recordCount: 3, checksPassed: 5, checksTotal: 5 },
    { id: "2", formKind: "B", correctionOf: null, recordCount: 2, checksPassed: 5, checksTotal: 5 },
    { id: "2C", formKind: "B", correctionOf: "2", recordCount: 2, checksPassed: 5, checksTotal: 5 },
    { id: "5", formKind: "C", correctionOf: null, recordCount: 3, checksPassed: 5, checksTotal: 5 },
    { id: "5C", formKind: "C", correctionOf: "5", recordCount: 2, checksPassed: 5, checksTotal: 5 },
  ]);
});

test("loadScenario returns null for unknown and traversal ids", async () => {
  const { loadScenario } = await import("./ftbCatalog.js");
  assert.equal(loadScenario("9"), null);
  assert.equal(loadScenario("../1"), null);
});

test("loadScenario masks every SSN in people, XML and narrative to the last four digits", async () => {
  const { loadScenario } = await import("./ftbCatalog.js");
  for (const id of ["1", "2", "2C", "5", "5C"]) {
    const { scenario } = loadScenario(id);
    const ssnPeople = scenario.summary.returns.flatMap((r) => r.people).filter((p) => p.idLabel === "SSN");
    for (const person of ssnPeople) assert.match(person.id, /^\*{5}\d{4}$/, `scenario ${id} person id`);
    const xml = [scenario.preview.formXml, scenario.preview.answerKeyXml].join("");
    const ssnTexts = [...xml.matchAll(/<(?:\w+:)?SSN>([^<]*)<\/(?:\w+:)?SSN>/g)].map((m) => m[1]);
    for (const value of ssnTexts) assert.match(value, /^\*{5}\d{4}$/, `scenario ${id} XML SSN`);
    const narrative = scenario.narrative.map((line) => line.text).join("\n");
    for (const ssn of rawSsns(id)) assert.equal(narrative.includes(ssn), false, `scenario ${id} narrative`);
  }
});
