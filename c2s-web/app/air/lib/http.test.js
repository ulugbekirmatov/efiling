const { test } = require("node:test");
const assert = require("node:assert/strict");

function request(headers) {
  return { headers: new Headers(headers) };
}

test("requireSameOrigin allows the same host", async () => {
  const { requireSameOrigin } = await import("./http.js");
  assert.equal(
    requireSameOrigin(
      request({ origin: "http://localhost:3000", host: "localhost:3000" }),
    ),
    null,
  );
});

test("requireSameOrigin rejects a missing Origin", async () => {
  const { requireSameOrigin } = await import("./http.js");
  const res = requireSameOrigin(request({ host: "localhost:3000" }));
  assert.equal(res.status, 403);
  assert.deepEqual(await res.json(), {
    code: "CROSS_ORIGIN",
    message: "Origin host must match Host",
  });
});

test("requireSameOrigin rejects a different host", async () => {
  const { requireSameOrigin } = await import("./http.js");
  const res = requireSameOrigin(
    request({ origin: "http://evil.example:3000", host: "localhost:3000" }),
  );
  assert.equal(res.status, 403);
  assert.deepEqual(await res.json(), {
    code: "CROSS_ORIGIN",
    message: "Origin host must match Host",
  });
});

test("airNotConfigured names the first missing variable in a 503 body", async () => {
  const { airNotConfigured } = await import("./http.js");
  const env = { passwordEnv: "AIR_P12_PASSWORD", pkcs12Var: "AIR_PKCS12", asidVar: "AIR_ASID" };
  const ready = { repoRoot: true, java: true, jar: true, pkcs12: true, passwordEnvSet: true, asid: false, env };
  const response = airNotConfigured(ready);
  assert.equal(response.status, 503);
  assert.deepEqual(await response.json(), { code: "AIR_NOT_CONFIGURED", message: "Set AIR_ASID" });
  assert.equal(airNotConfigured({ ...ready, asid: true }), null);
});
