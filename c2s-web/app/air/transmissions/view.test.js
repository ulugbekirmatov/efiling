const { test } = require("node:test");
const assert = require("node:assert/strict");

const COMPOSED = {
  id: "20260115T120000Z-scenario-3-abc123",
  scenarioId: "scenario-3",
  composedAt: "2026-01-15T12:00:00.000Z",
  utid: "aaaaaaaa-bbbb-cccc-dddd-123456abcdef:SYS12:BB1BB::T",
  fileName: "1094C_Request_BB1BB_20260115120000.xml",
  checksum: "deadbeef",
  byteSize: 12345,
  paymentYear: 2026,
  priorYearData: false,
  testFileCd: "T",
  environment: "aats",
  payeeCount: 2,
  tcc: "BB1BB",
  transmitterName: "Pyramos Software LLC",
  validation: null,
  previewed: false,
  submit: null,
  statusChecks: [],
  receiptId: null,
  answerAt: null,
  errors: [],
  stage: "composed",
  files: ["form", "manifest", "session"],
};

const READY = {
  repoRoot: true,
  jar: true,
  java: true,
  xmllint: true,
  pkcs12: true,
  passwordEnvSet: true,
  asid: true,
  env: {
    passwordEnv: "AIR_P12_PASSWORD",
    pkcs12Var: "AIR_PKCS12",
    asidVar: "AIR_ASID",
  },
};

function run(overrides) {
  return { ...COMPOSED, ...overrides };
}

test("errorRows maps code, text, and xpath", async () => {
  const { errorRows } = await import("./view.js");
  assert.deepEqual(
    errorRows([
      { code: "AIRT0001", text: "Missing element", xpath: "/Form/Payee[1]" },
      { code: "AIRT0002", text: "Bad year" },
    ]),
    [
      { code: "AIRT0001", text: "Missing element", xpath: "/Form/Payee[1]" },
      { code: "AIRT0002", text: "Bad year", xpath: "" },
    ]
  );
});

test("errorRows is empty for missing input", async () => {
  const { errorRows } = await import("./view.js");
  assert.deepEqual(errorRows(null), []);
  assert.deepEqual(errorRows(undefined), []);
  assert.deepEqual(errorRows([]), []);
});

test("formatValue uses an em dash for empty values", async () => {
  const { formatValue } = await import("./view.js");
  assert.equal(formatValue(null), "—");
  assert.equal(formatValue(""), "—");
  assert.equal(formatValue(0), "0");
  assert.equal(formatValue("BB1BB"), "BB1BB");
});

test("formatBytes uses en-US grouping", async () => {
  const { formatBytes } = await import("./view.js");
  assert.equal(formatBytes(null), "—");
  assert.equal(formatBytes(0), "0");
  assert.equal(formatBytes(12345), "12,345");
});

test("formatPriorYear maps booleans to yes and no", async () => {
  const { formatPriorYear } = await import("./view.js");
  assert.equal(formatPriorYear(true), "yes");
  assert.equal(formatPriorYear(false), "no");
  assert.equal(formatPriorYear(null), "—");
  assert.equal(formatPriorYear("1"), "1");
});

test("formatLocalTime uses an em dash for empty and keeps invalid strings", async () => {
  const { formatLocalTime } = await import("./view.js");
  assert.equal(formatLocalTime(null), "—");
  assert.equal(formatLocalTime(""), "—");
  assert.equal(formatLocalTime("not-a-date"), "not-a-date");
});

test("SENT_TABS lists the seven What we sent tabs", async () => {
  const { SENT_TABS } = await import("./view.js");
  assert.deepEqual(
    SENT_TABS.map((tab) => tab.label),
    [
      "Form data",
      "Manifest",
      "Session",
      "Request MIME",
      "Request headers",
      "Submit response",
      "Status response",
    ]
  );
  assert.equal(SENT_TABS[0].download, "form");
  assert.equal(SENT_TABS[1].download, "manifest");
  assert.equal(SENT_TABS[6].fromStatusResponses, true);
});

