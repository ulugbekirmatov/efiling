import path from "node:path";
import { runJava } from "../../../../lib/airTools.js";
import { readiness } from "../../../../lib/airConfig.js";
import { json, apiError, requireSameOrigin, airNotConfigured } from "../../../../lib/http.js";
import { isRunId, RUN_FILES } from "../../../../lib/runModel.js";
import { redactMessage } from "../../../../lib/redact.js";
import { readRun, runDir, secretValues } from "../../../../lib/runStore.js";

export const dynamic = "force-dynamic";
export const runtime = "nodejs";

export async function POST(request, { params }) {
  const denied = requireSameOrigin(request);
  if (denied) return denied;

  const { id } = params;
  if (!isRunId(id)) {
    return apiError(400, "INVALID_RUN_ID", "Invalid run id");
  }
  const run = await readRun(id);
  if (!run) {
    return apiError(404, "RUN_NOT_FOUND", "Run not found");
  }

  const missing = airNotConfigured(await readiness());
  if (missing) return missing;

  const dir = await runDir(id);
  const out = await runJava("preview", {
    form: path.join(dir, RUN_FILES.form.path),
    session: path.join(dir, RUN_FILES.session.path),
    out: path.join(dir, path.dirname(RUN_FILES.previewMime.path)),
  });
  if (out.exitCode !== 0) {
    const reason = redactMessage(out.stderr || `java exited with ${out.exitCode}`, secretValues());
    return apiError(422, "PREVIEW_FAILED", reason);
  }
  return json(await readRun(id));
}
