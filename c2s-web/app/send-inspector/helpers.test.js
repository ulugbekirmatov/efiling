const { test } = require("node:test");
const assert = require("node:assert/strict");

test("parseApiError copies status, code, and message from the contract body", async () => {
  const { parseApiError } = await import("./api.js");
  const err = parseApiError(409, {
    code: "NOT_LOGGED_IN",
    message: "No IRS session",
  });
  assert.equal(err instanceof Error, true);
  assert.equal(err.status, 409);
  assert.equal(err.code, "NOT_LOGGED_IN");
  assert.equal(err.message, "No IRS session");
});

test("parseApiError reads errorCode when code is absent", async () => {
  const { parseApiError } = await import("./api.js");
  const err = parseApiError(500, {
    errorCode: "MEF_ERROR",
    message: "Login failed",
    detail: "keystore",
  });
  assert.equal(err.status, 500);
  assert.equal(err.code, "MEF_ERROR");
  assert.equal(err.message, "Login failed");
});

test("parseApiError prefers code over errorCode", async () => {
  const { parseApiError } = await import("./api.js");
  const err = parseApiError(409, {
    code: "NOT_LOGGED_IN",
    errorCode: "OTHER",
    message: "No IRS session",
  });
  assert.equal(err.code, "NOT_LOGGED_IN");
  assert.equal(err.message, "No IRS session");
});

test("parseApiError uses a literal fallback when the body has no message", async () => {
  const { parseApiError } = await import("./api.js");
  const err = parseApiError(502, { code: "ACK_RETRIEVAL_FAILED" });
  assert.equal(err.status, 502);
  assert.equal(err.code, "ACK_RETRIEVAL_FAILED");
  assert.equal(err.message, "Request failed (502)");
});

test("parseApiError treats a missing body as status-only", async () => {
  const { parseApiError } = await import("./api.js");
  const err = parseApiError(500, null);
  assert.equal(err.status, 500);
  assert.equal(err.code, null);
  assert.equal(err.message, "Request failed (500)");
});

test("isUnreachableError is true for fetch failures and non-JSON 5xx only", async () => {
  const { parseApiError, isUnreachableError } = await import("./api.js");
  assert.equal(isUnreachableError(parseApiError(0, { message: "Network error" }, false)), true);
  assert.equal(isUnreachableError(parseApiError(500, null)), true);
  assert.equal(
    isUnreachableError(parseApiError(500, { message: "Internal Server Error" }, false)),
    true
  );
  assert.equal(
    isUnreachableError(parseApiError(502, { code: "ACK_RETRIEVAL_FAILED" }, true)),
    false
  );
  assert.equal(
    isUnreachableError(
      parseApiError(500, { errorCode: "MEF_ERROR", message: "Login failed" }, true)
    ),
    false
  );
  assert.equal(isUnreachableError(parseApiError(409, { code: "NOT_LOGGED_IN", message: "x" })), false);
  assert.equal(isUnreachableError(parseApiError(404, { code: "ACK_NOT_FOUND", message: "x" })), false);
});

test("prettyXml indents nested tags", async () => {
  const { prettyXml } = await import("./prettyXml.js");
  assert.equal(prettyXml("<a><b>x</b></a>"), "<a>\n  <b>x</b>\n</a>");
});

test("prettyXml returns an empty string for blank input", async () => {
  const { prettyXml } = await import("./prettyXml.js");
  assert.equal(prettyXml(""), "");
  assert.equal(prettyXml(null), "");
});

test("redactCredentials replaces SAML and token elements", async () => {
  const { redactCredentials } = await import("./prettyXml.js");
  assert.equal(
    redactCredentials("<soap><saml:Assertion>secret</saml:Assertion></soap>"),
    "<soap><!-- credential omitted --></soap>"
  );
  assert.equal(
    redactCredentials("<h><wsse:UsernameToken>x</wsse:UsernameToken></h>"),
    "<h><!-- credential omitted --></h>"
  );
});
