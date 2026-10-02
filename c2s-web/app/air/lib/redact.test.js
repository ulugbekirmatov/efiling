const { test } = require("node:test");
const assert = require("node:assert/strict");

test("maskSsn keeps the last four digits of every SSN element", async () => {
  const { maskSsn } = await import("./redact.js");
  const xml = "<a><SSN>123456789</SSN><irs:SSN attr=\"1\">987-65-4321</irs:SSN><Other>123456789</Other></a>";
  assert.equal(
    maskSsn(xml),
    "<a><SSN>*****6789</SSN><irs:SSN attr=\"1\">*****4321</irs:SSN><Other>123456789</Other></a>",
  );
});

test("maskSsn leaves empty, self-closing, and lookalike elements alone", async () => {
  const { maskSsn } = await import("./redact.js");
  const xml = "<SSN></SSN><SSN/><SSNType>123456789</SSNType><MissingSSNReasonCd>123456789</MissingSSNReasonCd>";
  assert.equal(maskSsn(xml), xml);
});

test("maskSsn hides a value with fewer than four digits entirely", async () => {
  const { maskSsn } = await import("./redact.js");
  assert.equal(maskSsn("<SSN>12</SSN>"), "<SSN>*****</SSN>");
});

test("redactCredentials replaces signature, certificate, token, assertion, and username token content", async () => {
  const { redactCredentials } = await import("./redact.js");
  const xml = [
    "<ds:SignatureValue Id=\"s\">abc==</ds:SignatureValue>",
    "<ds:X509Certificate>MIIC</ds:X509Certificate>",
    "<wsse:BinarySecurityToken ValueType=\"x\">tok</wsse:BinarySecurityToken>",
    "<saml2:Assertion ID=\"a\"><saml2:Subject>who</saml2:Subject></saml2:Assertion>",
    "<wsse:UsernameToken><wsse:Username>u</wsse:Username><wsse:Password>p</wsse:Password></wsse:UsernameToken>",
    "<keep>visible</keep>",
  ].join("");
  assert.equal(
    redactCredentials(xml),
    [
      "<ds:SignatureValue Id=\"s\">[redacted]</ds:SignatureValue>",
      "<ds:X509Certificate>[redacted]</ds:X509Certificate>",
      "<wsse:BinarySecurityToken ValueType=\"x\">[redacted]</wsse:BinarySecurityToken>",
      "<saml2:Assertion ID=\"a\">[redacted]</saml2:Assertion>",
      "<wsse:UsernameToken>[redacted]</wsse:UsernameToken>",
      "<keep>visible</keep>",
    ].join(""),
  );
});

test("redactCredentials does not let a self-closing element swallow later markup", async () => {
  const { redactCredentials } = await import("./redact.js");
  const xml = "<saml:Assertion/><keep>1</keep><saml:Assertion>x</saml:Assertion>";
  assert.equal(redactCredentials(xml), "<saml:Assertion/><keep>1</keep><saml:Assertion>[redacted]</saml:Assertion>");
});

test("redactSecrets replaces every occurrence and ignores empty or missing secrets", async () => {
  const { redactSecrets } = await import("./redact.js");
  assert.equal(redactSecrets("pw=hunter2 and hunter2", ["hunter2", "", undefined, null]), "pw=[redacted] and [redacted]");
  assert.equal(redactSecrets("nothing here", []), "nothing here");
  assert.equal(redactSecrets("nothing here", undefined), "nothing here");
});

test("redactSecrets replaces the longer secret first when one contains the other", async () => {
  const { redactSecrets } = await import("./redact.js");
  assert.equal(redactSecrets("abcd abc", ["abc", "abcd"]), "[redacted] [redacted]");
});

test("redactDocument masks SSNs and credentials together", async () => {
  const { redactDocument } = await import("./redact.js");
  const xml = "<SSN>123456789</SSN><ds:SignatureValue>zzz</ds:SignatureValue>";
  assert.equal(redactDocument(xml), "<SSN>*****6789</SSN><ds:SignatureValue>[redacted]</ds:SignatureValue>");
});
