const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const APP_DIR = path.join(__dirname, "..");
const TOKENS_FILE = path.join(__dirname, "tokens.css");
const COLOR_LITERAL = /#[0-9a-f]{3,8}\b|\b(rgba?|hsla?|oklch|color-mix)\(/i;
const NOT_A_COLOR = /\/\*.*?\*\/|\/\/.*$|(href=["']|url\()#/g;
const SOURCE_FILE = /\.(js|css)$/;

function sourceFiles(dir) {
  return fs.readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) return sourceFiles(full);
    return SOURCE_FILE.test(entry.name) && !entry.name.endsWith(".test.js") ? [full] : [];
  });
}

test("color literals live only in design-system/tokens.css", () => {
  const offenders = sourceFiles(APP_DIR)
    .filter((file) => file !== TOKENS_FILE)
    .flatMap((file) =>
      fs
        .readFileSync(file, "utf8")
        .split("\n")
        .map((line, index) => ({ line, at: `${path.relative(APP_DIR, file)}:${index + 1}` }))
        .filter(({ line }) => COLOR_LITERAL.test(line.replace(NOT_A_COLOR, "")))
        .map(({ at, line }) => `${at}  ${line.trim()}`),
    );
  assert.deepEqual(offenders, []);
});

test("tokenizeXml tags names, attributes, values, and comments", async () => {
  const { tokenizeXml } = await import("./xmlTokens.js");
  assert.deepEqual(tokenizeXml('<a x="1">t<!-- c --></a>'), [
    { kind: "punct", text: "<" },
    { kind: "tag", text: "a" },
    { kind: "text", text: " " },
    { kind: "attr", text: "x" },
    { kind: "punct", text: "=" },
    { kind: "value", text: '"1"' },
    { kind: "punct", text: ">" },
    { kind: "text", text: "t" },
    { kind: "comment", text: "<!-- c -->" },
    { kind: "punct", text: "</" },
    { kind: "tag", text: "a" },
    { kind: "punct", text: ">" },
  ]);
});

test("tokenizeXml round-trips text with = and > outside tags", async () => {
  const { tokenizeXml } = await import("./xmlTokens.js");
  const xml = '<?xml version="1.0"?>\n<Note k="v"/>\n<Body>a=b "q" 2 > 1</Body>';
  const tokens = tokenizeXml(xml);
  assert.equal(tokens.map((t) => t.text).join(""), xml);
  assert.deepEqual(
    tokens.filter((t) => t.kind === "text").map((t) => t.text),
    ["\n", " ", "\n", 'a=b "q" 2 > 1'],
  );
});

test("tokenizeXml handles single quotes, spaced equals, and CDATA", async () => {
  const { tokenizeXml } = await import("./xmlTokens.js");
  const kinds = (xml) => tokenizeXml(xml).map((t) => [t.kind, t.text]);
  assert.deepEqual(kinds("<a x = '1>2'/>"), [
    ["punct", "<"],
    ["tag", "a"],
    ["text", " "],
    ["attr", "x"],
    ["punct", " = "],
    ["value", "'1>2'"],
    ["punct", "/>"],
  ]);
  assert.deepEqual(kinds("<r><![CDATA[a>b]]></r>"), [
    ["punct", "<"],
    ["tag", "r"],
    ["punct", ">"],
    ["text", "<![CDATA[a>b]]>"],
    ["punct", "</"],
    ["tag", "r"],
    ["punct", ">"],
  ]);
});
