const { test, before, after } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");

const runsDir = fs.mkdtempSync(path.join(os.tmpdir(), "air-runs-"));
process.env.AIR_RUNS_DIR = runsDir;
const PASSWORD_VALUE = "fake-p12-password-9f3";
const NOW = Date.UTC(2026, 10, 2, 10, 0, 0, 123);
const SESSION = { utid: "11111111-2222-3333-4444-5555555AbCdEf:SYS12345:T:20261102T100000Z", testFileCd: "T", fileName: "f.xml" };

after(() => fs.rmSync(runsDir, { recursive: true, force: true }));

async function createRun(scenarioId = "scenario-1", now = NOW) {
  const store = await import("./runStore.js");
  const id = store.newRunId(scenarioId, now);
  const tmp = await store.stagingDir();
  fs.writeFileSync(path.join(tmp, "session.json"), JSON.stringify(SESSION));
  fs.writeFileSync(path.join(tmp, "form.xml"), "<Form><SSN>123456789</SSN></Form>");
  fs.writeFileSync(path.join(tmp, "manifest.xml"), "<Manifest/>");
  await store.commitComposedRun(id, tmp, { scenarioId, composedAt: new Date(now).toISOString() });
  return { store, id };
}

test("newRunId builds a valid id from a UTC stamp and a cleaned slug", async () => {
  const store = await import("./runStore.js");
  const { isRunId } = await import("./runModel.js");
  const id = store.newRunId("Scenario 3b!", NOW);
  assert.equal(id.startsWith("20261102T100000Z-scenario-3b-"), true);
  assert.equal(isRunId(id), true);
  assert.equal(isRunId(store.newRunId("", NOW)), true);
  assert.equal(isRunId(store.newRunId("x".repeat(100), NOW)), true);
});

test("a composed run is listed and read with its derived stage", async () => {
  const { store, id } = await createRun();
  const run = await store.readRun(id);
  assert.equal(run.id, id);
  assert.equal(run.stage, "composed");
  assert.equal(run.scenarioId, "scenario-1");
  assert.equal(run.environment, "aats");
  assert.deepEqual(run.files, ["form", "manifest", "session", "compose"]);
  assert.equal((await store.listRuns()).some((entry) => entry.id === id), true);
});

test("listRuns returns newest first and skips stray entries and runs without a session", async () => {
  const older = await createRun("scenario-order", Date.UTC(2026, 10, 1, 8, 0, 0));
  const newer = await createRun("scenario-order", Date.UTC(2026, 10, 3, 8, 0, 0));
  fs.mkdirSync(path.join(runsDir, "20261104T000000Z-nosession-abcdef"));
  fs.mkdirSync(path.join(runsDir, "not-a-run"));
  const ids = (await newer.store.listRuns()).map((run) => run.id);
  assert.equal(ids.indexOf(newer.id) < ids.indexOf(older.id), true);
  assert.equal(ids.includes("20261104T000000Z-nosession-abcdef"), false);
  assert.equal(ids.includes("not-a-run"), false);
  assert.equal(ids.includes(".staging"), false);
});

test("readRun returns null for a valid id with no directory", async () => {
  const store = await import("./runStore.js");
  assert.equal(await store.readRun("20200101T000000Z-missing-abcdef"), null);
});

test("readDocuments masks SSN text and reports missing files", async () => {
  const { store, id } = await createRun();
  const { documents, statusResponses } = await store.readDocuments(id);
  assert.equal(documents.form.text, "<Form><SSN>*****6789</SSN></Form>");
  assert.equal(documents.form.missingReason, null);
  assert.equal(documents.submit.text, null);
  assert.equal(documents.submit.missingReason, "Not created yet.");
  assert.equal(documents.session.text.includes("\n  \"testFileCd\": \"T\""), true);
  assert.deepEqual(statusResponses, []);
});

test("readDocuments returns status responses oldest first and redacted", async () => {
  const { store, id } = await createRun();
  const dir = store.runDir(id);
  const second = "status-20261102T101100000Z-bbbb";
  const first = "status-20261102T100000000Z-aaaa";
  fs.writeFileSync(path.join(dir, `${second}.json`), JSON.stringify({ exitCode: 0, at: "2026-11-02T10:11:00.000Z" }));
  fs.writeFileSync(path.join(dir, `${second}.response.xml`), "<r>second</r>");
  fs.writeFileSync(path.join(dir, `${first}.json`), JSON.stringify({ exitCode: 0, at: "2026-11-02T10:00:00.000Z" }));
  fs.writeFileSync(path.join(dir, `${first}.response.xml`), "<SSN>111223333</SSN>");
  const { statusResponses } = await store.readDocuments(id);
  assert.deepEqual(statusResponses, [
    { at: "2026-11-02T10:00:00.000Z", exitCode: 0, text: "<SSN>*****3333</SSN>", stderr: "" },
    { at: "2026-11-02T10:11:00.000Z", exitCode: 0, text: "<r>second</r>", stderr: "" },
  ]);
});

