import { NextResponse } from "next/server";
import { currentUserId } from "@/lib/auth";
import { getUserById } from "@/lib/store";

export async function GET() {
  const id = await currentUserId();
  if (!id) return NextResponse.json({ user: null }, { status: 401 });
  const user = await getUserById(id);
  if (!user) return NextResponse.json({ user: null }, { status: 401 });
  return NextResponse.json({
    user: {
      id: user.id,
      email: user.email,
      name: user.name,
      profile: user.profile,
      hasProfile: Boolean(user.profile),
    },
  });
}
