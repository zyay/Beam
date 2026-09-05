import { NextRequest, NextResponse } from "next/server";
import bcrypt from "bcryptjs";
import { getUserByEmail } from "@/lib/store";
import { createSessionToken, SESSION_COOKIE } from "@/lib/auth";

export async function POST(req: NextRequest) {
  try {
    const { email, password } = await req.json();
    if (!email || !password) {
      return NextResponse.json(
        { error: "Zadaj e-mail aj heslo." },
        { status: 400 }
      );
    }
    const user = await getUserByEmail(String(email));
    if (!user || !(await bcrypt.compare(String(password), user.password_hash))) {
      return NextResponse.json(
        { error: "Nesprávny e-mail alebo heslo." },
        { status: 401 }
      );
    }
    const token = await createSessionToken(user.id, user.email);
    const res = NextResponse.json({ ok: true, hasProfile: Boolean(user.profile) });
    res.cookies.set(SESSION_COOKIE, token, {
      httpOnly: true,
      sameSite: "lax",
      secure: process.env.NODE_ENV === "production",
      maxAge: 60 * 60 * 24 * 30,
      path: "/",
    });
    return res;
  } catch (e) {
    if ((e as Error).message === "NO_DB") {
      return NextResponse.json(
        { error: "Databáza nie je pripojená — nastav POSTGRES_URL vo Verceli (README → Deploy)." },
        { status: 503 }
      );
    }
    console.error("login error", e);
    return NextResponse.json({ error: "Prihlásenie sa nepodarilo." }, { status: 500 });
  }
}