test("readDocuments keeps a status check without a response file, with null text and masked stderr", async () => {
  const { store, id } = await createRun();
  const dir = store.runDir(id);
  fs.writeFileSync(
    path.join(dir, "status-20261102T100000000Z-cccc.json"),
    JSON.stringify({ exitCode: 1, at: "2026-11-02T10:00:00.000Z", stderr: "bad tin 123456789" }),
  );
  const { statusResponses } = await store.readDocuments(id);
  assert.deepEqual(statusResponses, [
    { at: "2026-11-02T10:00:00.000Z", exitCode: 1, text: null, stderr: "bad tin *****6789" },
  ]);
});

test("readRun and listRuns mask CLI text in stderr, errors, validation, and compose records", async () => {
  const { store, id } = await createRun("scenario-mask");
  const dir = store.runDir(id);
  const write = (name, value) => fs.writeFileSync(path.join(dir, name), JSON.stringify(value));
  write("compose.json", { scenarioId: "scenario-mask", result: { note: "tin 123-45-6789" } });
  write("validate.json", { ok: false, files: [{ path: "form.xml", ok: false, errors: ["ssn 123456789 bad"] }] });
  write("submit.json", {
    exitCode: 3,
    stderr: "rejected 123456789",
    at: "2026-11-02T10:00:00.000Z",
    result: { status: "REJECTED", errors: [{ text: "SSN 123456789 invalid", xpath: "/a[.='123456789']" }] },
  });
  write("status-20261102T101000000Z-dddd.json", { exitCode: 1, stderr: "again 987654321", at: "2026-11-02T10:10:00.000Z" });
  const expectMasked = (run) => {
    assert.equal(run.submit.stderr, "rejected *****6789");
    assert.deepEqual(run.errors, [{ text: "SSN *****6789 invalid", xpath: "/a[.='*****6789']" }]);
    assert.equal(run.statusChecks[0].stderr, "again *****4321");
    assert.deepEqual(run.validation.files[0].errors, ["ssn *****6789 bad"]);
  };
  expectMasked(await store.readRun(id));
  expectMasked((await store.listRuns()).find((run) => run.id === id));
  const { documents } = await store.readDocuments(id);
  assert.equal(documents.compose.text.includes("*****6789"), true);
  assert.equal(documents.compose.text.includes("123-45-6789"), false);
  assert.equal(documents.submit.text.includes("123456789"), false);
});

test("beginSubmit lets exactly one of several processes replace an exit-2 record", async () => {
  const { spawn } = require("node:child_process");
  const { pathToFileURL } = require("node:url");
  const storeUrl = pathToFileURL(path.join(__dirname, "runStore.js")).href;
  const script = `
    const store = await import(${JSON.stringify(storeUrl)});
    const start = Number(process.env.RACE_START);
    while (Date.now() < start) {}
    process.stdout.write(String(await store.beginSubmit(process.env.RACE_RUN)));
  `;
  const runOnce = (id, start) => new Promise((resolve) => {
    const child = spawn(process.execPath, ["--input-type=module", "-e", script], {
      env: { ...process.env, RACE_RUN: id, RACE_START: String(start) },
    });
    let out = "";
    child.stdout.on("data", (chunk) => { out += chunk; });
    child.on("close", () => resolve(out));
  });
  for (let round = 0; round < 5; round += 1) {
    const { store, id } = await createRun(`scenario-race-${round}`, NOW + round * 1000);
    fs.writeFileSync(path.join(store.runDir(id), "submit.json"), JSON.stringify({ exitCode: 2, stderr: "guard" }));
    const outputs = await Promise.all([1, 2, 3, 4].map(() => runOnce(id, Date.now() + 1500)));
    assert.equal(outputs.filter((out) => out === "true").length, 1, outputs.join(","));
    assert.equal(fs.existsSync(path.join(store.runDir(id), "submit.lock")), false);
  }
});

test("beginSubmit refuses a replace while another process holds submit.lock", async () => {
  const { store, id } = await createRun("scenario-locked");
  fs.writeFileSync(path.join(store.runDir(id), "submit.json"), JSON.stringify({ exitCode: 2 }));
  fs.writeFileSync(path.join(store.runDir(id), "submit.lock"), "");
  assert.equal(await store.beginSubmit(id), false);
});

test("stderr redaction also hides the pkcs12 path", async () => {
  const { store, id } = await createRun("scenario-p12");
  process.env.AIR_PKCS12 = "/secret/keys/air-fake.p12";
  try {
    assert.equal(await store.beginSubmit(id), true);
    await store.finishSubmit(id, { exitCode: 1, stderr: "cannot open /secret/keys/air-fake.p12", at: "2026-11-02T10:00:01.000Z" });
  } finally {
    delete process.env.AIR_PKCS12;
  }
  const onDisk = JSON.parse(fs.readFileSync(path.join(store.runDir(id), "submit.json"), "utf8"));
  assert.equal(onDisk.stderr, "cannot open [redacted]");
});

