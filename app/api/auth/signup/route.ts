import { NextRequest, NextResponse } from "next/server";
import bcrypt from "bcryptjs";
import { createUser, getUserByEmail } from "@/lib/store";
import { createSessionToken, SESSION_COOKIE } from "@/lib/auth";

export async function POST(req: NextRequest) {
  try {
    const { email, password, name } = await req.json();
    if (!email || !/.+@.+\..+/.test(String(email))) {
      return NextResponse.json({ error: "Neplatný e-mail." }, { status: 400 });
    }
    if (!password || String(password).length < 8) {
      return NextResponse.json(
        { error: "Heslo musí mať aspoň 8 znakov." },
        { status: 400 }
      );
    }
    const existing = await getUserByEmail(String(email));
    if (existing) {
      return NextResponse.json(
        { error: "Tento e-mail už má účet. Skús sa prihlásiť." },
        { status: 409 }
      );
    }
    const hash = await bcrypt.hash(String(password), 10);
    const user = await createUser(
      String(email),
      hash,
      name ? String(name).slice(0, 60) : null
    );
    const token = await createSessionToken(user.id, user.email);
    const res = NextResponse.json({ ok: true, hasProfile: false });
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
    console.error("signup error", e);
    return NextResponse.json({ error: "Registrácia sa nepodarila." }, { status: 500 });
  }
}
