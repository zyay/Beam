"use client";

import { useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import {
  Plus,
  PaperPlaneRight,
  PhoneCall,
  Warning,
} from "@phosphor-icons/react";
import { motion, AnimatePresence } from "framer-motion";
import dynamic from "next/dynamic";
import { BorderBeam } from "border-beam";

const Mascot = dynamic(() => import("@/components/mascot/Mascot"), { ssr: false });
import ThinkingOrb from "@/components/effects/ThinkingOrb";

type Msg = { role: "user" | "assistant"; content: string; crisis?: boolean };

const CRISIS_TEXT = `To, čo cítiš, je veľmi vážne a nie si v tom sám/sama. Prosím, zavolaj teraz na **Linku krízy: 0800 900 900** (nonstop, zdarma) alebo napíš **IPčko: 0800 500 500**. V okamžom ohrození volaj **112**.

Som len chatbot a v takejto chvíli ti musí pomôcť človek — sú to ľudia, ktorí rozumejú a nikto ťa za to neodsúdi. Držím si za teba palce.`;

const stripDiacritics = (s: string) =>
  s.normalize("NFD").replace(/[\u0300-\u036f]/g, "").toLowerCase();

const CRISIS_RX =
  /(nechcem\s+(uz\s+)?zit|nemam\s+(chut|silu)\s+zi(t|t)|skoncit\s+(so zivo|to)|zabit\s+sa|sebavraz\w*|suicid\w*|zomrie(t|t)|umrie(t|t)|prehltn\w*\s+(table|pilul)|chcem\s+zomrie|kill\s+myself|end\s+my\s+life|want\s+to\s+die)/;

const STORAGE_KEY = "beam.chat.v1";

export default function ChatClient({ name }: { name: string }) {
  const router = useRouter();
  const [messages, setMessages] = useState<Msg[]>([]);
  const [input, setInput] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const scrollRef = useRef<HTMLDivElement>(null);
  const bottomRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    try {
      const saved = localStorage.getItem(STORAGE_KEY);
      if (saved) setMessages(JSON.parse(saved));
    } catch {
      /* ignore */
    }
  }, []);

  useEffect(() => {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(messages.slice(-60)));
    } catch {
      /* ignore */
    }
    bottomRef.current?.scrollIntoView({ behavior: "smooth", block: "end" });
  }, [messages, busy]);

  async function send(text?: string) {
    const content = (text ?? input).trim();
    if (!content || busy) return;
    setError(null);
    setInput("");

    // client-side crisis guard: instant fixed response, no round-trip
    if (CRISIS_RX.test(stripDiacritics(content))) {
      setMessages((m) => [...m, { role: "user", content }, { role: "assistant", content: CRISIS_TEXT, crisis: true }]);
      return;
    }

    const history = [...messages, { role: "user" as const, content }];
    setMessages(history);
    setBusy(true);
    try {
      const res = await fetch("/api/chat", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          messages: history
            .filter((m) => !m.crisis)
            .slice(-12)
            .map(({ role, content }) => ({ role, content })),
        }),
      });
      const data = await res.json();
      if (!res.ok) {
        setError(data.text || data.error || "Služba nereaguje.");
        return;
      }
      setMessages((m) => [...m, { role: "assistant", content: data.text }]);
    } catch {
      setError("Spojenie prerušené. Skús to znova.");
    } finally {
      setBusy(false);
    }
  }

  function newChat() {
    if (busy) return;
    setMessages([]);
    setInput("");
  }

  return (
    <BorderBeam size="md" colorVariant="colorful" strength={0.7} theme="dark">
    <main className="relative mx-auto flex h-dvh max-w-[760px] flex-col px-4">
      {/* header */}
      <header className="flex items-center justify-between border-b border-line py-3.5">
        <div className="flex items-center gap-2">
          <span className="block h-8 w-8 overflow-hidden rounded-full bg-white/5">
            <Mascot size={32} animation="idle" />
          </span>
          <span className="font-semibold tracking-tight">Beam</span>
          <span className="text-sm text-fog">· {name}</span>
        </div>
        <button
          onClick={newChat}
          title="Nový rozhovor"
          className="rounded-full p-2.5 text-fog transition-colors hover:bg-white/5 hover:text-mist"
        >
          <Plus size={19} />
        </button>
      </header>

      {/* messages */}
      <div ref={scrollRef} className="flex-1 overflow-y-auto py-5">
        {messages.length === 0 && (
          <div className="flex h-full flex-col items-center justify-center gap-4 text-center">
            <span className="block h-28 w-28 overflow-hidden rounded-full">
              <Mascot size={112} animation="idle" />
            </span>
            <div>
              <p className="font-medium text-mist">Ahoj, {name}.</p>
              <p className="mt-1 max-w-[320px] text-sm leading-relaxed text-fog">
                Napíš mi hocikoľvek — ako sa máš, čo ťa trápi, alebo ti len urob spoločnosť.
              </p>
            </div>
          </div>
        )}

        <div className="flex flex-col gap-4">
          <AnimatePresence initial={false}>
            {messages.map((m, i) => (
              <motion.div
                key={i}
                initial={{ opacity: 0, y: 10 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.25 }}
                className={m.role === "user" ? "flex justify-end" : "flex justify-start"}
              >
                {m.crisis ? (
                  <CrisisCard />
                ) : (
                  <div
                    className={
                      m.role === "user"
                        ? "max-w-[85%] rounded-2xl rounded-br-md bg-[rgba(167,139,250,0.16)] px-4 py-3 text-[15px] leading-relaxed"
                        : "max-w-[88%] text-[15px] leading-relaxed text-mist"
                    }
                  >
                    <RichText text={m.content} />
                  </div>
                )}
              </motion.div>
            ))}
          </AnimatePresence>

          {busy && (
            <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="flex items-center gap-3">
              <span className="block h-10 w-10 overflow-hidden rounded-full bg-white/5">
                <Mascot size={40} animation="thinking" />
              </span>
              <span className="text-sm text-fog">Premýšľam…</span>
            </motion.div>
          )}

          {error && (
            <p className="rounded-xl border border-[rgba(255,110,130,0.3)] bg-[rgba(255,110,130,0.08)] px-4 py-2.5 text-sm text-[#ff9fb0]">
              {error}
            </p>
          )}
          <div ref={bottomRef} />
        </div>
      </div>

      {/* composer */}
      <form
        onSubmit={(e) => {
          e.preventDefault();
          send();
        }}
        className="sticky bottom-0 flex items-end gap-2 pb-5 pt-2"
        style={{
          background: "linear-gradient(to top, #060607 78%, transparent)",
        }}
      >
        <textarea
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter" && !e.shiftKey) {
              e.preventDefault();
              send();
            }
          }}
          rows={1}
          placeholder="Napíš správu…"
          className="field max-h-36 flex-1 resize-none py-3.5"
        />
        <button
          type="submit"
          disabled={busy || !input.trim()}
          title="Poslať"
          className="btn-beam !w-auto rounded-full !p-3.5"
        >
          <PaperPlaneRight size={19} weight="bold" />
        </button>
      </form>
    </main>
    </BorderBeam>
  );
}

