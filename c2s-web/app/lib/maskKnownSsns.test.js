const { test } = require("node:test");
const assert = require("node:assert/strict");

// 123-45-6789 is the SSA's own example value, never issued.
const EXAMPLE_SSN = "123456789";

test("maskKnownSsns masks the given SSNs plain and dashed, and leaves other nine-digit values alone", async () => {
  const { maskKnownSsns } = await import("./redact.js");
  const text = `Holder ${EXAMPLE_SSN} or 123-45-6789 works for EIN 300000040.`;
  assert.equal(maskKnownSsns(text, [EXAMPLE_SSN]), "Holder *****6789 or *****6789 works for EIN 300000040.");
  assert.equal(maskKnownSsns(text, []), text);
  assert.equal(maskKnownSsns(text, ["123-45-6789"]), "Holder *****6789 or *****6789 works for EIN 300000040.");
  assert.equal(maskKnownSsns(null, [EXAMPLE_SSN]), null);
});
