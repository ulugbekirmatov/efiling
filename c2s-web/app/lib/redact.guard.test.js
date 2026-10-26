const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

// Personal identifiers and session credentials are masked in one module, app/lib/redact.js, and a
// sibling repo's script is loaded through one helper, app/lib/nodeRequire.js. Three past slips
// (EmployeeSSN past an exact-name mask, SSNs quoted in IRS error prose, an SSN under a TIN label)
// each came from a page carrying its own copy of the masking. This test fails on a new copy.

const APP_ROOT = path.resolve(__dirname, "..");
const OWNERS = {
  "lib/redact.js": [
    [/\bSSN_MASK\b/, "the SSN mask constant"],
    [/\bfunction (?:maskedDigits|maskSsn|maskKnownSsns|redactCredentials|redactSecrets|redactDocument|redactMessage)\b/, "a redaction function"],
    [/<!-- credential omitted -->/, "a credential placeholder"],
    [/\bSSN(?:\\s|\(\?:)/, "an SSN element regex"],
  ],
  "lib/nodeRequire.js": [
    [/__non_webpack_require__|createRequire\(/, "a webpack-safe require"],
  ],
};

function appFiles(dir) {
  return fs.readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) return entry.name === "node_modules" ? [] : appFiles(full);
    return entry.name.endsWith(".js") && !entry.name.endsWith(".test.js") ? [full] : [];
  });
}

test("redaction and webpack-safe require each have one owner under app/", () => {
  const offenders = [];
  for (const file of appFiles(APP_ROOT)) {
    const relative = path.relative(APP_ROOT, file);
    const text = fs.readFileSync(file, "utf8");
    for (const [owner, patterns] of Object.entries(OWNERS)) {
      if (relative === owner) continue;
      for (const [pattern, what] of patterns) {
        if (pattern.test(text)) offenders.push(`app/${relative} defines ${what}; import it from app/${owner} instead`);
      }
    }
  }
  assert.deepEqual(offenders, []);
});
