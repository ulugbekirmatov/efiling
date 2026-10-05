import path from "node:path";
import { validate } from "../../../../lib/airTools.js";
import { json, apiError, requireSameOrigin } from "../../../../lib/http.js";
import { isRunId, RUN_FILES } from "../../../../lib/runModel.js";
import { readRun, runDir, writeValidate } from "../../../../lib/runStore.js";

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

  const dir = await runDir(id);
  const record = await validate({
    formPath: path.join(dir, RUN_FILES.form.path),
    manifestPath: path.join(dir, RUN_FILES.manifest.path),
  });
  await writeValidate(id, record);
  return json(await readRun(id));
}
