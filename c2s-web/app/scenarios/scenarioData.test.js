const { test } = require("node:test");
const assert = require("node:assert/strict");

test("parseLineMapping returns one object per data row", async () => {
  const { parseLineMapping } = await import("./scenarioData.js");
  const tsv = [
    "document\tform line\telement path\tvalue\tpdf page",
    "IRS941\tLine 1\tIRS941/EmployeeCnt\t3\t2",
    "IRS941ScheduleB\tMonth 1 day 11\tIRS941ScheduleB/TaxLiability/Month1Day11Amt\t100.00\t4",
    "",
  ].join("\n");
  assert.deepEqual(parseLineMapping(tsv), [
    {
      document: "IRS941",
      formLine: "Line 1",
      elementPath: "IRS941/EmployeeCnt",
      value: "3",
      pdfPage: "2",
    },
    {
      document: "IRS941ScheduleB",
      formLine: "Month 1 day 11",
      elementPath: "IRS941ScheduleB/TaxLiability/Month1Day11Amt",
      value: "100.00",
      pdfPage: "4",
    },
  ]);
});

test("groupByDocument keeps first-appearance order", async () => {
  const { groupByDocument } = await import("./scenarioData.js");
  const rows = [
    { document: "IRS941", formLine: "a" },
    { document: "IRS941ScheduleR", formLine: "b" },
    { document: "IRS941", formLine: "c" },
    { document: "IRS8974", formLine: "d" },
  ];
  const groups = groupByDocument(rows);
  assert.deepEqual(
    groups.map((group) => group.document),
    ["IRS941", "IRS941ScheduleR", "IRS8974"]
  );
  assert.equal(groups[0].rows.length, 2);
  assert.equal(groups[0].rows[1].formLine, "c");
  assert.equal(groups[1].rows[0].formLine, "b");
});

test("loadRegistry on the real repo returns scenario numbers 1-4", async () => {
  const { loadRegistry } = await import("./scenarioData.js");
  const registry = loadRegistry();
  assert.deepEqual(
    registry.scenarios.map((scenario) => scenario.number),
    [1, 2, 3, 4]
  );
});

test("loadScenario(3) has all four formats and 81 mapping rows", async () => {
  const { loadScenario } = await import("./scenarioData.js");
  const loaded = loadScenario(3);
  assert.equal(loaded.formats.length, 4);
  for (const format of loaded.formats) {
    assert.equal(typeof format.content, "string");
    assert.ok(format.content.length > 0, `${format.id} should have content`);
  }
  assert.equal(loaded.lineMapping.length, 81);
});

test("loadScenario(9) is null", async () => {
  const { loadScenario } = await import("./scenarioData.js");
  assert.equal(loadScenario(9), null);
});

test("resolveScenarioFile maps through the registry and rejects unknown kinds", async () => {
  const { resolveScenarioFile } = await import("./scenarioData.js");
  assert.equal(resolveScenarioFile(1, "../../etc/passwd"), null);
  assert.equal(resolveScenarioFile(1, "pdf").fileName, "941-test-scenario-1-ty2026.pdf");
});

test("resolveLegacyFile returns null for an unknown id", async () => {
  const { resolveLegacyFile } = await import("./scenarioData.js");
  assert.equal(resolveLegacyFile("nope"), null);
});
