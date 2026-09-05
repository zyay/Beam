"use client";

import { Suspense, useRef, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
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
  Sparkle,
  LockKey,
} from "@phosphor-icons/react";
import BeamBackground from "@/components/effects/BeamBackground";
import GlowCard from "@/components/effects/GlowCard";
import AnimatedBeam from "@/components/effects/AnimatedBeam";
import BeamMark from "@/components/BeamMark";
import ThinkingOrb from "@/components/effects/ThinkingOrb";

type Mode = "login" | "signup";

const FEATURES = [
  {
    Icon: Sparkle,
    title: "Odpovede s citáciami",
    text: "RAG korpus z mental-health dát — každá rada má zdroj.",
  },
  {
    Icon: ShieldCheck,
    title: "Krízový protokol",
    text: "Pri ohrození nikdy nerieši model — hneď linka pomoci.",
  },
  {
    Icon: LockKey,
    title: "Tvoj token zostáva na serveri",
    text: "AI beží cez Vercel proxy — žiadne kľúče v zariadení.",
  },
];

export default function LoginPage() {
  return (
    <Suspense fallback={null}>
      <LoginInner />
    </Suspense>
  );
}

function LoginInner() {
  const router = useRouter();
  const next = useSearchParams().get("next") || "/onboarding";
  const [mode, setMode] = useState<Mode>("login");
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const root = useRef<HTMLDivElement>(null);
  const magnetRef = useRef<HTMLDivElement>(null);
  const btnRef = useRef<HTMLButtonElement>(null);

  // GSAP entrance timeline
  useGSAP(
    () => {
      const reduced = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
      if (reduced) return;
      const tl = gsap.timeline({ defaults: { ease: "power3.out" } });
      tl.from(".hero-eyebrow", { y: 18, opacity: 0, duration: 0.55 })
        .from(".hero-char", { y: 46, opacity: 0, stagger: 0.028, duration: 0.75 }, "-=0.25")
        .from(".hero-sub", { y: 16, opacity: 0, duration: 0.5 }, "-=0.45")
        .from(".hero-feature", { x: -26, opacity: 0, stagger: 0.1, duration: 0.55 }, "-=0.35")
        .from(".auth-card", { y: 44, opacity: 0, scale: 0.965, duration: 0.8 }, 0.35)
        .from(".auth-stagger", { y: 16, opacity: 0, stagger: 0.07, duration: 0.45 }, "-=0.45")
        .from(".orb-float", { scale: 0, opacity: 0, stagger: 0.12, duration: 0.6, ease: "back.out(2)" }, "-=0.6");

      // gentle idle float on the orbs
      gsap.to(".orb-float", {
        y: -12,
        duration: 2.4,
        ease: "sine.inOut",
        yoyo: true,
        repeat: -1,
        stagger: 0.35,
        delay: 1.6,
      });
    },
    { scope: root },
  );

  // magnetic submit button
  const onMagnetMove = (e: React.MouseEvent) => {
    if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;
    const wrap = magnetRef.current;
    const btn = btnRef.current;
    if (!wrap || !btn) return;
    const r = wrap.getBoundingClientRect();
    const relX = (e.clientX - r.left - r.width / 2) / r.width;
    const relY = (e.clientY - r.top - r.height / 2) / r.height;
    gsap.to(btn, { x: relX * 22, y: relY * 12, duration: 0.4, ease: "power3.out" });
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
    <main
      ref={root}
      className="relative min-h-dvh overflow-hidden"
    >
      <BeamBackground />
      <div className="grid-bg pointer-events-none absolute inset-0 -z-[5]" aria-hidden />

      <div className="relative mx-auto flex min-h-dvh w-full max-w-6xl flex-col items-center justify-center gap-12 px-6 py-14 lg:flex-row lg:gap-20">
        {/* ---- hero ---- */}
        <section className="relative max-w-[560px] flex-1">
          {/* floating orbs + animated beams (desktop) */}
          <div className="pointer-events-none absolute -right-10 -top-16 hidden lg:block">
            <div className="orb-float glass flex h-16 w-16 items-center justify-center rounded-2xl">
              <ThinkingOrb size={40} className="h-16 w-16" />
            </div>
          </div>
          <div className="pointer-events-none absolute -left-14 top-24 hidden w-40 lg:block">
            <AnimatedBeam width={160} height={56} curvature={0.5} delay={0.4} />
          </div>
          <div className="pointer-events-none absolute -right-6 bottom-2 hidden w-48 lg:block">
            <AnimatedBeam width={190} height={56} curvature={-0.5} reverse delay={1.1} />
          </div>

          <p className="hero-eyebrow mb-4 inline-flex items-center gap-2 rounded-full border border-line bg-white/3 px-3.5 py-1.5 text-xs text-fog">
            <Sparkle size={13} weight="fill" className="text-beam-violet" />
            mental health · súkromne · po slovensky
          </p>

          <h1 className="hero-title text-5xl font-semibold leading-[1.06] tracking-tight lg:text-6xl">
            {TITLE.split("").map((ch, i) => (
              <span key={i} className={`hero-char inline-block ${ch === " " ? "w-[0.28em]" : ""}`}>
                {ch === " " ? "\u00A0" : ch}
              </span>
            ))}
            <span className="hero-char shimmer-text">.</span>
          </h1>

          <p className="hero-sub mt-5 max-w-[440px] text-[17px] leading-relaxed text-fog">
            Beam je kľudný spoločník na rozhovor o duševnej pohode. Píšeš
            ako s kamarátom, odpovede sú podložené korpusom a kríza má
            vždy prednosť.
          </p>

          <ul className="mt-9 flex flex-col gap-4">
            {FEATURES.map(({ Icon, title, text }) => (
              <li key={title} className="hero-feature flex items-start gap-4">
                <span className="mt-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-xl border border-line bg-white/4">
                  <Icon size={17} className="text-beam-violet" weight="duotone" />
                </span>
                <span>
                  <span className="block text-[15px] font-medium text-mist">{title}</span>
                  <span className="block text-sm text-fog">{text}</span>
                </span>
              </li>
            ))}
          </ul>
        </section>

        {/* ---- auth card ---- */}
        <section className="w-full max-w-[420px] flex-1">
          <div className="mb-6 flex items-center justify-center gap-2 lg:justify-start">
            <BeamMark size={26} />
            <span className="text-lg font-semibold tracking-tight">
              Beam<span className="text-fog"> · mental health</span>
            </span>
          </div>

          <motion.div
            className="auth-card"
            whileHover={{ y: -3 }}
            transition={{ type: "spring", stiffness: 260, damping: 22 }}
          >
            <GlowCard className="p-6 sm:p-7">
              <div className="auth-stagger mb-6 grid grid-cols-2 gap-1 rounded-full border border-line bg-white/2 p-1">
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
                        style={{
                          background:
                            "linear-gradient(100deg, var(--color-beam-pink), var(--color-beam-violet) 55%, var(--color-beam-mint))",
                        }}
                        transition={{ type: "spring", stiffness: 400, damping: 32 }}
                      />
                    )}
                    <span className="relative z-10">
                      {m === "login" ? "Prihlásenie" : "Registrácia"}
                    </span>
                  </button>
                ))}
              </div>

              <form onSubmit={submit} className="flex flex-col gap-3">
                {mode === "signup" && (
                  <label className="auth-stagger relative block">
                    <User
                      size={17}
                      className="pointer-events-none absolute left-4 top-1/2 z-10 -translate-y-1/2 text-fog"
                    />
                    <input
                      className="field pl-11"
                      placeholder="Ako sa voláš?"
                      value={name}
                      onChange={(e) => setName(e.target.value)}
                      maxLength={60}
                      autoComplete="name"
                    />
                  </label>
                )}

                <label className="auth-stagger relative block">
                  <EnvelopeSimple
                    size={17}
                    className="pointer-events-none absolute left-4 top-1/2 z-10 -translate-y-1/2 text-fog"
                  />
                  <input
                    className="field pl-11"
                    type="email"
                    required
                    placeholder="E-mail"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    autoComplete="email"
                  />
                </label>

                <label className="auth-stagger relative block">
                  <Lock
                    size={17}
                    className="pointer-events-none absolute left-4 top-1/2 z-10 -translate-y-1/2 text-fog"
                  />
                  <input
                    className="field pl-11"
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
                  <p className="auth-stagger rounded-xl border border-[rgba(255,110,130,0.3)] bg-[rgba(255,110,130,0.08)] px-4 py-2.5 text-sm text-[#ff9fb0]">
                    {error}
                  </p>
                )}

                <div ref={magnetRef} className="auth-stagger mt-1" onMouseMove={onMagnetMove} onMouseLeave={onMagnetLeave}>
                  <button ref={btnRef} className="btn-beam" disabled={busy}>
                    <PaperPlaneRight size={17} weight="bold" />
                    {busy
                      ? "Chvíľu…"
                      : mode === "login"
                        ? "Prihlásiť sa"
                        : "Vytvoriť účet"}
                  </button>
                </div>
              </form>
            </GlowCard>
          </motion.div>

          <p className="mt-6 text-center text-xs leading-relaxed text-fog lg:text-left">
            Beam nie je zdravotnícka pomôcka ani náhrada psychologickej pomoci.{" "}
            V kríze volaj <span className="text-mist">0800 900 900</span> (nonstop).{" "}
            <Link href="/pravne/ochrana-sukromia" className="underline underline-offset-2 hover:text-mist">
              Ochrana súkromia
            </Link>
            {" · "}
            <Link href="/pravne/vseobecne-podmienky" className="underline underline-offset-2 hover:text-mist">
              Podmienky
            </Link>
          </p>
        </section>
      </div>
    </main>
  );
}
