import { NextResponse } from "next/server";
import { readScenarioPdf } from "../../../lib/scenarioCatalog";

export const dynamic = "force-dynamic";
export const runtime = "nodejs";

export async function GET(request, { params }) {
  const entry = request.nextUrl.searchParams.get("entry");
  const bytes = entry ? await readScenarioPdf(params.id, entry) : null;
  if (!bytes) return new NextResponse("Not found", { status: 404 });
  return new NextResponse(bytes, {
    headers: {
      "Content-Type": "application/pdf",
      "Content-Disposition": "inline",
    },
  });
}
