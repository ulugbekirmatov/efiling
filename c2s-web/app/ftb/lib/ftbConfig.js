import os from "node:os";
import path from "node:path";

const TESTING_SPEC_PDF = "2025-FTB-File-Exchange-System-1094-1095-Testing-Specifications.pdf";

// Read per call so tests can vary the environment.
export function ftbConfig() {
  const repoRoot = path.resolve(process.env.FTB_REPO_ROOT || path.join(os.homedir(), "Documents", "aca"));
  const ftbRoot = path.join(repoRoot, "ftb-fx");
  return {
    repoRoot,
    ftbRoot,
    operatorPageScript: path.join(ftbRoot, "scripts", "operator-page.js"),
    testingSpecPdf: path.join(ftbRoot, "ftb", TESTING_SPEC_PDF),
    previewRoot: path.join(os.tmpdir(), "c2s-web-ftb-preview"),
  };
}
