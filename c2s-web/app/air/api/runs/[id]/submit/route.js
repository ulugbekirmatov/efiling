import path from "node:path";
import { runJava } from "../../../../lib/airTools.js";
import { readiness } from "../../../../lib/airConfig.js";
import { json, apiError, requireSameOrigin, airNotConfigured } from "../../../../lib/http.js";
import { canSubmit, confirmMatches, isRunId, RUN_FILES } from "../../../../lib/runModel.js";
import { beginSubmit, finishSubmit, readRun, runDir } from "../../../../lib/runStore.js";

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
  if (!canSubmit(run)) {
    return apiError(409, "NOT_SUBMITTABLE", "Run is not submittable");
  }

  let body = {};
  try {
    body = await request.json();
  } catch {
    body = {};
  }
  if (!confirmMatches(run, body && body.confirm)) {
    return apiError(403, "CONFIRM_MISMATCH", "Confirmation does not match");
  }

  const missing = airNotConfigured(await readiness());
  if (missing) return missing;

  const started = await beginSubmit(id);
  if (!started) {
    return apiError(409, "SUBMIT_IN_PROGRESS", "A submit is already in progress");
  }

  const dir = await runDir(id);
  const at = new Date().toISOString();
  let exitCode = null;
  let result = null;
  let stderr = "";
  try {
    const out = await runJava("submit", {
      form: path.join(dir, RUN_FILES.form.path),
      session: path.join(dir, RUN_FILES.session.path),
      "save-response": path.join(dir, RUN_FILES.submitResponse.path),
    });
    exitCode = out.exitCode;
    result = out.json;
    stderr = out.stderr;
  } catch (err) {
    stderr = err && err.message ? err.message : String(err);
  } finally {
    await finishSubmit(id, { exitCode, result, stderr, at });
  }

  return json(await readRun(id));
}
