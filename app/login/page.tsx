"use client";

import { Suspense, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import Link from "next/link";
import { motion, AnimatePresence } from "framer-motion";
import { EnvelopeSimple, Lock, User, PaperPlaneRight } from "@phosphor-icons/react";
import BeamBackground from "@/components/effects/BeamBackground";
import BorderBeam from "@/components/effects/BorderBeam";
import BeamMark from "@/components/BeamMark";

type Mode = "login" | "signup";

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

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      const res = await fetch(`/api/auth/${mode}`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(mode === "signup" ? { email, password, name } : { email, password }),
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

  return (
    <main className="relative flex min-h-dvh flex-col items-center justify-center px-5">
      <BeamBackground />

      <div className="w-full max-w-[400px]">
        <div className="mb-8 flex flex-col items-center gap-2 text-center">
          <div className="flex items-center gap-2">
            <BeamMark />
            <span className="text-2xl font-semibold tracking-tight">
              Beam<span className="text-fog"> · mental health</span>
            </span>
          </div>
          <p className="text-sm text-fog">
            Kľudné miesto na rozhovor, keď ti nie je ľahko.
          </p>
        </div>

        <BorderBeam className="p-6 sm:p-7">
          <div className="mb-6 grid grid-cols-2 gap-1 rounded-full border border-line bg-white/2 p-1">
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
            <AnimatePresence initial={false}>
              {mode === "signup" && (
                <motion.div
                  initial={{ height: 0, opacity: 0 }}
                  animate={{ height: "auto", opacity: 1 }}
                  exit={{ height: 0, opacity: 0 }}
                  className="overflow-hidden"
                >
                  <label className="relative block">
                    <User
                      size={17}
                      className="pointer-events-none absolute left-4 top-1/2 -translate-y-1/2 text-fog"
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
                </motion.div>
              )}
            </AnimatePresence>

            <label className="relative block">
              <EnvelopeSimple
                size={17}
                className="pointer-events-none absolute left-4 top-1/2 -translate-y-1/2 text-fog"
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

            <label className="relative block">
              <Lock
                size={17}
                className="pointer-events-none absolute left-4 top-1/2 -translate-y-1/2 text-fog"
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
              <p className="rounded-xl border border-[rgba(255,110,130,0.3)] bg-[rgba(255,110,130,0.08)] px-4 py-2.5 text-sm text-[#ff9fb0]">
                {error}
              </p>
            )}

            <button className="btn-beam mt-1" disabled={busy}>
              <PaperPlaneRight size={17} weight="bold" />
              {busy
                ? "Chvíľu…"
                : mode === "login"
                  ? "Prihlásiť sa"
                  : "Vytvoriť účet"}
            </button>
          </form>
        </BorderBeam>

        <p className="mt-6 text-center text-xs leading-relaxed text-fog">
          Beam nie je zdravotnícka pomôcka ani náhrada psychologickej pomoci.{" "}
          <br className="hidden sm:block" />
          V kríze volaj <span className="text-mist">0800 900 900</span> (nonstop).{" "}
          <Link href="/pravne/ochrana-sukromia" className="underline underline-offset-2 hover:text-mist">
            Ochrana súkromia
          </Link>
          {" · "}
          <Link href="/pravne/vseobecne-podmienky" className="underline underline-offset-2 hover:text-mist">
            Podmienky
          </Link>
        </p>
      </div>
    </main>
  );
}