test("displayDoc uses missingReason or Not produced yet", async () => {
  const { displayDoc, SENT_TABS } = await import("./view.js");
  const form = SENT_TABS[0];
  assert.deepEqual(displayDoc(form, {}, []), {
    text: null,
    missingReason: "Not produced yet.",
    stderr: null,
  });
  assert.deepEqual(
    displayDoc(form, { form: { missingReason: "form.xml is not in this run." } }, []),
    { text: null, missingReason: "form.xml is not in this run.", stderr: null }
  );
  assert.deepEqual(displayDoc(form, { form: { text: "<Form/>" } }, []), {
    text: "<Form/>",
    missingReason: null,
    stderr: null,
  });
});

test("displayDoc picks the latest status response by at", async () => {
  const { displayDoc, SENT_TABS } = await import("./view.js");
  const tab = SENT_TABS.find((item) => item.id === "statusResponse");
  assert.deepEqual(displayDoc(tab, {}, []), {
    text: null,
    missingReason: "Not produced yet.",
    stderr: null,
  });
  assert.deepEqual(
    displayDoc(
      tab,
      {},
      [
        { at: "2026-01-15T13:00:00.000Z", text: "<new/>" },
        { at: "2026-01-15T12:00:00.000Z", text: "<old/>" },
      ]
    ),
    { text: "<new/>", missingReason: null, stderr: null }
  );
  assert.deepEqual(
    displayDoc(
      tab,
      {},
      [
        {
          at: "2026-01-15T13:00:00.000Z",
          text: null,
          stderr: "Status check failed: connection refused",
          exitCode: 1,
        },
      ]
    ),
    {
      text: null,
      missingReason: null,
      stderr: "Status check failed: connection refused",
    }
  );
  assert.deepEqual(
    displayDoc(tab, {}, [{ at: "2026-01-15T13:00:00.000Z", text: null, exitCode: 1 }]),
    { text: null, missingReason: "Not produced yet.", stderr: null }
  );
});

test("downloadHref points at the raw form and manifest routes", async () => {
  const { downloadHref } = await import("./view.js");
  assert.equal(
    downloadHref("20260115T120000Z-scenario-3-abc123", "form"),
    "/air/api/runs/20260115T120000Z-scenario-3-abc123/files/form"
  );
  assert.equal(
    downloadHref("20260115T120000Z-scenario-3-abc123", "manifest"),
    "/air/api/runs/20260115T120000Z-scenario-3-abc123/files/manifest"
  );
});

test("xmlForDisplay pretty-prints unless packed", async () => {
  const { xmlForDisplay } = await import("./view.js");
  assert.equal(xmlForDisplay("<a><b>x</b></a>", true), "<a><b>x</b></a>");
  assert.equal(xmlForDisplay("<a><b>x</b></a>", false), "<a>\n  <b>x</b>\n</a>");
});

test("readinessItems labels and env vars", async () => {
  const { readinessItems } = await import("./view.js");
  assert.deepEqual(readinessItems(null), [
    { key: "repoRoot", label: "repoRoot", ok: false, envVar: "AIR_REPO_ROOT" },
    { key: "jar", label: "jar", ok: false, envVar: "AIR_JAR" },
    { key: "java", label: "java", ok: false, envVar: "AIR_JAVA_HOME" },
    { key: "xmllint", label: "xmllint", ok: false, envVar: null },
    { key: "pkcs12", label: "pkcs12", ok: false, envVar: "AIR_PKCS12" },
    { key: "passwordEnvSet", label: "password env", ok: false, envVar: "AIR_P12_PASSWORD" },
    { key: "asid", label: "ASID", ok: false, envVar: "AIR_ASID" },
  ]);
  assert.deepEqual(
    readinessItems(READY).map((item) => [item.key, item.ok, item.envVar]),
    [
      ["repoRoot", true, "AIR_REPO_ROOT"],
      ["jar", true, "AIR_JAR"],
      ["java", true, "AIR_JAVA_HOME"],
      ["xmllint", true, null],
      ["pkcs12", true, "AIR_PKCS12"],
      ["passwordEnvSet", true, "AIR_P12_PASSWORD"],
      ["asid", true, "AIR_ASID"],
    ]
  );
});

