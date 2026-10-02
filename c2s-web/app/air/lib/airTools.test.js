const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");

function writeFake(dir, name, source) {
  const file = path.join(dir, name);
  fs.writeFileSync(file, source);
  fs.chmodSync(file, 0o755);
  return file;
}

function testConfig(dir, javaBin) {
  return {
    repoRoot: dir,
    airRoot: path.join(dir, "redesign", "air-a2a"),
    javaHome: path.join(dir, "java-home"),
    javaBin,
    jar: path.join(dir, "air-a2a-channel-0.1.0-SNAPSHOT.jar"),
    wsdlRoot: path.join(dir, "irs", "ty2026"),
    pkcs12: path.join(dir, "enrolled.p12"),
    passwordEnv: "AIR_P12_PASSWORD",
    asid: "1TBTBJ01",
    transmitterConfig: path.join(dir, "aats-transmitter.json"),
  };
}

test("runJava argv is an array with --env aats and an absolute --wsdl-root", async () => {
  const { runJava } = await import("./airTools.js");
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "air-tools-argv-"));
  const javaBin = writeFake(
    dir,
    "java",
    "#!/usr/bin/env node\nprocess.stdout.write(JSON.stringify({ argv: process.argv.slice(2) }));\n",
  );
  const cfg = testConfig(dir, javaBin);
  const result = await runJava(
    "preview",
    { form: "/tmp/form.xml", session: "/tmp/session.json", out: "/tmp/preview" },
    { config: cfg, javaBin },
  );
  assert.equal(result.exitCode, 0);
  assert.equal(Array.isArray(result.json.argv), true);
  const argv = result.json.argv;
  assert.equal(argv[0], "-jar");
  assert.equal(argv[2], "preview");
  assert.equal(argv[argv.indexOf("--env") + 1], "aats");
  assert.equal(path.isAbsolute(argv[argv.indexOf("--wsdl-root") + 1]), true);
  assert.equal(argv[argv.indexOf("--wsdl-root") + 1], cfg.wsdlRoot);
  assert.equal(argv.includes("--env aats"), false);
  fs.rmSync(dir, { recursive: true, force: true });
});

test("runJava returns exitCode null when the process is killed", async () => {
  const { runJava } = await import("./airTools.js");
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "air-tools-kill-"));
  const javaBin = writeFake(
    dir,
    "java",
    '#!/usr/bin/env node\nprocess.kill(process.pid, "SIGKILL");\n',
  );
  const result = await runJava("preview", { form: "/tmp/form.xml" }, {
    config: testConfig(dir, javaBin),
    javaBin,
  });
  assert.equal(result.exitCode, null);
  fs.rmSync(dir, { recursive: true, force: true });
});

test("validateErrors keeps every line of a multi-line xmllint error", async () => {
  const { validateErrors } = await import("./airTools.js");
  const stdout = "FAIL /runs/r1/form.xml\n  schema: form.xml:3: element Foo: not expected\nform.xml fails to validate\n  empty tag: Bar\n";
  assert.deepEqual(validateErrors(stdout, "", 1), [
    "schema: form.xml:3: element Foo: not expected\nform.xml fails to validate",
    "empty tag: Bar",
  ]);
});
