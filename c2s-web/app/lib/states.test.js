const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const FACTS = path.join(__dirname, "..", "..", "generated", "mef-facts.json");

const FACT_DOMAINS = {
  journalStates: "mef.journal",
  ackStatuses: "mef.ack",
  scenarioStates: "mef.scenario",
};

test("every state in mef-facts.json has an explicit tone", async () => {
  const facts = JSON.parse(fs.readFileSync(FACTS, "utf8"));
  const { DOMAIN_TONES } = await import("./states.js");
  const missing = [];
  for (const [key, domain] of Object.entries(FACT_DOMAINS)) {
    assert.ok(Array.isArray(facts[key]), `${key} is a list`);
    const table = DOMAIN_TONES[domain];
    for (const state of facts[key]) {
      if (!table || !Object.prototype.hasOwnProperty.call(table, state)) {
        missing.push(`${domain} ${state}`);
      }
    }
  }
  assert.deepEqual(missing, []);
});

test("toneFor returns the tone each page used, and waiting for an unknown state", async () => {
  const { toneFor } = await import("./states.js");
  const cases = [
    ["mef.journal", "CREATED", "neutral"],
    ["mef.journal", "TRANSMITTED", "neutral"],
    ["mef.journal", "REJECTED", "neutral"],
    ["mef.journal", "INDETERMINATE", "neutral"],
    ["mef.journal", "ABANDONED", "neutral"],
    ["mef.journal", "fault", "waiting"],
    ["mef.ack", "Accepted", "safe"],
    ["mef.ack", "Rejected", "attention"],
    ["mef.ack", "Pending", "waiting"],
    ["mef.scenario", "accepted", "safe"],
    ["mef.scenario", "ready", "waiting"],
    ["mef.scenario", "partial", "attention"],
    ["mef.headerSource", "client", "neutral"],
    ["mef.headerSource", "pyramos", "safe"],
    ["mef.headerSource", "generated", "waiting"],
    ["air.stage", "composed", "waiting"],
    ["air.stage", "blocked", "attention"],
    ["air.stage", "sending", "waiting"],
    ["air.stage", "fault", "attention"],
    ["air.stage", "PROCESSING", "waiting"],
    ["air.stage", "ACCEPTED", "safe"],
    ["air.stage", "ACCEPTED_WITH_ERRORS", "attention"],
    ["air.stage", "PARTIALLY_ACCEPTED", "attention"],
    ["air.stage", "REJECTED", "attention"],
    ["air.stage", "NOT_FOUND", "attention"],
    ["ftb.state", "not_sent", "waiting"],
    ["ftb.state", "blocked", "waiting"],
    ["ftb.state", "pending", "waiting"],
    ["ftb.state", "submitted", "waiting"],
    ["ftb.state", "superseded", "waiting"],
    ["ftb.state", "accepted", "safe"],
    ["ftb.state", "rejected", "attention"],
    ["ftb.state", "accepted_with_errors", "attention"],
    ["ftb.state", "needs_recovery", "attention"],
    ["ftb.state", "nope", "waiting"],
    ["no.such", "accepted", "waiting"],
  ];
  assert.deepEqual(
    cases.map(([domain, state]) => toneFor(domain, state)),
    cases.map((row) => row[2]),
  );
});
