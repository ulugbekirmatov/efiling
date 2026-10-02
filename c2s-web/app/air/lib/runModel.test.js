const { test } = require("node:test");
const assert = require("node:assert/strict");

const UTID = "11111111-2222-3333-4444-5555555AbCdEf:SYS12345:T:20261102T100000Z";
const SUBMITTED_AT = "2026-11-02T10:00:00.000Z";
const SESSION = { utid: UTID, testFileCd: "T", fileName: "f.xml" };
const BASE_FILES = ["form", "session"];

function answer(status, at, exitCode = 0) {
  return { exitCode, result: { status, receiptId: "R-1" }, stderr: "", at };
}

function derive(model, { submit = null, statusChecks = [], session = SESSION, present = BASE_FILES } = {}) {
  return model.deriveRun("20261102T100000Z-scenario-1-abc123", { session, submit, statusChecks }, new Set(present));
}

test("deriveRun reaches every stage in STAGES", async () => {
  const model = await import("./runModel.js");
  const reached = {
    composed: derive(model),
    blocked: derive(model, { submit: { exitCode: 2, stderr: "guard", at: SUBMITTED_AT } }),
    sending: derive(model, { submit: { pending: true, at: SUBMITTED_AT } }),
    fault: derive(model, { submit: { exitCode: 1, stderr: "boom", at: SUBMITTED_AT } }),
    PROCESSING: derive(model, { submit: answer("PROCESSING", SUBMITTED_AT) }),
    ACCEPTED: derive(model, { submit: answer("ACCEPTED", SUBMITTED_AT) }),
    ACCEPTED_WITH_ERRORS: derive(model, { submit: answer("ACCEPTED_WITH_ERRORS", SUBMITTED_AT) }),
    PARTIALLY_ACCEPTED: derive(model, { submit: answer("PARTIALLY_ACCEPTED", SUBMITTED_AT) }),
    REJECTED: derive(model, { submit: answer("REJECTED", SUBMITTED_AT, 3) }),
    NOT_FOUND: derive(model, { submit: answer("NOT_FOUND", SUBMITTED_AT, 3) }),
  };
  assert.deepEqual(Object.keys(reached).sort(), Object.keys(model.STAGES).sort());
  for (const [stage, run] of Object.entries(reached)) assert.equal(run.stage, stage);
});

test("a killed submit process is a fault that counts as sent", async () => {
  const model = await import("./runModel.js");
  const run = derive(model, { submit: { exitCode: null, stderr: "", at: SUBMITTED_AT } });
  assert.equal(run.stage, "fault");
  assert.equal(model.canSubmit(run), false);
});

test("failed, blocked, and unknown-status checks leave PROCESSING unchanged", async () => {
  const model = await import("./runModel.js");
  const run = derive(model, {
    submit: answer("PROCESSING", SUBMITTED_AT),
    statusChecks: [
      { exitCode: 1, stderr: "channel down", at: "2026-11-02T10:11:00.000Z" },
      { exitCode: 2, stderr: "guard", at: "2026-11-02T10:12:00.000Z" },
      answer("SOMETHING_NEW", "2026-11-02T10:13:00.000Z"),
    ],
  });
  assert.equal(run.stage, "PROCESSING");
  assert.equal(run.answerAt, SUBMITTED_AT);
  assert.equal(run.statusChecks.length, 3);
});

test("a later IRS answer moves PROCESSING to its status", async () => {
  const model = await import("./runModel.js");
  const run = derive(model, {
    submit: answer("PROCESSING", SUBMITTED_AT),
    statusChecks: [answer("ACCEPTED", "2026-11-02T10:11:00.000Z")],
  });
  assert.equal(run.stage, "ACCEPTED");
  assert.equal(run.answerAt, "2026-11-02T10:11:00.000Z");
  assert.equal(run.receiptId, "R-1");
});

test("status checks are ordered by parsed time, not by string order", async () => {
  const model = await import("./runModel.js");
  const earlier = answer("REJECTED", "2026-11-02T10:00:00Z", 3);
  const later = answer("ACCEPTED", "2026-11-02T09:30:00-05:00");
  const run = derive(model, {
    submit: answer("PROCESSING", "2026-11-02T09:00:00Z"),
    statusChecks: [later, earlier],
  });
  assert.deepEqual(run.statusChecks.map((check) => check.result.status), ["REJECTED", "ACCEPTED"]);
  assert.equal(run.stage, "REJECTED");
});

test("a guard-blocked submit allows another submit", async () => {
  const model = await import("./runModel.js");
  const run = derive(model, { submit: { exitCode: 2, stderr: "guard", at: SUBMITTED_AT } });
  assert.equal(run.stage, "blocked");
  assert.equal(model.canSubmit(run), true);
});

test("a pending marker is the sending stage and blocks both actions", async () => {
  const model = await import("./runModel.js");
  const run = derive(model, { submit: { pending: true, at: SUBMITTED_AT } });
  assert.equal(run.stage, "sending");
  assert.equal(model.canSubmit(run), false);
  assert.equal(model.canCheckStatus(run, Date.parse(SUBMITTED_AT) + model.STATUS_WAIT_MS), false);
});

