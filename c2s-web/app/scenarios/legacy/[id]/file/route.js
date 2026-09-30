import { readFile } from "node:fs/promises";
import { NextResponse } from "next/server";
import { resolveLegacyFile } from "../../../scenarioData";

export async function GET(request, { params }) {
  const resolved = resolveLegacyFile(params.id);
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
  const disposition = download
    ? `attachment; filename="${resolved.fileName}"`
    : "inline";
  return new NextResponse(bytes, {
    headers: {
      "Content-Type": resolved.contentType,
      "Content-Disposition": disposition,
    },
  });
}