test("traversal ids and ids with a trailing newline are rejected", async () => {
  const store = await import("./runStore.js");
  const bad = ["../x", "..", "", "20261102T100000Z-a-abc12\n", "20261102T100000Z-a-abc123\n", "a/b", null];
  for (const id of bad) {
    assert.throws(() => store.runDir(id), { code: "INVALID_RUN_ID" });
    await assert.rejects(store.readRun(id), { code: "INVALID_RUN_ID" });
    await assert.rejects(store.readDocuments(id), { code: "INVALID_RUN_ID" });
    await assert.rejects(store.beginSubmit(id), { code: "INVALID_RUN_ID" });
    await assert.rejects(store.readRawFile(id, "form"), { code: "INVALID_RUN_ID" });
  }
});

test("beginSubmit succeeds once, then refuses until a guard-blocked result replaces the marker", async () => {
  const { store, id } = await createRun();
  assert.equal(await store.beginSubmit(id), true);
  assert.equal((await store.readRun(id)).stage, "sending");
  assert.equal(await store.beginSubmit(id), false);

  await store.finishSubmit(id, { exitCode: 2, stderr: "guard", at: "2026-11-02T10:00:01.000Z" });
  assert.equal((await store.readRun(id)).stage, "blocked");
  assert.equal(await store.beginSubmit(id), true);
  assert.equal((await store.readRun(id)).stage, "sending");
});

test("beginSubmit refuses after a submit that reached IRS", async () => {
  const { store, id } = await createRun();
  assert.equal(await store.beginSubmit(id), true);
  await store.finishSubmit(id, { exitCode: 0, result: { status: "PROCESSING", receiptId: "R-9" }, stderr: "", at: "2026-11-02T10:00:01.000Z" });
  assert.equal(await store.beginSubmit(id), false);
  assert.equal((await store.readRun(id)).stage, "PROCESSING");
});

test("concurrent beginSubmit calls produce exactly one winner", async () => {
  const { store, id } = await createRun();
  const results = await Promise.all([store.beginSubmit(id), store.beginSubmit(id), store.beginSubmit(id)]);
  assert.deepEqual(results.filter(Boolean).length, 1);
});

test("finishSubmit redacts the password env value from stderr before writing", async () => {
  const { store, id } = await createRun();
  process.env.AIR_P12_PASSWORD = PASSWORD_VALUE;
  try {
    assert.equal(await store.beginSubmit(id), true);
    await store.finishSubmit(id, { exitCode: 1, stderr: `bad password ${PASSWORD_VALUE}!`, at: "2026-11-02T10:00:01.000Z" });
  } finally {
    delete process.env.AIR_P12_PASSWORD;
  }
  const onDisk = fs.readFileSync(path.join(store.runDir(id), "submit.json"), "utf8");
  assert.equal(onDisk.includes(PASSWORD_VALUE), false);
  assert.equal(JSON.parse(onDisk).stderr, "bad password [redacted]!");
});

test("status check paths stay in the run and writeStatusCheck redacts stderr", async () => {
  const { store, id } = await createRun();
  const { recordPath, responsePath, stamp } = store.statusCheckPaths(id, NOW);
  assert.equal(stamp, "20261102T100000123Z");
  assert.equal(path.dirname(recordPath), store.runDir(id));
  assert.equal(path.basename(responsePath).endsWith(".response.xml"), true);

  process.env.AIR_P12_PASSWORD = PASSWORD_VALUE;
  try {
    await store.writeStatusCheck(recordPath, { exitCode: 1, stderr: `x ${PASSWORD_VALUE}`, at: "2026-11-02T10:10:00.000Z" });
  } finally {
    delete process.env.AIR_P12_PASSWORD;
  }
  assert.equal(JSON.parse(fs.readFileSync(recordPath, "utf8")).stderr, "x [redacted]");
  assert.equal((await store.readRun(id)).statusChecks.length, 1);
  await assert.rejects(store.writeStatusCheck(path.join(os.tmpdir(), "status-20261102T100000123Z-abcd.json"), {}));
});

test("writeValidate stores the record the run reports", async () => {
  const { store, id } = await createRun();
  await store.writeValidate(id, { ok: true, files: [], at: "2026-11-02T10:00:00.000Z" });
  assert.equal((await store.readRun(id)).validation.ok, true);
});

test("readRawFile serves only downloadable keys", async () => {
  const { store, id } = await createRun();
  assert.equal((await store.readRawFile(id, "form")).toString(), "<Form><SSN>123456789</SSN></Form>");
  assert.equal(await store.readRawFile(id, "session"), null);
  assert.equal(await store.readRawFile(id, "__proto__"), null);
  assert.equal(await store.readRawFile(id, "submitResponse"), null);
});

test("commitComposedRun refuses a staging directory outside the run store", async () => {
  const store = await import("./runStore.js");
  const outside = fs.mkdtempSync(path.join(os.tmpdir(), "air-outside-"));
  try {
    await assert.rejects(store.commitComposedRun(store.newRunId("scenario-1", NOW), outside, {}), /outside the run store/);
  } finally {
    fs.rmSync(outside, { recursive: true, force: true });
  }
});
