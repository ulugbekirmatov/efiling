import path from "node:path";
import { runJava } from "../../../../lib/airTools.js";
import { readiness } from "../../../../lib/airConfig.js";
import { json, apiError, requireSameOrigin, airNotConfigured } from "../../../../lib/http.js";
import { canCheckStatus, isRunId, RUN_FILES, statusCheckOpensAt } from "../../../../lib/runModel.js";
import { readRun, runDir, statusCheckPaths, writeStatusCheck } from "../../../../lib/runStore.js";

export const dynamic = "force-dynamic";
export const runtime = "nodejs";

const checkingRunIds = new Set();

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

  if (run.stage !== "PROCESSING") {
    return apiError(409, "NOT_PROCESSING", `Status checks run only while IRS is processing; this run is ${run.stage}`);
  }
  if (!run.receiptId) {
    return apiError(409, "NO_RECEIPT", "This run has no Receipt ID to check");
  }
  const now = Date.now();
  if (!canCheckStatus(run, now)) {
    const opensAt = statusCheckOpensAt(run);
    const when = opensAt != null ? new Date(opensAt).toISOString() : "unknown";
    return apiError(409, "STATUS_NOT_OPEN", `Status check opens at ${when}`);
  }

  const missing = airNotConfigured(await readiness());
  if (missing) return missing;

  if (checkingRunIds.has(id)) {
    return apiError(409, "STATUS_IN_PROGRESS", "A status check is already running for this run");
  }
  checkingRunIds.add(id);
  try {
    return await checkStatus(id, run, now);
  } finally {
    checkingRunIds.delete(id);
  }
}

async function checkStatus(id, run, now) {
  const dir = await runDir(id);
  const paths = await statusCheckPaths(id, now);
  const out = await runJava("status", {
    "receipt-id": run.receiptId,
    session: path.join(dir, RUN_FILES.session.path),
    "save-response": paths.responsePath,
  });
  await writeStatusCheck(paths.recordPath, {
    exitCode: out.exitCode,
    result: out.json,
    stderr: out.stderr,
    at: new Date(now).toISOString(),
  });
  return json(await readRun(id));
}