test("isReady is true only when every flag is true", async () => {
  const { isReady } = await import("./view.js");
  assert.equal(isReady(null), false);
  assert.equal(isReady(READY), true);
  assert.equal(isReady({ ...READY, asid: false }), false);
});

test("actionState enables validate when repoRoot and xmllint are ready", async () => {
  const { actionState } = await import("./view.js");
  const none = actionState(null, null, 0);
  assert.equal(none.validate.enabled, false);
  assert.equal(none.validate.reason, "No run selected.");
  const missingRoot = actionState(run(), null, 0);
  assert.equal(missingRoot.validate.enabled, false);
  assert.equal(missingRoot.validate.reason, "Set AIR_REPO_ROOT.");
  const missingLint = actionState(run(), { ...READY, xmllint: false }, 0);
  assert.equal(missingLint.validate.enabled, false);
  assert.equal(missingLint.validate.reason, "xmllint is not available.");
  const composed = actionState(run(), READY, 0);
  assert.equal(composed.validate.enabled, true);
  assert.equal(composed.validate.reason, null);
});

test("actionState enables preview only when Java readiness is set, not xmllint", async () => {
  const { actionState } = await import("./view.js");
  const waiting = actionState(run(), { ...READY, pkcs12: false }, 0);
  assert.equal(waiting.preview.enabled, false);
  assert.equal(waiting.preview.reason, "Set AIR_PKCS12.");
  const noLint = actionState(run(), { ...READY, xmllint: false }, 0);
  assert.equal(noLint.preview.enabled, true);
  assert.equal(noLint.preview.reason, null);
  const ready = actionState(run(), READY, 0);
  assert.equal(ready.preview.enabled, true);
  assert.equal(ready.preview.reason, null);
  const missingRoot = actionState(run(), null, 0);
  assert.equal(missingRoot.preview.enabled, false);
  assert.equal(missingRoot.preview.reason, "Set AIR_REPO_ROOT.");
  const noJar = actionState(run(), { ...READY, jar: false }, 0);
  assert.equal(noJar.preview.reason, "Set AIR_JAR.");
  const noPass = actionState(run(), { ...READY, passwordEnvSet: false }, 0);
  assert.equal(noPass.preview.reason, "Set AIR_P12_PASSWORD.");
});

test("actionState enables submit only when canSubmit is true and Java is ready", async () => {
  const { actionState } = await import("./view.js");
  const composed = actionState(run(), READY, 0);
  assert.equal(composed.submit.enabled, true);
  assert.equal(composed.submit.reason, null);
  const production = actionState(run({ testFileCd: "P", environment: "production" }), READY, 0);
  assert.equal(production.submit.enabled, false);
  assert.equal(production.submit.reason, "Only AATS (test file code T) can be submitted.");
  const sent = actionState(
    run({
      stage: "PROCESSING",
      receiptId: "REC1",
      files: ["form", "manifest", "session", "submit"],
    }),
    READY,
    0
  );
  assert.equal(sent.submit.enabled, false);
  assert.equal(sent.submit.reason, "This run was already sent.");
  const noAsid = actionState(run(), { ...READY, asid: false }, 0);
  assert.equal(noAsid.submit.enabled, false);
  assert.equal(noAsid.submit.reason, "Set AIR_ASID.");
  const noPass = actionState(
    run(),
    { ...READY, passwordEnvSet: false, env: { ...READY.env, passwordEnv: "MY_P12" } },
    0
  );
  assert.equal(noPass.submit.enabled, false);
  assert.equal(noPass.submit.reason, "Set MY_P12.");
  const noLint = actionState(run(), { ...READY, xmllint: false }, 0);
  assert.equal(noLint.submit.enabled, true);
  assert.equal(noLint.submit.reason, null);
});

