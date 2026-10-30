const { test } = require("node:test");
const assert = require("node:assert/strict");

test("session status is a GET and login and logout are POSTs", async () => {
  const calls = [];
  const original = global.fetch;
  global.fetch = async (path, options) => {
    calls.push({ path, method: (options && options.method) || "GET" });
    return {
      ok: true,
      status: 200,
      text: async () => JSON.stringify({ loggedIn: false }),
    };
  };
  try {
    const api = await import("./api.js");
    await api.getSession();
    await api.login();
    await api.logout();
    assert.deepEqual(calls, [
      { path: "/backend/mef/session", method: "GET" },
      { path: "/backend/mef/session/login", method: "POST" },
      { path: "/backend/mef/session/logout", method: "POST" },
    ]);
  } finally {
    global.fetch = original;
  }
});
