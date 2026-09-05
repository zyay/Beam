"use client";

import { useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { motion, AnimatePresence } from "framer-motion";
import {
  ArrowLeft,
  ArrowRight,
  Check,
  Clock,
  Smiley,
  SmileyWink,
  SmileyMeh,
  CloudRain,
  BatteryLow,
  Sparkle,
} from "@phosphor-icons/react";
import BorderBeam from "@/components/effects/BorderBeam";
import ThinkingOrb from "@/components/effects/ThinkingOrb";

const MOODS = [
  { id: "dobre", label: "Dobre", Icon: Smiley },
  { id: "ok", label: "V pohode", Icon: SmileyWink },
  { id: "priemerne", label: "Priemerne", Icon: SmileyMeh },
  { id: "tazko", label: "Ťažko", Icon: CloudRain },
  { id: "prazdno", label: "Vyčerpane", Icon: BatteryLow },
];

const STRESSORS = ["Práca", "Škola", "Spánok", "Vzťahy", "Zdravie", "Peniaze", "Osamelosť", "Iné"];
const GOALS = [
  "Menej stresu",
  "Lepší spánok",
  "Hovoriť o pocitoch",
  "Zostať pokojný/á",
  "Viac energie",
  "Rozumieť sám/samej sebe",
];
const CADENCES = ["Denne", "Každý druhý deň", "Iba keď potrebujem"];

export default function OnboardingPage() {
  const router = useRouter();
  const [step, setStep] = useState(0);
  const [saving, setSaving] = useState(false);
  const [data, setData] = useState({
    name: "",
    mood: "",
    stressors: [] as string[],
    goals: [] as string[],
    cadence: "",
    time: "",
  });

  const steps = useMemo(() => 7, []);
  const progress = ((step + 1) / steps) * 100;

  const canNext = [
    data.name.trim().length >= 2,
    Boolean(data.mood),
    data.stressors.length > 0,
    data.goals.length > 0,
    Boolean(data.cadence),
    true, // time is skippable
    true, // summary
  ][step];

  function toggle(list: string[], value: string) {
    return list.includes(value) ? list.filter((v) => v !== value) : [...list, value];
  }

  async function finish() {
    setSaving(true);
    try {
      await fetch("/api/profile", {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ profile: data }),
      });
      router.push("/chat");
      router.refresh();
    } finally {
      setSaving(false);
    }
  }

  return (
    <main className="relative flex min-h-dvh flex-col px-5 pb-8 pt-6">
      {/* progress beam */}
      <div className="mx-auto w-full max-w-[520px]">
        <div className="h-1 overflow-hidden rounded-full bg-white/6">
          <motion.div
            className="h-full rounded-full"
            style={{
              background:
                "linear-gradient(90deg, var(--color-beam-pink), var(--color-beam-violet), var(--color-beam-mint))",
            }}
            animate={{ width: `${progress}%` }}
            transition={{ type: "spring", stiffness: 120, damping: 22 }}
          />
        </div>
      </div>

      <div className="mx-auto flex w-full max-w-[520px] flex-1 flex-col items-center justify-center py-10">
        <AnimatePresence mode="wait">
          <motion.div
            key={step}
            initial={{ opacity: 0, y: 22, filter: "blur(6px)" }}
            animate={{ opacity: 1, y: 0, filter: "blur(0px)" }}
            exit={{ opacity: 0, y: -22, filter: "blur(6px)" }}
            transition={{ duration: 0.35, ease: [0.22, 1, 0.36, 1] }}
            className="w-full"
          >
            {step === 0 && (
              <StepShell
                title="Ahoj. Ako sa voláš?"
                sub="Aby sme si nemuseli tykať medzi anonymmi."
              >
                <input
                  autoFocus
                  className="field text-center text-lg"
                  placeholder="Tvoje meno"
                  value={data.name}
                  maxLength={60}
                  onChange={(e) => setData({ ...data, name: e.target.value })}
                  onKeyDown={(e) => e.key === "Enter" && canNext && setStep(1)}
                />
              </StepShell>
            )}

            {step === 1 && (
              <StepShell
                title={`${data.name || "Hej"}, ako sa dnes cítiš?`}
                sub="Bez hodnotenia — len tak, ako to reálne je."
              >
                <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
                  {MOODS.map(({ id, label, Icon }) => (
                    <button
                      key={id}
                      data-active={data.mood === id}
                      onClick={() => setData({ ...data, mood: id })}
                      className="glass flex flex-col items-center gap-2 py-5 text-sm text-fog transition-all data-[active=true]:border-[rgba(167,139,250,0.6)] data-[active=true]:text-mist"
                      style={{ borderRadius: 16 }}
                    >
                      <Icon size={26} weight={data.mood === id ? "fill" : "regular"} />
                      {label}
                    </button>
                  ))}
                </div>
              </StepShell>
            )}

            {step === 2 && (
              <StepShell title="Čo ťa teraz najviac zaťažuje?" sub="Vyber jednu alebo viac vecí.">
                <div className="flex flex-wrap gap-2.5">
                  {STRESSORS.map((s) => (
                    <button
                      key={s}
                      className="chip"
                      data-active={data.stressors.includes(s)}
                      onClick={() => setData({ ...data, stressors: toggle(data.stressors, s) })}
                    >
                      {s}
                    </button>
                  ))}
                </div>
              </StepShell>
            )}

            {step === 3 && (
              <StepShell title="S čím ti to chceme skúsiť uľahčiť?" sub="Čo by ti tu najviac pomohlo?">
                <div className="flex flex-wrap gap-2.5">
                  {GOALS.map((g) => (
                    <button
                      key={g}
                      className="chip"
                      data-active={data.goals.includes(g)}
                      onClick={() => setData({ ...data, goals: toggle(data.goals, g) })}
                    >
                      {data.goals.includes(g) && <Check size={14} weight="bold" />}
                      {g}
                    </button>
                  ))}
                </div>
              </StepShell>
            )}

            {step === 4 && (
              <StepShell title="Ako často si chceš písať?" sub="Len odhad — kedykoľvek to môžeš zmeniť.">
                <div className="flex flex-col gap-2.5">
                  {CADENCES.map((c) => (
                    <button
                      key={c}
                      className="chip w-full justify-center py-3.5"
                      data-active={data.cadence === c}
                      onClick={() => setData({ ...data, cadence: c })}
                    >
                      {c}
                    </button>
                  ))}
                </div>
              </StepShell>
            )}

            {step === 5 && (
              <StepShell title="Kedy ti to najviac sedí?" sub="Môžeme ti ráno alebo večer pripomenúť, že tu sme.">
                <label className="relative mx-auto block w-fit">
                  <Clock size={18} className="pointer-events-none absolute left-4 top-1/2 -translate-y-1/2 text-fog" />
                  <input
                    type="time"
                    className="field w-44 pl-11"
                    value={data.time}
                    onChange={(e) => setData({ ...data, time: e.target.value })}
                  />
                </label>
                <button
                  className="mx-auto mt-3 block text-sm text-fog underline underline-offset-4 hover:text-mist"
                  onClick={() => setData({ ...data, time: "" })}
                >
                  Radšej bez pripomienok
                </button>
              </StepShell>
            )}

            {step === 6 && (
              <StepShell title={`Hotovo, ${data.name}.`} sub="Takto vyzerá tvoja výbava na štart.">
                <BorderBeam className="p-5">
                  <div className="flex items-center gap-4">
                    <ThinkingOrb size={56} />
                    <div className="flex flex-col gap-1 text-sm">
                      <span className="text-mist">
                        {data.name} · {MOODS.find((m) => m.id === data.mood)?.label}
                      </span>
                      <span className="text-fog">
                        {data.goals.slice(0, 3).join(" · ")}
                      </span>
                      <span className="text-fog">
                        {data.cadence}
                        {data.time ? ` · ${data.time}` : " · bez pripomienok"}
                      </span>
                    </div>
                  </div>
                </BorderBeam>
                <p className="mt-4 flex items-start gap-2 text-xs leading-relaxed text-fog">
                  <Sparkle size={14} className="mt-0.5 shrink-0" />
                  Beam nie je zdravotnícka pomôcka ani náhrada psychológa. V kríze vždy volaj 0800 900 900.
                </p>
              </StepShell>
            )}
          </motion.div>
        </AnimatePresence>
      </div>

      <div className="mx-auto flex w-full max-w-[520px] gap-3">
        {step > 0 && (
          <button className="btn-ghost w-auto px-5" onClick={() => setStep(step - 1)}>
            <ArrowLeft size={17} />
          </button>
        )}
        {step < steps - 1 ? (
          <button className="btn-beam" disabled={!canNext} onClick={() => setStep(step + 1)}>
            Ďalej
            <ArrowRight size={17} weight="bold" />
          </button>
        ) : (
          <button className="btn-beam" disabled={saving} onClick={finish}>
            {saving ? "Ukladám…" : "Začať si písať"}
            <ArrowRight size={17} weight="bold" />
          </button>
        )}
      </div>
    </main>
  );
}

function StepShell({
  title,
  sub,
  children,
}: {
  title: string;
  sub: string;
  children: React.ReactNode;
}) {
  return (
    <div className="flex w-full flex-col items-center gap-7 text-center">
      <div className="max-w-[420px]">
        <h1 className="text-2xl font-semibold tracking-tight text-mist">{title}</h1>
        <p className="mt-2 text-sm text-fog">{sub}</p>
      </div>
      <div className="w-full">{children}</div>
    </div>
  );
}
