import { NextRequest, NextResponse } from "next/server";
import { currentUserId } from "@/lib/auth";
import { getUserById, updateProfile, type Profile } from "@/lib/store";

export async function PUT(req: NextRequest) {
  const id = await currentUserId();
  if (!id) return NextResponse.json({ error: "Neprihlásený." }, { status: 401 });
  const body = (await req.json()) as { profile: Profile; name?: string };
  const profile: Profile = {
    mood: body.profile?.mood,
    stressors: Array.isArray(body.profile?.stressors) ? body.profile.stressors.slice(0, 12) : [],
    goals: Array.isArray(body.profile?.goals) ? body.profile.goals.slice(0, 12) : [],
    cadence: body.profile?.cadence,
    time: body.profile?.time,
  };
  const user = await updateProfile(
    id,
    profile,
    body.name !== undefined ? String(body.name).slice(0, 60) : undefined
  );
  if (!user) return NextResponse.json({ error: "Účet neexistuje." }, { status: 404 });
  return NextResponse.json({ ok: true, name: user.name, profile: user.profile });
}

export async function GET() {
  const id = await currentUserId();
  if (!id) return NextResponse.json({ error: "Neprihlásený." }, { status: 401 });
  const user = await getUserById(id);
  if (!user) return NextResponse.json({ error: "Účet neexistuje." }, { status: 404 });
  return NextResponse.json({ name: user.name, profile: user.profile, email: user.email });
}
