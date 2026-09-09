import { NextRequest, NextResponse } from "next/server";
import { requestUserId } from "@/lib/auth";
import { listCheckins, upsertCheckin } from "@/lib/store";

export async function GET(req: NextRequest) {
  const id = await requestUserId(req);
  if (!id) return NextResponse.json({ error: "Neprihlásený." }, { status: 401 });
  const daysParam = Number(req.nextUrl.searchParams.get("days") ?? "90");
  const days = Number.isFinite(daysParam) ? Math.min(365, Math.max(7, daysParam)) : 90;
  const checkins = await listCheckins(id, days);
  return NextResponse.json({ checkins });
}

export async function POST(req: NextRequest) {
  const id = await requestUserId(req);
  if (!id) return NextResponse.json({ error: "Neprihlásený." }, { status: 401 });
  const body = (await req.json().catch(() => null)) as {
    mood?: number;
    note?: string;
    day?: string;
  } | null;
  if (!body || typeof body.mood !== "number" || !Number.isFinite(body.mood)) {
    return NextResponse.json({ error: "Chýba nálada." }, { status: 400 });
  }
  if (body.mood < 1 || body.mood > 5) {
    return NextResponse.json({ error: "Nálada musí byť 1–5." }, { status: 400 });
  }
  const checkin = await upsertCheckin(
    id,
    typeof body.day === "string" ? body.day : "",
    body.mood,
    typeof body.note === "string" ? body.note : null,
  );
  return NextResponse.json({ ok: true, checkin });
}
