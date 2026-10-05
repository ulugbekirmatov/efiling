import { json } from "../../lib/http.js";
import { readiness } from "../../lib/airConfig.js";

export const dynamic = "force-dynamic";
export const runtime = "nodejs";

export async function GET() {
  return json(await readiness());
}