test("actionState enables check status after the 10 minute wait while PROCESSING", async () => {
  const { actionState } = await import("./view.js");
  const { STATUS_WAIT_MS } = await import("../lib/runModel.js");
  const at = "2026-01-15T12:00:00.000Z";
  const processing = run({
    stage: "PROCESSING",
    receiptId: "REC1",
    answerAt: at,
    submit: {
      exitCode: 0,
      at,
      result: { status: "PROCESSING", receiptId: "REC1" },
    },
    files: ["form", "manifest", "session", "submit"],
  });
  const opensAt = Date.parse(at) + STATUS_WAIT_MS;
  const early = actionState(processing, READY, opensAt - 1);
  assert.equal(early.checkStatus.enabled, false);
  assert.equal(early.checkStatus.opensAt, opensAt);
  assert.match(early.checkStatus.reason, /^opens at /);
  const open = actionState(processing, READY, opensAt);
  assert.equal(open.checkStatus.enabled, true);
  assert.equal(open.checkStatus.reason, null);
  const noJava = actionState(processing, { ...READY, java: false }, opensAt);
  assert.equal(noJava.checkStatus.enabled, false);
  assert.equal(noJava.checkStatus.reason, "Set AIR_JAVA_HOME.");
  const noLint = actionState(processing, { ...READY, xmllint: false }, opensAt);
  assert.equal(noLint.checkStatus.enabled, true);
  assert.equal(noLint.checkStatus.reason, null);
  const composed = actionState(run(), READY, opensAt);
  assert.equal(composed.checkStatus.enabled, false);
  assert.equal(composed.checkStatus.reason, "Status can be checked only while IRS is processing.");
});

test("submitArmed is true only for the last six of the UTID UUID", async () => {
  const { submitArmed } = await import("./view.js");
  assert.equal(submitArmed(run(), "abcdef"), true);
  assert.equal(submitArmed(run(), "ABCDEF"), true);
  assert.equal(submitArmed(run(), "abcde"), false);
  assert.equal(submitArmed(run(), "nopexx"), false);
});

test("confirmPrompt asks the operator to type the confirm code", async () => {
  const { confirmPrompt } = await import("./view.js");
  assert.equal(confirmPrompt(run()), "Type abcdef to send this UTID to IRS AATS");
  assert.equal(confirmPrompt(run({ utid: null })), null);
});

test("noticeText maps AIR action error codes", async () => {
  const { noticeText, noticeFrom } = await import("./view.js");
  assert.equal(noticeText({ code: "NOT_SUBMITTABLE" }), "This run cannot be submitted to AATS.");
  assert.equal(
    noticeText({ code: "CONFIRM_MISMATCH" }),
    "That confirmation code does not match this UTID."
  );
  assert.equal(
    noticeText({ code: "SUBMIT_IN_PROGRESS" }),
    "A submit is already in progress for this run."
  );
  assert.equal(noticeText({ code: "STATUS_NOT_OPEN" }), "Status check is not open yet.");
  assert.equal(
    noticeText({ code: "STATUS_NOT_OPEN", message: "Status check opens at 2026-01-15T12:10:00.000Z." }),
    `Status check opens at ${new Date("2026-01-15T12:10:00.000Z").toLocaleString()}.`
  );
  assert.equal(noticeText({ code: "COMPOSE_FAILED" }), "Compose failed.");
  assert.equal(noticeText({ code: "AIR_NOT_CONFIGURED" }), "AIR is not configured.");
  assert.equal(
    noticeText({ code: "AIR_NOT_CONFIGURED", message: "Set AIR_ASID." }),
    "Set AIR_ASID."
  );
  assert.equal(noticeText({ message: "boom" }), "boom");
  assert.equal(noticeFrom({ status: 0, message: "Network error" }).text, "Cannot reach the AIR operator API.");
  assert.equal(
    noticeFrom({ status: 409, code: "CONFIRM_MISMATCH", json: true }).text,
    "That confirmation code does not match this UTID."
  );
});

