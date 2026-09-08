import { NextResponse } from "next/server";
import { currentUserId } from "@/lib/auth";

export const dynamic = "force-dynamic";

/** Hands the Gemini Live API key to the browser so the page can open the
 *  audio WebSocket directly. Without this, every browser would have to ship
 *  the key in the client bundle, which would let anyone scrape it.
 *  The key still lives only on the server — this route just vouches for
 *  the caller being a signed-in user before passing it on. */
export async function GET() {
  const id = await currentUserId();
  if (!id) return NextResponse.json({ error: "Unauthorized" }, { status: 401 });

  const key = process.env.GOOGLE_LIVE_API_KEY;
  if (!key) {
    return NextResponse.json(
      { error: "Hlasový režim nie je na tomto deployi aktívny. Chýba GOOGLE_LIVE_API_KEY." },
      { status: 503 },
    );
  }
  return NextResponse.json({ key });
}
