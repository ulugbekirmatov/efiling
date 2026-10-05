import { rm } from "node:fs/promises";
import { compose } from "../../lib/airTools.js";
import { json, apiError, requireSameOrigin } from "../../lib/http.js";
import { redactMessage } from "../../lib/redact.js";
import { commitComposedRun, listRuns, newRunId, readRun, stagingDir } from "../../lib/runStore.js";
import { scenarioFile } from "../../lib/scenarioCatalog.js";

export const dynamic = "force-dynamic";
export const runtime = "nodejs";

export async function GET() {
  return json(await listRuns());
}

export async function POST(request) {
  const denied = requireSameOrigin(request);
  if (denied) return denied;

  let body = {};
  try {
    body = await request.json();
  } catch {
    body = {};
  }
  const scenarioId = body && body.scenarioId;
  const scenarioPath = await scenarioFile(scenarioId);
  if (!scenarioPath) {
    return apiError(404, "SCENARIO_NOT_FOUND", `Unknown scenario: ${scenarioId || ""}`);
  }

  const staging = await stagingDir();
  try {
    const composed = await compose({ scenarioPath, outDir: staging });
    if (composed.exitCode !== 0) {
      await rm(staging, { recursive: true, force: true });
      return apiError(422, "COMPOSE_FAILED", redactMessage(composed.stderr || ""));
    }
    const now = new Date();
    const id = await newRunId(scenarioId, now);
    await commitComposedRun(id, staging, {
      scenarioId,
      composedAt: now.toISOString(),
      exitCode: composed.exitCode,
      result: composed.json,
    });
    return json(await readRun(id));
  } catch (err) {
    await rm(staging, { recursive: true, force: true });
    throw err;
  }
}
