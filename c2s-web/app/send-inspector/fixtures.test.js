const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const dir = __dirname;
const files = ["fixtures.js", "SendInspector.js", "prettyXml.js", "page.js"];

test("inspector sources never include SAML assertions", () => {
  for (const file of files) {
    const text = fs.readFileSync(path.join(dir, file), "utf8");
    assert.equal(/saml:Assertion/i.test(text), false, file);
    assert.equal(/samlAssertion/i.test(text), false, file);
  }
});

test("fixtures describe MIME SOAP and omitted ZIP, not a log dump", () => {
  const text = fs.readFileSync(path.join(dir, "fixtures.js"), "utf8");
  assert.match(text, /multipart\/related/);
  assert.match(text, /application\/octet-stream/);
  assert.match(text, /credential omitted/);
  assert.equal(text.includes("SOAPMessage.writeTo"), false);
});
