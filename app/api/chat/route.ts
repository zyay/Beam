import { NextRequest, NextResponse } from "next/server";
import { requestUserId } from "@/lib/auth";
import { CRISIS_RESPONSE, CRISIS_RX, FALLBACKS, SYSTEM } from "@/lib/prompts";

/** Crisis signals — the model never answers these; a fixed protocol does.
 * Matched on diacritic-stripped lowercase text (users type without diacritics). */
const stripDiacritics = (s: string) =>
  s.normalize("NFD").replace(/[\u0300-\u036f]/g, "").toLowerCase();

type Msg = { role: "user" | "assistant"; content: string };

// Vercel Hobby limit (s); the fetch below fails gracefully before this.
export const maxDuration = 60;

export async function POST(req: NextRequest) {
  const id = await requestUserId(req);
  if (!id) {
    return NextResponse.json({ error: FALLBACKS.unauthenticated }, { status: 401 });
  }

  let messages: Msg[] = [];
  let stream = false;
  try {
    const body = await req.json();
    messages = Array.isArray(body.messages) ? body.messages : [];
    stream = body.stream === true;
  } catch {
    return NextResponse.json({ error: FALLBACKS.badRequest }, { status: 400 });
  }

  // keep only the last 12 turns, both roles, sane sizes
  messages = messages
    .filter((m) => (m.role === "user" || m.role === "assistant") && typeof m.content === "string")
    .map((m) => ({ role: m.role, content: m.content.slice(0, 2000) }))
    .slice(-12);

  const last = messages[messages.length - 1];
  if (!last || last.role !== "user") {
    return NextResponse.json({ error: FALLBACKS.missingMessage }, { status: 400 });
  }

  // crisis guard: fixed protocol, never the model
  if (CRISIS_RX.test(stripDiacritics(last.content))) {
    return NextResponse.json({ text: CRISIS_RESPONSE, crisis: true });
  }

  // Vercel AI Gateway key (Settings → Environment Variables → AI_GATEWAY_KEY)
  const token = process.env.AI_GATEWAY_KEY;
  if (!token) {
    return NextResponse.json({ text: FALLBACKS.noKey }, { status: 503 });
  }

  const payload = {
    model: process.env.AI_GATEWAY_MODEL || "minimax/minimax-m3",
    messages: [{ role: "system", content: SYSTEM }, ...messages],
    max_tokens: 1000,
    temperature: 0.6,
    ...(stream ? { stream: true } : {}),
  };

  try {
    const r = await fetch("https://ai-gateway.vercel.sh/v1/chat/completions", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${token}`,
      },
      body: JSON.stringify(payload),
      // finish inside the 60 s function limit
      signal: AbortSignal.timeout(55_000),
    });
    if (!r.ok) {
      const detail = (await r.text()).slice(0, 200);
      console.error("AI Gateway error", r.status, detail);
      return NextResponse.json({ text: FALLBACKS.upstreamError }, { status: 502 });
    }
    // streaming clients (native app): pass the gateway's SSE straight through
    if (stream && r.body) {
      return new Response(r.body, {
        headers: {
          "Content-Type": "text/event-stream; charset=utf-8",
          "Cache-Control": "no-cache, no-transform",
          Connection: "keep-alive",
        },
      });
    }
    const data = await r.json();
    const text: string =
      (data.choices?.[0]?.message?.content || "").trim() || FALLBACKS.emptyResponse;
    return NextResponse.json({ text });
  } catch (e) {
    console.error("chat error", e);
    return NextResponse.json({ text: FALLBACKS.timeout }, { status: 504 });
  }
}
