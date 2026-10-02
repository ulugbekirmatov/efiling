import { json, apiError } from "../../../lib/http.js";
import { isRunId } from "../../../lib/runModel.js";
import { readDocuments, readRun } from "../../../lib/runStore.js";

export const dynamic = "force-dynamic";
export const runtime = "nodejs";

export async function GET(_request, { params }) {
  const { id } = params;
  if (!isRunId(id)) {
    return apiError(400, "INVALID_RUN_ID", "Invalid run id");
  }
  const run = await readRun(id);
  if (!run) {
    return apiError(404, "RUN_NOT_FOUND", "Run not found");
  }
  const docs = await readDocuments(id);
  return json({ ...run, ...docs });
}