function CrisisCard() {
  return (
    <BorderBeam size="sm" colorVariant="colorful" strength={0.7} theme="dark">
    <div className="w-full rounded-2xl border border-[rgba(255,143,214,0.35)] bg-[rgba(255,143,214,0.06)] p-4">
      <p className="flex items-center gap-2 text-sm font-semibold text-[#ffb3d9]">
        <Warning size={17} weight="fill" />
        Krízová pomoc
      </p>
      <div className="mt-3 flex flex-col gap-2 text-sm">
        <a href="tel:0800900900" className="flex items-center gap-2.5 rounded-xl border border-line bg-white/3 px-3.5 py-3 text-mist transition-colors hover:border-white/25">
          <PhoneCall size={17} className="text-beam-pink" weight="bold" />
          Linka krízy · <strong>0800 900 900</strong> · nonstop
        </a>
        <a href="tel:0800500500" className="flex items-center gap-2.5 rounded-xl border border-line bg-white/3 px-3.5 py-3 text-mist transition-colors hover:border-white/25">
          <PhoneCall size={17} className="text-beam-violet" weight="bold" />
          IPčko · <strong>0800 500 500</strong> · nonstop
        </a>
        <a href="tel:112" className="flex items-center gap-2.5 rounded-xl border border-line bg-white/3 px-3.5 py-3 text-mist transition-colors hover:border-white/25">
          <PhoneCall size={17} className="text-beam-mint" weight="bold" />
          Tiesňové volanie · <strong>112</strong>
        </a>
      </div>
      <p className="mt-3 text-xs leading-relaxed text-fog">
        Som len chatbot — v kríze ti musí pomôcť človek. Zavolaj, nie je to zlé rozhodnutie.
      </p>
    </div>
    </BorderBeam>
  );
}

/** tiny formatter: **bold**, newlines, phone numbers → tel: links */
function RichText({ text }: { text: string }) {
  const lines = text.split("\n");
  const phoneRx = /(0800\s?900\s?900|0800\s?500\s?500|\b112\b)/g;

  return (
    <>
      {lines.map((line, li) => {
        const parts: React.ReactNode[] = [];
        let rest = line;
        let key = 0;
        // bold segments
        const boldSplit = rest.split(/(\*\*[^*]+\*\*)/g);
        rest = "";
        boldSplit.forEach((seg, si) => {
          if (seg.startsWith("**") && seg.endsWith("**")) {
            parts.push(<strong key={`b${li}-${si}`}>{seg.slice(2, -2)}</strong>);
          } else {
            // phone links inside plain segments
            const pieces = seg.split(phoneRx);
            pieces.forEach((p, pi) => {
              if (!p) return;
              if (/^0?800[\s]?[0-9]{3}[\s]?[0-9]{3}$|^112$/.test(p.replace(/\s/g, ""))) {
                parts.push(
                  <a key={`t${li}-${si}-${pi}`} href={`tel:${p.replace(/\s/g, "")}`} className="underline underline-offset-2">
                    {p}
                  </a>
                );
              } else {
                parts.push(<span key={`s${li}-${si}-${pi}`}>{p}</span>);
              }
            });
          }
        });
        return (
          <span key={li} className="block">
            {parts.length ? parts : "\u00A0"}
          </span>
        );
      })}
    </>
  );
}
