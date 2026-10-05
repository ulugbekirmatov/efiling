import { NextResponse } from "next/server";
import { readTestingSpecPdf } from "../../../lib/ftbCatalog";

export const dynamic = "force-dynamic";
export const runtime = "nodejs";

export async function GET() {
  const bytes = await readTestingSpecPdf();
  if (!bytes) return new NextResponse("Not found", { status: 404 });
  return new NextResponse(bytes, {
    headers: {
      "Content-Type": "application/pdf",
      "Content-Disposition": "inline",
    },
  });
}