test("environment follows testFileCd", async () => {
  const model = await import("./runModel.js");
  assert.equal(derive(model, { session: { ...SESSION, testFileCd: "T" } }).environment, "aats");
  assert.equal(derive(model, { session: { ...SESSION, testFileCd: "P" } }).environment, "production");
  assert.equal(derive(model, { session: { ...SESSION, testFileCd: "X" } }).environment, "unknown");
  assert.equal(derive(model, { session: {} }).environment, "unknown");
});

test("canSubmit needs an AATS test file, a UTID, the form and session files, and an unsent stage", async () => {
  const model = await import("./runModel.js");
  assert.equal(model.canSubmit(derive(model)), true);
  assert.equal(model.canSubmit(derive(model, { session: { ...SESSION, testFileCd: "P" } })), false);
  assert.equal(model.canSubmit(derive(model, { session: { ...SESSION, testFileCd: "X" } })), false);
  assert.equal(model.canSubmit(derive(model, { session: { testFileCd: "T" } })), false);
  assert.equal(model.canSubmit(derive(model, { present: ["session"] })), false);
  assert.equal(model.canSubmit(derive(model, { submit: answer("PROCESSING", SUBMITTED_AT) })), false);
});

test("confirmMatches compares the UUID tail case-insensitively", async () => {
  const model = await import("./runModel.js");
  const run = derive(model);
  assert.equal(model.confirmCode(run), "AbCdEf");
  assert.equal(model.confirmMatches(run, "AbCdEf"), true);
  assert.equal(model.confirmMatches(run, "abcdef"), true);
  assert.equal(model.confirmMatches(run, "  ABCDEF\n"), true);
  assert.equal(model.confirmMatches(run, "abcde"), false);
  assert.equal(model.confirmMatches(run, "abcdefg"), false);
  assert.equal(model.confirmMatches(run, "AbCdEx"), false);
  assert.equal(model.confirmMatches(run, ""), false);
  assert.equal(model.confirmMatches(run, null), false);
  assert.equal(model.confirmMatches(run, undefined), false);
  assert.equal(model.confirmMatches(run, 123456), false);
});

test("confirmMatches is false for a run with no UTID or a short UUID", async () => {
  const model = await import("./runModel.js");
  assert.equal(model.confirmMatches(derive(model, { session: {} }), "abcdef"), false);
  assert.equal(model.confirmMatches(derive(model, { session: { utid: "abc:SYS" } }), "abc"), false);
});

test("canCheckStatus opens only from PROCESSING and only after the wait", async () => {
  const model = await import("./runModel.js");
  const opensAt = Date.parse(SUBMITTED_AT) + model.STATUS_WAIT_MS;
  const processing = derive(model, { submit: answer("PROCESSING", SUBMITTED_AT) });
  assert.equal(model.canCheckStatus(processing, opensAt - 1), false);
  assert.equal(model.canCheckStatus(processing, opensAt), true);
  const undated = derive(model, { submit: { exitCode: 0, result: { status: "PROCESSING", receiptId: "R1", errors: [] } } });
  assert.equal(model.canCheckStatus(undated, opensAt), false);

  const accepted = derive(model, { submit: answer("ACCEPTED", SUBMITTED_AT) });
  assert.equal(model.canCheckStatus(accepted, opensAt + 1), false);
  assert.equal(model.canCheckStatus(derive(model), opensAt + 1), false);
  assert.equal(model.canCheckStatus(derive(model, { submit: { exitCode: 1, at: SUBMITTED_AT } }), opensAt + 1), false);
});

test("the wait restarts from the latest PROCESSING answer", async () => {
  const model = await import("./runModel.js");
  const checkAt = "2026-11-02T10:20:00.000Z";
  const run = derive(model, {
    submit: answer("PROCESSING", SUBMITTED_AT),
    statusChecks: [answer("PROCESSING", checkAt)],
  });
  assert.equal(model.canCheckStatus(run, Date.parse(checkAt) + model.STATUS_WAIT_MS - 1), false);
  assert.equal(model.canCheckStatus(run, Date.parse(checkAt) + model.STATUS_WAIT_MS), true);
});

test("canCheckStatus is false while PROCESSING has no receipt id", async () => {
  const model = await import("./runModel.js");
  const submit = { exitCode: 0, result: { status: "PROCESSING" }, at: SUBMITTED_AT };
  const run = derive(model, { submit });
  assert.equal(model.canCheckStatus(run, Date.parse(SUBMITTED_AT) + model.STATUS_WAIT_MS), false);
});

test("isRunId accepts generated ids and rejects traversal and trailing newlines", async () => {
  const { isRunId } = await import("./runModel.js");
  assert.equal(isRunId("20261102T100000Z-scenario-1-abc123"), true);
  assert.equal(isRunId("20261102T100000Z-scenario-1-abc123\n"), false);
  assert.equal(isRunId("../x"), false);
  assert.equal(isRunId(null), false);
});
