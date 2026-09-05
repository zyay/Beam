"use client";

import { useRef, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { motion } from "framer-motion";
import { gsap } from "gsap";
import { useGSAP } from "@gsap/react";
import {
  EnvelopeSimple,
  Lock,
  User,
  PaperPlaneRight,
  ShieldCheck,
  Database,
  Cpu,
  LockKey,
  DeviceMobile,
  ArrowDown,
} from "@phosphor-icons/react";
import GlowCard from "@/components/effects/GlowCard";
import BlueprintGrid from "@/components/effects/BlueprintGrid";
import BeamMark from "@/components/BeamMark";
import dynamic from "next/dynamic";

const Mascot = dynamic(() => import("@/components/mascot/Mascot"), { ssr: false });
import ThinkingOrb from "@/components/effects/ThinkingOrb";

type Mode = "login" | "signup";

export default function LoginClient({ next }: { next: string }) {
  const router = useRouter();
  const [mode, setMode] = useState<Mode>("login");
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const root = useRef<HTMLDivElement>(null);
  const magnetRef = useRef<HTMLDivElement>(null);
  const btnRef = useRef<HTMLButtonElement>(null);

  useGSAP(
    () => {
      const reduced = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
      if (reduced) return;
      const tl = gsap.timeline({ defaults: { ease: "power3.out" } });
      tl.from(".nav-row", { y: -14, opacity: 0, duration: 0.5 })
        .from(".hero-eyebrow", { y: 14, opacity: 0, duration: 0.45 }, "-=0.2")
        .from(".hero-char", { y: 38, opacity: 0, stagger: 0.025, duration: 0.7 }, "-=0.25")
        .from(".hero-sub", { y: 14, opacity: 0, duration: 0.45 }, "-=0.45")
        .from(".bento-cell", {
          y: 34,
          opacity: 0,
          scale: 0.985,
          stagger: 0.09,
          duration: 0.7,
        }, "-=0.35")
        .from(".footer-row", { opacity: 0, duration: 0.5 }, "-=0.3");

      // idle float for the demo orb
      gsap.to(".demo-orb", {
        y: -6,
        duration: 2.2,
        ease: "sine.inOut",
        yoyo: true,
        repeat: -1,
        delay: 1.4,
      });
    },
    { scope: root },
  );

  const onMagnetMove = (e: React.MouseEvent) => {
    if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;
    const wrap = magnetRef.current;
    const btn = btnRef.current;
    if (!wrap || !btn) return;
    const r = wrap.getBoundingClientRect();
    const relX = (e.clientX - r.left - r.width / 2) / r.width;
    const relY = (e.clientY - r.top - r.height / 2) / r.height;
    gsap.to(btn, { x: relX * 18, y: relY * 10, duration: 0.4, ease: "power3.out" });
  };
  const onMagnetLeave = () => {
    gsap.to(btnRef.current, { x: 0, y: 0, duration: 0.6, ease: "elastic.out(1, 0.4)" });
  };

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      const res = await fetch(`/api/auth/${mode}`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(
          mode === "signup" ? { email, password, name } : { email, password },
        ),
      });
      const data = await res.json();
      if (!res.ok) {
        setError(data.error || "Niečo sa pokazilo.");
        return;
      }
      router.push(data.hasProfile ? "/chat" : next);
      router.refresh();
    } catch {
      setError("Server nereaguje. Skús to znova.");
    } finally {
      setBusy(false);
    }
  }

  const TITLE = "Kľud v každej správe";

  return (
    <main ref={root} className="relative min-h-dvh overflow-x-clip">
      <BlueprintGrid />

      <div className="mx-auto w-full max-w-6xl px-5 pb-16 pt-6">
        {/* ---- nav ---- */}
        <nav className="nav-row flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Mascot size={28} animation="idle" />
            <span className="text-[17px] font-semibold tracking-tight">
              Beam<span className="text-fog"> · mental health</span>
            </span>
          </div>
          <a
            href="tel:0800900900"
            className="flex items-center gap-2 rounded-full border border-line bg-white/2 px-3.5 py-1.5 text-xs text-fog transition-colors hover:text-mist"
          >
            <ShieldCheck size={14} className="text-mist" />
            Kríza? 0800 900 900
          </a>
        </nav>

        {/* ---- hero ---- */}
        <section className="mt-16 text-center sm:mt-20">
          <p className="hero-eyebrow mono-label caret-blink inline-flex items-center rounded-full border border-line bg-black px-3.5 py-1.5">
            beam — mental health · súkromne · po slovensky
          </p>
          <h1 className="mx-auto mt-6 max-w-[720px] text-[44px] font-semibold leading-[1.04] tracking-tight sm:text-6xl">
            {TITLE.split("").map((ch, i) => (
              <span key={i} className="hero-char inline-block">
                {ch === " " ? "\u00A0" : ch}
              </span>
            ))}
            <span className="hero-char shimmer-text">.</span>
          </h1>
          <p className="hero-sub mx-auto mt-5 max-w-[520px] text-[16px] leading-relaxed text-fog">
            Kľudný spoločník na rozhovor o duševnej pohode. Píšeš ako
            s kamarátom — odpovede sú podložené korpusom, citované, a kríza
            má vždy prednosť pred modelom.
          </p>
          <div className="mt-7 flex items-center justify-center gap-3">
            <a
              href="#zacat"
              className="inline-flex items-center gap-2 rounded-full bg-mist px-5 py-2.5 text-sm font-semibold text-ink transition-transform hover:scale-[1.03] active:scale-[0.98]"
            >
              Začať si písať
              <ArrowDown size={15} weight="bold" />
            </a>
            <a
              href="https://github.com/zyay/beam-mental-health"
              target="_blank"
              className="inline-flex items-center gap-2 rounded-full border border-line px-5 py-2.5 text-sm text-fog transition-colors hover:text-mist"
            >
              Zdrojový kód
            </a>
          </div>
        </section>

        {/* ---- bento ---- */}
        <section className="mt-14 grid grid-cols-1 gap-4 md:grid-cols-12">
          {/* A: chat demo */}
          <motion.div
            whileHover={{ y: -4 }}
            transition={{ type: "spring", stiffness: 300, damping: 24 }}
            className="bento-cell md:col-span-7 md:row-span-2"
          >
            <GlowCard className="flex h-full flex-col p-5">
              <CellLabel icon={<span className="text-mist">01</span>}>
                Ako vyzerá rozhovor
              </CellLabel>

              <div className="mt-4 flex flex-1 flex-col justify-end gap-3.5">
                <div className="flex justify-end">
                  <p className="max-w-[78%] rounded-2xl rounded-br-md bg-[rgba(167,139,250,0.14)] px-4 py-2.5 text-[14px] leading-relaxed">
                    Ahoj, mám dnes naraz veľa práce a trochu ma to tlačí.
                  </p>
                </div>
                <div className="flex justify-start">
                  <div className="max-w-[88%]">
                    <p className="text-[14px] leading-relaxed text-mist">
                      To je úplne pochopiteľné — keď sa toho nahrmie naraz,
                      je v poriadku spomaliť. Skús si vybrať jednu vec,
                      ktorá ťa tlačí najviac.
                    </p>
                    <span className="mt-2 inline-flex items-center gap-1.5 rounded-full border border-line bg-white/3 px-2.5 py-1 text-[11px] text-fog">
                      <Database size={11} className="text-mist" />
                      S1 · counsel-chat
                    </span>
                  </div>
                </div>
                <div className="flex items-center gap-3">
                  <span className="demo-orb block h-10 w-10 overflow-hidden rounded-full bg-white/5">
                    <Mascot size={40} animation="thinking" />
                  </span>
                  <span className="text-[13px] text-fog">Premýšľam…</span>
                </div>
              </div>
            </GlowCard>
          </motion.div>

          {/* B: auth */}
          <div className="bento-cell md:col-span-5 md:row-span-2" id="zacat">
            <GlowCard className="h-full p-6">
              <CellLabel icon={<User size={13} className="text-mist" />}>
                {mode === "login" ? "Vitaj späť" : "Nový účet"}
              </CellLabel>

              <div className="mt-4 grid grid-cols-2 gap-1 rounded-full border border-line bg-white/2 p-1">
                {(["login", "signup"] as Mode[]).map((m) => (
                  <button
                    key={m}
                    type="button"
                    onClick={() => {
                      setMode(m);
                      setError(null);
                    }}
                    className={`relative rounded-full py-2 text-sm font-medium transition-colors ${
                      mode === m ? "text-ink" : "text-fog hover:text-mist"
                    }`}
                  >
                    {mode === m && (
                      <motion.span
                        layoutId="auth-tab"
                        className="absolute inset-0 rounded-full"
                        style={{ background: "#ededed" }}
                        transition={{ type: "spring", stiffness: 400, damping: 32 }}
                      />
                    )}
                    <span className="relative z-10">
                      {m === "login" ? "Prihlásenie" : "Registrácia"}
                    </span>
                  </button>
                ))}
              </div>

              <form onSubmit={submit} className="mt-4 flex flex-col gap-3">
                {mode === "signup" && (
                  <label className="relative block">
                    <User
                      size={16}
                      className="pointer-events-none absolute left-4 top-1/2 z-10 -translate-y-1/2 text-fog"
                    />
                    <input
                      className="field pl-10"
                      placeholder="Ako sa voláš?"
                      value={name}
                      onChange={(e) => setName(e.target.value)}
                      maxLength={60}
                      autoComplete="name"
                    />
                  </label>
                )}
                <label className="relative block">
                  <EnvelopeSimple
                    size={16}
                    className="pointer-events-none absolute left-4 top-1/2 z-10 -translate-y-1/2 text-fog"
                  />
                  <input
                    className="field pl-10"
                    type="email"
                    required
                    placeholder="E-mail"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    autoComplete="email"
                  />
                </label>
                <label className="relative block">
                  <Lock
                    size={16}
                    className="pointer-events-none absolute left-4 top-1/2 z-10 -translate-y-1/2 text-fog"
                  />
                  <input
                    className="field pl-10"
                    type="password"
                    required
                    minLength={8}
                    placeholder={mode === "signup" ? "Heslo (aspoň 8 znakov)" : "Heslo"}
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    autoComplete={mode === "signup" ? "new-password" : "current-password"}
                  />
                </label>

                {error && (
                  <p className="rounded-xl border border-[rgba(255,110,130,0.3)] bg-[rgba(255,110,130,0.08)] px-4 py-2.5 text-sm text-[#ff9fb0]">
                    {error}
                  </p>
                )}

                <div ref={magnetRef} className="mt-1" onMouseMove={onMagnetMove} onMouseLeave={onMagnetLeave}>
                  <button ref={btnRef} className="btn-beam" disabled={busy}>
                    <PaperPlaneRight size={16} weight="bold" />
                    {busy ? "Chvíľu…" : mode === "login" ? "Prihlásiť sa" : "Vytvoriť účet"}
                  </button>
                </div>

                <p className="text-center text-[11px] leading-relaxed text-fog">
                  Beam nie je zdravotnícka pomôcka ani náhrada psychologickej pomoci.
                </p>
              </form>
            </GlowCard>
          </div>

          {/* C: crisis */}
          <motion.div
            whileHover={{ y: -4 }}
            transition={{ type: "spring", stiffness: 300, damping: 24 }}
            className="bento-cell md:col-span-4"
          >
            <GlowCard className="h-full p-5">
              <CellLabel icon={<span className="text-mist">03</span>}>
                Krízový protokol
              </CellLabel>
              <p className="mt-4 text-2xl font-semibold tracking-tight text-mist">0800 900 900</p>
              <p className="mt-1.5 text-[13px] leading-relaxed text-fog">
                Pri signáloch ohrozenia sa model nepýta — hneď príde pevná
                karta s linkami krízy. Nonstop, zdarma.
              </p>
            </GlowCard>
          </motion.div>

          {/* D: RAG */}
          <motion.div
            whileHover={{ y: -4 }}
            transition={{ type: "spring", stiffness: 300, damping: 24 }}
            className="bento-cell md:col-span-4"
          >
            <GlowCard className="h-full p-5">
              <CellLabel icon={<span className="text-mist">04</span>}>
                Podložené korpusom
              </CellLabel>
              <p className="mt-4 text-2xl font-semibold tracking-tight text-mist">
                1 270 chunkov
              </p>
              <p className="mt-1.5 text-[13px] leading-relaxed text-fog">
                counsel-chat · mental-health-chat · chatbot dataset · FAQ.
                Odpoveď cituje <span className="text-mist">[S1]</span>{" "}
                <span className="text-mist">[S2]</span> priamo v texte.
              </p>
            </GlowCard>
          </motion.div>

          {/* E: model */}
          <motion.div
            whileHover={{ y: -4 }}
            transition={{ type: "spring", stiffness: 300, damping: 24 }}
            className="bento-cell md:col-span-4"
          >
            <GlowCard className="h-full p-5">
              <CellLabel icon={<span className="text-mist">05</span>}>
                Mozog
              </CellLabel>
              <p className="mt-4 text-2xl font-semibold tracking-tight text-mist">
                DeepSeek-V4-Flash
              </p>
              <p className="mt-1.5 text-[13px] leading-relaxed text-fog">
                Hugging Face serverless proxy. Reálnych ~30–45 tok/s,
                reasoning effort nízky, latencia v sekundách.
              </p>
            </GlowCard>
          </motion.div>

          {/* F: privacy + native */}
          <motion.div
            whileHover={{ y: -4 }}
            transition={{ type: "spring", stiffness: 300, damping: 24 }}
            className="bento-cell md:col-span-12"
          >
            <GlowCard className="grid h-full grid-cols-1 divide-y divide-line sm:grid-cols-2 sm:divide-x sm:divide-y-0">
              <div className="p-5">
                <CellLabel icon={<span className="text-mist">06</span>}>
                  Súkromie
                </CellLabel>
                <p className="mt-3 text-[14px] leading-relaxed text-fog">
                  Token je výhradne na serveri. História chatu zostáva
                  v tvojom zariadení — server si ju dlhodobo neuchováva.
                </p>
              </div>
              <div className="p-5">
                <CellLabel icon={<span className="text-mist">07</span>}>
                  Web + natívny Android
                </CellLabel>
                <p className="mt-3 text-[14px] leading-relaxed text-fog">
                  Next.js na Verceli a plne natívna Kotlin + Compose appka
                  (APK v Releases) — rovnaký dizajn, rovnaký mozog.
                </p>
              </div>
            </GlowCard>
          </motion.div>
        </section>

        {/* ---- footer ---- */}
        <footer className="footer-row mt-12 flex flex-col items-center justify-between gap-3 border-t border-line pt-6 text-xs text-fog sm:flex-row">
          <span>V kríze vždy volaj 0800 900 900 · IPčko 0800 500 500 · 112</span>
          <span className="flex items-center gap-4">
            <Link href="/pravne/ochrana-sukromia" className="hover:text-mist">
              Ochrana súkromia
            </Link>
            <Link href="/pravne/vseobecne-podmienky" className="hover:text-mist">
              Podmienky
            </Link>
            <Link href="/pravne/zdravotny-disclaimer" className="hover:text-mist">
              Disclaimer
            </Link>
          </span>
        </footer>
      </div>
    </main>
  );
}

function CellLabel({ icon, children }: { icon: React.ReactNode; children: React.ReactNode }) {
  return (
    <p className="mono-label flex items-center gap-2.5">
      <span className="text-white/70">{icon}</span>
      {children}
    </p>
  );
}
