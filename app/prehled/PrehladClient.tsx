"use client";

import { useCallback, useEffect, useState } from "react";

type Checkin = { day: string; mood: number; note: string | null };

const MOODS = [
  { value: 1, label: "Hrozné" },
  { value: 2, label: "Slabé" },
  { value: 3, label: "OK" },
  { value: 4, label: "Dobré" },
  { value: 5, label: "Skvelé" },
];

function todayKey(): string {
  const d = new Date();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${d.getFullYear()}-${m}-${day}`;
}

function last30Days(): string[] {
  const out: string[] = [];
  const d = new Date();
  for (let i = 29; i >= 0; i--) {
    const c = new Date(d.getTime() - i * 86400000);
    const m = String(c.getMonth() + 1).padStart(2, "0");
    const day = String(c.getDate()).padStart(2, "0");
    out.push(`${c.getFullYear()}-${m}-${day}`);
  }
  return out;
}

export default function PrehladClient({ name }: { name: string }) {
  const [checkins, setCheckins] = useState<Checkin[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedMood, setSelectedMood] = useState<number | null>(null);
  const [note, setNote] = useState("");
  const [saving, setSaving] = useState(false);
  const [savedMsg, setSavedMsg] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const today = todayKey();

  const load = useCallback(async () => {
    try {
      const res = await fetch("/api/checkins?days=90", { cache: "no-store" });
      if (res.ok) {
        const body = (await res.json()) as { checkins: Checkin[] };
        setCheckins(body.checkins);
        const t = body.checkins.find((c) => c.day === todayKey());
        setSelectedMood(t?.mood ?? null);
        setNote(t?.note ?? "");
      }
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  async function save() {
    if (selectedMood == null) return;
    setSaving(true);
    setSavedMsg(false);
    setError(null);
    try {
      const res = await fetch("/api/checkins", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ mood: selectedMood, note }),
      });
      if (res.ok) {
        await load();
        setSavedMsg(true);
        setTimeout(() => setSavedMsg(false), 2200);
      } else {
        const body = (await res.json().catch(() => ({}))) as { error?: string };
        setError(body.error ?? "Uloženie sa nepodarilo.");
      }
    } finally {
      setSaving(false);
    }
  }

  // streak: consecutive days ending today (or yesterday if today missing)
  const daySet = new Set(checkins.map((c) => c.day));
  let streak = 0;
  {
    const d = new Date();
    if (!daySet.has(todayKey())) d.setDate(d.getDate() - 1);
    for (;;) {
      const m = String(d.getMonth() + 1).padStart(2, "0");
      const day = String(d.getDate()).padStart(2, "0");
      const key = `${d.getFullYear()}-${m}-${day}`;
      if (!daySet.has(key)) break;
      streak++;
      d.setDate(d.getDate() - 1);
    }
  }
  const recent = checkins.slice(-30);
  const avg30 =
    recent.length > 0
      ? (recent.reduce((s, c) => s + c.mood, 0) / recent.length).toFixed(1)
      : null;

  const days = last30Days();
  const byDay = new Map(checkins.map((c) => [c.day, c]));
  const moodLabel = selectedMood != null ? MOODS[selectedMood - 1].label : null;

  return (
    <main className="relative min-h-dvh bg-ink text-mist">
      <div
        className="pointer-events-none absolute inset-0"
        style={{
          background:
            "radial-gradient(ellipse at 20% 0%, rgba(143,191,154,0.10), transparent 55%), radial-gradient(ellipse at 85% 90%, rgba(217,196,142,0.07), transparent 55%)",
        }}
      />
      <div className="relative z-10 mx-auto w-full max-w-[620px] px-5 py-8">
        <div className="flex items-center justify-between">
          <a href="/chat" className="text-sm text-fog transition-colors hover:text-mist">
            ← Chat
          </a>
          <span className="text-sm text-fog">{name}</span>
        </div>

        <h1 className="mt-8 text-3xl font-semibold tracking-tight">Prehľad</h1>
        <p className="mt-2 text-sm text-fog">
          Ako sa máš deň čo deň. Vidíš to len ty — údaje sú tvoje.
        </p>

        {/* today check-in */}
        <section className="mt-7 rounded-3xl border border-line/60 bg-card/60 p-6">
          <h2 className="text-[15px] font-medium text-mist">Ako sa dnes máš?</h2>
          <div className="mt-4 grid grid-cols-5 gap-2">
            {MOODS.map((m) => {
              const on = selectedMood === m.value;
              return (
                <button
                  key={m.value}
                  type="button"
                  onClick={() => {
                    setSelectedMood(m.value);
                    setSavedMsg(false);
                  }}
                  className={
                    "rounded-xl border px-2 py-2.5 text-[11px] font-medium transition-colors " +
                    (on
                      ? "border-[#8FBF9A] bg-[#8FBF9A] text-[#0D160F]"
                      : "border-line/70 bg-[#8FBF9A]/10 text-fog hover:bg-[#8FBF9A]/20")
                  }
                >
                  {m.label}
                </button>
              );
            })}
          </div>
          {moodLabel && (
            <p className="mt-3 text-xs text-fog">
              Dnes: {moodLabel}
            </p>
          )}
          <input
            className="field mt-4"
            placeholder="Krátka poznámka (nepovinné)"
            value={note}
            maxLength={500}
            onChange={(e) => setNote(e.target.value)}
          />
          <div className="mt-4 flex items-center gap-3">
            <button
              type="button"
              onClick={save}
              disabled={selectedMood == null || saving}
              className="rounded-full bg-[#8FBF9A] px-6 py-2.5 text-sm font-medium text-[#0D160F] transition-transform hover:scale-[1.02] active:scale-[0.98] disabled:opacity-40"
            >
              {saving ? "Ukladám…" : "Uložiť dnes"}
            </button>
            {savedMsg && <span className="text-sm text-[#8FBF9A]">Uložené.</span>}
            {error && <span className="text-sm text-rose-300">{error}</span>}
          </div>
        </section>

        {/* stats */}
        <section className="mt-4 grid grid-cols-3 gap-3">
          <div className="rounded-2xl border border-line/60 bg-card/60 p-4">
            <div className="text-xl font-semibold text-[#8FBF9A]">{streak}</div>
            <div className="mt-0.5 text-[11px] leading-4 text-fog">
              {streak === 1 ? "deň v rade" : streak >= 2 && streak <= 4 ? "dni v rade" : "dní v rade"}
            </div>
          </div>
          <div className="rounded-2xl border border-line/60 bg-card/60 p-4">
            <div className="text-xl font-semibold text-[#8FBF9A]">{avg30 ?? "—"}</div>
            <div className="mt-0.5 text-[11px] leading-4 text-fog">priemer 30 dní</div>
          </div>
          <div className="rounded-2xl border border-line/60 bg-card/60 p-4">
            <div className="text-xl font-semibold text-[#8FBF9A]">{checkins.length}</div>
            <div className="mt-0.5 text-[11px] leading-4 text-fog">záznamov</div>
          </div>
        </section>

        {/* 30-day chart */}
        <section className="mt-4 rounded-3xl border border-line/60 bg-card/60 p-6">
          <div className="flex items-baseline justify-between">
            <h2 className="text-[15px] font-medium text-mist">Posledných 30 dní</h2>
            <span className="text-[11px] text-fog">1–5</span>
          </div>
          <div className="mt-5 flex h-24 items-end gap-[3px]">
            {loading
              ? null
              : days.map((day) => {
                  const c = byDay.get(day);
                  return c ? (
                    <div
                      key={day}
                      title={`${day}: ${MOODS[c.mood - 1].label}${c.note ? ` — ${c.note}` : ""}`}
                      className="flex-1 rounded-t-[4px] bg-[#8FBF9A]/55"
                      style={{
                        height: `${(c.mood / 5) * 100}%`,
                        ...(day === today ? { backgroundColor: "#8FBF9A" } : {}),
                      }}
                    />
                  ) : (
                    <div key={day} className="flex-1" title={day}>
                      <div className="mx-auto h-1 w-1 rounded-full bg-line" />
                    </div>
                  );
                })}
          </div>
          <p className="mt-4 text-[11px] leading-4 text-fog">
            {checkins.length === 0
              ? "Zatiaľ tu nič nie je. Prvý záznam pridá klik na náladu hore."
              : "Vyšší stĺpec = lepší deň. Dni bez záznamu sú malé bodky."}
          </p>
        </section>

        <p className="mt-5 flex items-center justify-center gap-1.5 text-[11px] text-fog">
          <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" aria-hidden>
            <rect x="4" y="10" width="16" height="10" rx="2" />
            <path d="M8 10V7a4 4 0 0 1 8 0v3" />
          </svg>
          Záznamy sú viazané na tvoj účet a nikam ich neposielame.
        </p>
      </div>
    </main>
  );
}
