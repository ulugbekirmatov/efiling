const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const APP_DIR = __dirname;

function pageFiles(dir) {
  return fs.readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      if (entry.name.startsWith("[") && entry.name.endsWith("]")) return [];
      return pageFiles(full);
    }
    return entry.name === "page.js" ? [full] : [];
  });
}

function isRedirectOnly(file) {
  const text = fs.readFileSync(file, "utf8");
  return /\bredirect\(/.test(text) && !text.includes("<");
}

function hrefFor(file) {
  const rel = path.relative(APP_DIR, path.dirname(file));
  if (rel === "") return null;
  return `/${rel.split(path.sep).join("/")}`;
}

test("PAGES matches content page.js files", async () => {
  const { PAGES } = await import("./registry.js");
  const fromFiles = pageFiles(APP_DIR)
    .filter((file) => path.relative(APP_DIR, file) !== "page.js")
    .filter((file) => !isRedirectOnly(file))
    .map(hrefFor)
    .sort();
  const fromRegistry = PAGES.map((page) => page.href).sort();
  assert.deepEqual(fromRegistry, fromFiles);
  for (const page of PAGES) {
    assert.equal(fs.existsSync(path.join(APP_DIR, page.href.slice(1), "page.js")), true, page.href);
  }
});
