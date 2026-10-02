import path from "node:path";
import { apiError } from "../../../../../lib/http.js";
import { isRunId, RUN_FILES } from "../../../../../lib/runModel.js";
import { readRawFile } from "../../../../../lib/runStore.js";

export const dynamic = "force-dynamic";
export const runtime = "nodejs";

export async function GET(_request, { params }) {
  const { id, key } = params;
  if (!isRunId(id)) {
    return apiError(400, "INVALID_RUN_ID", "Invalid run id");
  }
  const file = RUN_FILES[key];
  const body = await readRawFile(id, key);
  if (!file || !body) {
    return apiError(404, "NOT_FOUND", "File not found");
  }
  const filename = `${id}-${path.basename(file.path)}`;
  return new Response(body, {
    headers: {
      "Content-Type": "application/octet-stream",
      "Content-Disposition": `attachment; filename="${filename}"`,
    },
  });
}
