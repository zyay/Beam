import { NextResponse } from "next/server";

/** Public health endpoint — the app uses this to detect a working backend
 *  before deciding whether to retry. No auth, no side effects. */
export const dynamic = "force-dynamic";

export async function GET() {
  return NextResponse.json({
    ok: true,
    time: new Date().toISOString(),
    hasGateway: Boolean(process.env.AI_GATEWAY_KEY),
    model: process.env.AI_GATEWAY_MODEL || "minimax/minimax-m3",
  });
}
