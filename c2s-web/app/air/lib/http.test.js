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
