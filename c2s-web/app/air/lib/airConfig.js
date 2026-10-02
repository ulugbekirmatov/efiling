import fs from "node:fs";
import os from "node:os";
import path from "node:path";

const DEFAULT_JAVA_HOME = "/opt/homebrew/Cellar/openjdk/26.0.2.1/libexec/openjdk.jdk/Contents/Home";
const DEFAULT_PASSWORD_ENV = "AIR_P12_PASSWORD";
const PKCS12_VAR = "AIR_PKCS12";
const ASID_VAR = "AIR_ASID";

function fromEnv(name) {
  const value = process.env[name];
  return value ? value : null;
}

// Read on every call so a test or an operator can change the environment without a restart.
export function airConfig() {
  const repoRoot = path.resolve(fromEnv("AIR_REPO_ROOT") || path.join(os.homedir(), "Documents", "aca"));
  const airRoot = path.join(repoRoot, "redesign", "air-a2a");
  const javaHome = path.resolve(fromEnv("AIR_JAVA_HOME") || fromEnv("JAVA_HOME") || DEFAULT_JAVA_HOME);
  const pkcs12 = fromEnv(PKCS12_VAR);
  return {
    repoRoot,
    airRoot,
    runsDir: path.resolve(fromEnv("AIR_RUNS_DIR") || path.join(os.homedir(), ".air-operator", "runs")),
    javaHome,
    javaBin: path.join(javaHome, "bin", "java"),
    jar: path.resolve(fromEnv("AIR_JAR") || path.join(airRoot, "java", "target", "air-a2a-channel-0.1.0-SNAPSHOT.jar")),
    wsdlRoot: path.join(airRoot, "irs", "ty2026"),
    pkcs12: pkcs12 ? path.resolve(pkcs12) : null,
    passwordEnv: fromEnv("AIR_P12_PASSWORD_ENV") || DEFAULT_PASSWORD_ENV,
    asid: fromEnv(ASID_VAR),
    transmitterConfig: path.join(airRoot, "fixtures", "aats-transmitter.json"),
    scenariosDir: path.join(airRoot, "fixtures", "aats"),
    pdfZip: path.join(repoRoot, "irs-air", "2026 f1094595c.zip"),
  };
}

function exists(file, mode = fs.constants.F_OK) {
  try {
    fs.accessSync(file, mode);
    return true;
  } catch {
    return false;
  }
}

function onPath(binary) {
  const dirs = (process.env.PATH || "").split(path.delimiter).concat("/usr/bin");
  return dirs.some((dir) => dir && exists(path.join(dir, binary), fs.constants.X_OK));
}

// Booleans and variable names only. A secret value or the p12 path never leaves this function.
export function readiness() {
  const config = airConfig();
  return {
    repoRoot: exists(path.join(config.airRoot, "README.md")),
    jar: exists(config.jar),
    java: exists(config.javaBin, fs.constants.X_OK),
    xmllint: onPath("xmllint"),
    pkcs12: config.pkcs12 != null && exists(config.pkcs12),
    passwordEnvSet: Boolean(process.env[config.passwordEnv]),
    asid: config.asid != null,
    env: { passwordEnv: config.passwordEnv, pkcs12Var: PKCS12_VAR, asidVar: ASID_VAR },
  };
}