test("statusHistoryRows uses no answer when IRS did not reply", async () => {
  const { statusHistoryRows } = await import("./view.js");
  assert.deepEqual(statusHistoryRows(run()), []);
  assert.deepEqual(
    statusHistoryRows(
      run({
        submit: { pending: true, at: "2026-01-15T12:00:00.000Z" },
      })
    ),
    [{ at: "2026-01-15T12:00:00.000Z", exitCode: "—", status: "no answer", stderr: "" }]
  );
  assert.deepEqual(
    statusHistoryRows(
      run({
        submit: {
          exitCode: 0,
          at: "2026-01-15T12:00:00.000Z",
          result: { status: "PROCESSING", receiptId: "REC1" },
          stderr: "",
        },
        statusChecks: [
          { exitCode: 1, at: "2026-01-15T12:20:00.000Z", result: null, stderr: "channel down" },
          {
            exitCode: 0,
            at: "2026-01-15T12:30:00.000Z",
            result: { status: "ACCEPTED", receiptId: "REC1" },
          },
        ],
      })
    ),
    [
      { at: "2026-01-15T12:00:00.000Z", exitCode: 0, status: "PROCESSING", stderr: "" },
      { at: "2026-01-15T12:20:00.000Z", exitCode: 1, status: "no answer", stderr: "channel down" },
      { at: "2026-01-15T12:30:00.000Z", exitCode: 0, status: "ACCEPTED", stderr: "" },
    ]
  );
});

test("previewStderr truncates at 400 characters", async () => {
  const { previewStderr, STDERR_PREVIEW_LIMIT } = await import("./view.js");
  assert.equal(STDERR_PREVIEW_LIMIT, 400);
  assert.deepEqual(previewStderr(""), { preview: "", full: "", truncated: false });
  assert.deepEqual(previewStderr(null), { preview: "", full: "", truncated: false });
  assert.deepEqual(previewStderr("short"), { preview: "short", full: "short", truncated: false });
  const long = "x".repeat(401);
  assert.deepEqual(previewStderr(long), {
    preview: "x".repeat(400),
    full: long,
    truncated: true,
  });
});

test("displayStage is Sending while a submit is in flight", async () => {
  const { displayStage } = await import("./view.js");
  assert.deepEqual(displayStage(run(), "submit"), {
    label: "Sending",
    tone: "waiting",
    sent: true,
    next: "A submit is running. If it never finishes, compose a new run; this UTID may have reached IRS.",
  });
  assert.equal(displayStage(run(), "validate").label, "Composed, not sent");
  assert.equal(displayStage(run(), null).label, "Composed, not sent");
});

test("validationRows maps PASS and FAIL per file", async () => {
  const { validationRows } = await import("./view.js");
  assert.deepEqual(validationRows(null), []);
  assert.deepEqual(
    validationRows({
      ok: false,
      files: [
        { path: "/Users/op/.air-operator/runs/r1/form.xml", kind: "form", ok: true, errors: [] },
        { path: "manifest.xml", kind: "manifest", ok: false, errors: ["missing TCC"] },
      ],
    }),
    [
      { path: "form.xml", kind: "form", result: "PASS", errors: [] },
      { path: "manifest.xml", kind: "manifest", result: "FAIL", errors: ["missing TCC"] },
    ]
  );
});

test("runStage uses STAGES tone and label", async () => {
  const { runStage } = await import("./view.js");
  assert.deepEqual(runStage(run()), {
    label: "Composed, not sent",
    tone: "waiting",
    sent: false,
    next: "Validate, preview, then submit to AATS.",
  });
  assert.equal(runStage(run({ stage: "ACCEPTED" })).tone, "safe");
  assert.equal(runStage(run({ stage: "ACCEPTED" })).label, "Accepted");
});

test("statusOpensLabel is null once the wait has elapsed", async () => {
  const { statusOpensLabel } = await import("./view.js");
  assert.equal(statusOpensLabel(null, 0), null);
  assert.equal(statusOpensLabel(100, 100), null);
  assert.equal(statusOpensLabel(100, 99).startsWith("opens at "), true);
});

test("noticeText shows the STATUS_NOT_OPEN time in local time", async () => {
  const { noticeText } = await import("./view.js");
  const iso = "2026-11-02T10:10:00.000Z";
  assert.equal(noticeText({ code: "STATUS_NOT_OPEN", message: `Status check opens at ${iso}` }), `Status check opens at ${new Date(iso).toLocaleString()}`);
});
