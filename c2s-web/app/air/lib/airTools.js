import { execFile as execFileCb } from "node:child_process";
import path from "node:path";

const TIMEOUT_MS = 180_000;
const MAX_BUFFER = 16 * 1024 * 1024;

async function resolveConfig(options) {
  if (options && options.config) return options.config;
  const { airConfig } = await import("./airConfig.js");
  return airConfig();
}

function exitCodeOf(err) {
  if (!err) return 0;
  if (err.killed || err.signal) return null;
  if (typeof err.code === "number") return err.code;
  if (err.code == null) return null;
  return 1;
}

function parseJson(stdout) {
  const text = String(stdout || "").trim();
  if (!text) return null;
  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}

function execCaptured(bin, argv, options) {
  const execFile = (options && options.execFile) || execFileCb;
  const cfgEnv = options.env || process.env;
  return new Promise((resolve) => {
    execFile(
      bin,
      argv,
      {
        cwd: options.cwd,
        env: cfgEnv,
        timeout: options.timeoutMs ?? TIMEOUT_MS,
        encoding: "utf8",
        shell: false,
        maxBuffer: MAX_BUFFER,
      },
      (err, stdout, stderr) => {
        let text = stderr || "";
        if (err && !err.killed && !err.signal && typeof err.code !== "number" && err.message) {
          text = text ? `${text}\n${err.message}` : err.message;
        }
        resolve({
          exitCode: exitCodeOf(err),
          stdout: stdout || "",
          stderr: text,
        });
      },
    );
  });
}

function flagArgs(flags) {
  const argv = [];
  for (const [name, value] of Object.entries(flags || {})) {
    if (value == null || value === false || value === "") continue;
    if (value === true) {
      argv.push(`--${name}`);
    } else {
      argv.push(`--${name}`, String(value));
    }
  }
  return argv;
}

export function javaArgv(subcommand, flags, cfg) {
  const argv = ["-jar", cfg.jar, subcommand, "--env", "aats", "--wsdl-root", path.resolve(cfg.wsdlRoot)];
  if (cfg.pkcs12) argv.push("--pkcs12", cfg.pkcs12);
  if (cfg.passwordEnv) argv.push("--password-env", cfg.passwordEnv);
  if (cfg.asid) argv.push("--asid", cfg.asid);
  argv.push(...flagArgs(flags));
  return argv;
}

function javaEnv(cfg) {
  const javaBinDir = path.join(cfg.javaHome, "bin");
  return {
    ...process.env,
    JAVA_HOME: cfg.javaHome,
    PATH: `${javaBinDir}${path.delimiter}${process.env.PATH || ""}`,
  };
}

export async function compose({ scenarioPath, outDir }, options = {}) {
  const cfg = await resolveConfig(options);
  const script = path.join(cfg.airRoot, "scripts", "compose-transmission.js");
  const argv = [
    script,
    "--scenario",
    scenarioPath,
    "--config",
    cfg.transmitterConfig,
    "--out",
    outDir,
  ];
  const ran = await execCaptured(options.nodeBin || process.execPath, argv, {
    cwd: cfg.repoRoot,
    timeoutMs: options.timeoutMs,
    execFile: options.execFile,
  });
  return { exitCode: ran.exitCode, json: parseJson(ran.stdout), stderr: ran.stderr };
}

function validateErrors(stdout, stderr, exitCode) {
  const errors = [];
  for (const line of String(stdout || "").split("\n")) {
    if (line.startsWith("  ")) errors.push(line.slice(2));
  }
  if (exitCode !== 0 && errors.length === 0) {
    const fallback = String(stderr || stdout || "validation failed").trim();
    if (fallback) errors.push(fallback);
  }
  return errors;
}

export async function validate({ formPath, manifestPath }, options = {}) {
  const cfg = await resolveConfig(options);
  const script = path.join(cfg.airRoot, "scripts", "validate-air.js");
  const nodeBin = options.nodeBin || process.execPath;
  const execOpts = {
    cwd: cfg.repoRoot,
    timeoutMs: options.timeoutMs,
    execFile: options.execFile,
  };
  const formRan = await execCaptured(nodeBin, [script, formPath], execOpts);
  const manifestRan = await execCaptured(nodeBin, [script, "--manifest", manifestPath], execOpts);
  const files = [
    {
      path: formPath,
      kind: "form",
      ok: formRan.exitCode === 0,
      errors: validateErrors(formRan.stdout, formRan.stderr, formRan.exitCode),
    },
    {
      path: manifestPath,
      kind: "manifest",
      ok: manifestRan.exitCode === 0,
      errors: validateErrors(manifestRan.stdout, manifestRan.stderr, manifestRan.exitCode),
    },
  ];
  return {
    ok: files.every((file) => file.ok),
    files,
    at: new Date().toISOString(),
  };
}

export async function runJava(subcommand, flags, options = {}) {
  const cfg = await resolveConfig(options);
  const argv = javaArgv(subcommand, flags, cfg);
  const ran = await execCaptured(options.javaBin || cfg.javaBin, argv, {
    cwd: cfg.repoRoot,
    env: javaEnv(cfg),
    timeoutMs: options.timeoutMs,
    execFile: options.execFile,
  });
  return { exitCode: ran.exitCode, json: parseJson(ran.stdout), stderr: ran.stderr };
}
