import { NextRequest, NextResponse } from "next/server";
import { requestUserId } from "@/lib/auth";

/** Crisis signals — the model never answers these; a fixed protocol does.
 * Matched on diacritic-stripped lowercase text (users type without diacritics). */
const stripDiacritics = (s: string) =>
  s.normalize("NFD").replace(/[\u0300-\u036f]/g, "").toLowerCase();

const CRISIS_RX =
  /(nechcem\s+(uz\s+)?zit|nemam\s+(chut|silu)\s+zi(t|t)|skoncit\s+(so zivo|to)|zabit\s+sa|sebavraz\w*|suicid\w*|zomrie(t|t)|umrie(t|t)|prehltn\w*\s+(table|pilul)|chcem\s+zomrie|kill\s+myself|end\s+my\s+life|want\s+to\s+die)/;

const CRISIS_RESPONSE = `To, čo cítiš, je veľmi vážne a nie si v tom sám/sama. Prosím, zavolaj teraz na **Linku krízy: 0800 900 900** (nonstop, zdarma) alebo napíš **IPčko: 0800 500 500**. V okamžom ohrození volaj **112**.

Som len chatbot a v takejto chvíli ti musí pomôcť človek — sú to ľudia, ktorí rozumejú a nikto ťa za to neodsúdi. Držím si za teba palce.`;

const SYSTEM = `Si Beam — priateľský, empatický spoločník na rozhovor o duševnej pohode.
- Odpovedaj v jazyku používateľa (predvolene po slovensky), 2–5 viet, prirodzene a s teplom.
- Aktívne počúvaj, validuj pocity a opatrne sa pýtaj jednu doplňujúcu otázku.
- Faktické otázky zodpovedaj pravdivo; ak niečo nevieš, povedz to úprimne a nevymýšľaj si.
- Nikdy nediagnostikuj a neposkytuj lekárske rady — nie si terapeut ani zdravotnícka pomôcka.
- Ak sa používateľ vyjadruje o sebapoškodzovaní alebo samovražedných myšlienkach, pokojne uveď Linku krízy 0800 900 900 (Slovensko, nonstop), IPčko 0800 500 500 a 112, a povzbuď ho obrátiť sa na blízkeho človeka.`;

type Msg = { role: "user" | "assistant"; content: string };

// Vercel Hobby limit (s); the fetch below fails gracefully before this.
export const maxDuration = 60;

export async function POST(req: NextRequest) {
  const id = await requestUserId(req);
  if (!id) {
    return NextResponse.json({ error: "Neprihlásený." }, { status: 401 });
  }

  let messages: Msg[] = [];
  try {
    const body = await req.json();
    messages = Array.isArray(body.messages) ? body.messages : [];
  } catch {
    return NextResponse.json({ error: "Zlý formát." }, { status: 400 });
  }

  // keep only the last 12 turns, both roles, sane sizes
  messages = messages
    .filter((m) => (m.role === "user" || m.role === "assistant") && typeof m.content === "string")
    .map((m) => ({ role: m.role, content: m.content.slice(0, 2000) }))
    .slice(-12);

  const last = messages[messages.length - 1];
  if (!last || last.role !== "user") {
    return NextResponse.json({ error: "Chýba správa." }, { status: 400 });
  }

  // crisis guard: fixed protocol, never the model
  if (CRISIS_RX.test(stripDiacritics(last.content))) {
    return NextResponse.json({ text: CRISIS_RESPONSE, crisis: true });
  }

  const token = process.env.HF_TOKEN;
  if (!token) {
    return NextResponse.json(
      { text: "Chat nie je pripojený — chýba HF_TOKEN na serveri. Nastav ho vo Verceli (Settings → Environment Variables)." },
      { status: 503 }
    );
  }

  const payload = {
    model: process.env.HF_MODEL || "deepseek-ai/DeepSeek-V4-Flash-Vision-Exp",
    messages: [{ role: "system", content: SYSTEM }, ...messages],
    max_tokens: 1000,
    temperature: 0.6,
    reasoning_effort: "low",
  };

  try {
    const r = await fetch("https://router.huggingface.co/v1/chat/completions", {
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
      console.error("HF error", r.status, detail);
      return NextResponse.json(
        { text: "Chat služba teraz neodpovedá. Skús to o chvíľu znova." },
        { status: 502 }
      );
    }
    const data = await r.json();
    const text: string =
      (data.choices?.[0]?.message?.content || "").trim() ||
      "Práve sa mi nechcú slová skladnúť. Skús to ešte raz.";
    return NextResponse.json({ text });
  } catch (e) {
    console.error("chat error", e);
    return NextResponse.json(
      { text: "Chat služba je momentálne preťažená. Skús to o chvíľu." },
      { status: 504 }
    );
  }
}
