import { readFile } from "node:fs/promises";
import { NextResponse } from "next/server";
import { loadRegistry, resolveScenarioFile } from "../../../scenarioData";

export async function GET(request, { params }) {
  const resolved = resolveScenarioFile(params.number, params.kind);
  if (!resolved) {
    return new NextResponse("Not found", { status: 404 });
  }
  let bytes;
  try {
    bytes = await readFile(resolved.absPath);
  } catch {
    return new NextResponse("Not found", { status: 404 });
  }
  const download = request.nextUrl.searchParams.get("download") === "1";
  let disposition = "inline";
  if (download) {
    const registry = loadRegistry();
    const scenario = registry.scenarios.find((item) => item.number === Number(params.number));
    const prefix = scenario ? scenario.dir : "scenario";
    disposition = `attachment; filename="${prefix}-${resolved.fileName}"`;
  }
  return new NextResponse(bytes, {
    headers: {
      "Content-Type": resolved.contentType,
      "Content-Disposition": disposition,
    },
  });
}
