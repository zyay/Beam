"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import {
  Check,
  IdentificationCard,
  Microphone,
  PhoneCall,
  SignOut,
  ShieldCheck,
  FileText,
  HeartStraight,
} from "@phosphor-icons/react";
import BeamMark from "@/components/BeamMark";
import BorderBeam from "@/components/effects/BorderBeam";

type Profile = {
  mood?: string;
  stressors?: string[];
  goals?: string[];
  cadence?: string;
  time?: string;
};

export default function SettingsClient({
  email,
  name,
  profile,
}: {
  email: string;
  name: string;
  profile: Profile | null;
}) {
  const router = useRouter();
  const [newName, setNewName] = useState(name);
  const [saved, setSaved] = useState(false);
  const [busy, setBusy] = useState(false);

  async function save() {
    setBusy(true);
    try {
      await fetch("/api/profile", {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ profile: profile ?? {}, name: newName }),
      });
      setSaved(true);
      setTimeout(() => setSaved(false), 2000);
    } finally {
      setBusy(false);
    }
  }

  async function logout() {
    await fetch("/api/auth/logout", { method: "POST" });
    localStorage.removeItem("beam.chat.v1");
    router.push("/login");
    router.refresh();
  }

  return (
    <main className="mx-auto min-h-dvh w-full max-w-[560px] px-5 py-8">
      <div className="mb-8 flex items-center justify-end">
        <BeamMark size={22} />
      </div>

      <h1 className="text-2xl font-semibold tracking-tight">Nastavenia</h1>

      <section className="mt-7">
        <p className="mb-3 flex items-center gap-2 text-sm font-medium text-fog">
          <IdentificationCard size={16} /> Účet
        </p>
        <BorderBeam className="p-5">
          <div className="flex flex-col gap-3">
            <p className="text-sm text-fog">{email}</p>
            <label className="block">
              <span className="mb-1.5 block text-xs text-fog">Meno</span>
              <input
                className="field"
                value={newName}
                maxLength={60}
                onChange={(e) => setNewName(e.target.value)}
              />
            </label>
            <button className="btn-beam" onClick={save} disabled={busy}>
              {saved ? <Check size={16} weight="bold" /> : null}
              {saved ? "Uložené" : "Uložiť"}
            </button>
          </div>
        </BorderBeam>
      </section>

      <section className="mt-7">
        <p className="mb-3 flex items-center gap-2 text-sm font-medium text-fog">
          <Microphone size={16} /> Hlas
        </p>
        <BorderBeam className="p-2">
          <Link
            href="/hlas"
            className="flex items-center gap-3 rounded-xl px-3.5 py-3.5 text-[15px] text-mist transition-colors hover:bg-white/4"
          >
            <Microphone size={17} className="text-beam-violet" weight="bold" />
            <span className="flex flex-col">
              <span>Hlasový hovor</span>
              <span className="text-xs text-fog">
                Hovorte s Beamom v reálnom čase
              </span>
            </span>
          </Link>
        </BorderBeam>
      </section>

      <section className="mt-7">
        <p className="mb-3 flex items-center gap-2 text-sm font-medium text-fog">
          <HeartStraight size={16} /> Krízové linky
        </p>
        <BorderBeam className="divide-y divide-line p-2">
          <a href="tel:0800900900" className="flex items-center gap-3 rounded-xl px-3.5 py-3.5 text-[15px] text-mist transition-colors hover:bg-white/4">
            <PhoneCall size={17} className="text-beam-pink" weight="bold" />
            Linka krízy · 0800 900 900 · nonstop
          </a>
          <a href="tel:0800500500" className="flex items-center gap-3 rounded-xl px-3.5 py-3.5 text-[15px] text-mist transition-colors hover:bg-white/4">
            <PhoneCall size={17} className="text-beam-violet" weight="bold" />
            IPčko · 0800 500 500 · nonstop
          </a>
          <a href="tel:112" className="flex items-center gap-3 rounded-xl px-3.5 py-3.5 text-[15px] text-mist transition-colors hover:bg-white/4">
            <PhoneCall size={17} className="text-beam-mint" weight="bold" />
            Tiesňové volanie · 112
          </a>
        </BorderBeam>
      </section>

      <section className="mt-7">
        <p className="mb-3 flex items-center gap-2 text-sm font-medium text-fog">
          <ShieldCheck size={16} /> Právne
        </p>
        <BorderBeam className="divide-y divide-line p-2">
          {[
            ["/pravne/ochrana-sukromia", "Ochrana súkromia"],
            ["/pravne/vseobecne-podmienky", "Všeobecné podmienky"],
            ["/pravne/zdravotny-disclaimer", "Zdravotný disclaimer"],
          ].map(([href, label]) => (
            <Link
              key={href}
              href={href}
              className="flex items-center gap-3 rounded-xl px-3.5 py-3.5 text-[15px] text-mist transition-colors hover:bg-white/4"
            >
              <FileText size={17} className="text-fog" />
              {label}
            </Link>
          ))}
        </BorderBeam>
      </section>

      <section className="mt-7 mb-4">
        <button className="btn-ghost" onClick={logout}>
          <SignOut size={17} />
          Odhlásiť sa
        </button>
      </section>
    </main>
  );
}
