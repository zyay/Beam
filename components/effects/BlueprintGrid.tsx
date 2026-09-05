"use client";

import { useEffect, useState } from "react";

/**
 * bklit.com-style blueprint canvas: hairline grid, dot intersections,
 * randomly pulsing cells, ruler ticks + hatched corners. Monochrome.
 */
export default function BlueprintGrid({ className = "" }: { className?: string }) {
  const [lit, setLit] = useState<Set<number>>(new Set());

  // responsive column count
  const [cols, setCols] = useState(6);
  useEffect(() => {
    const compute = () =>
      setCols(window.innerWidth < 640 ? 3 : window.innerWidth < 1024 ? 4 : 6);
    compute();
    window.addEventListener("resize", compute);
    return () => window.removeEventListener("resize", compute);
  }, []);
  const rows = 3;

  // living canvas: randomly light up a few cells
  useEffect(() => {
    const total = cols * rows;
    const tick = () => {
      const n = 3 + Math.floor(Math.random() * 3);
      const next = new Set<number>();
      while (next.size < n) next.add(Math.floor(Math.random() * total));
      setLit(next);
    };
    tick();
    const id = setInterval(tick, 1600);
    return () => clearInterval(id);
  }, [cols]);

  return (
    <div aria-hidden className={`pointer-events-none absolute inset-0 -z-10 ${className}`}>
      {/* pure black base */}
      <div className="absolute inset-0 bg-black" />

      {/* blueprint sheet, top-anchored */}
      <div
        className="absolute inset-x-0 top-0 mx-auto max-w-6xl"
        style={{
          WebkitMaskImage:
            "linear-gradient(to bottom, #000 55%, transparent 96%)",
          maskImage: "linear-gradient(to bottom, #000 55%, transparent 96%)",
        }}
      >
        <div className="relative m-5 border-t border-l border-white/12">
          {/* ruler ticks */}
          <span className="absolute -top-4 left-0 h-4 w-px bg-white/25" />
          <span className="absolute -top-8 left-0 h-8 w-px bg-white/15" />
          <span className="absolute -top-4 right-0 h-4 w-px bg-white/25" />
          <span className="absolute -top-8 right-0 h-8 w-px bg-white/15" />
          <span className="absolute -left-4 top-0 h-px w-4 bg-white/25" />
          <span className="absolute -left-8 top-0 h-px w-8 bg-white/15" />
          <span className="absolute -right-4 top-0 h-px w-4 bg-white/25" />
          <span className="absolute -right-8 top-0 h-px w-8 bg-white/15" />
          {/* hatched corner squares */}
          <span
            className="absolute -left-8 -top-8 h-8 w-8"
            style={{
              background:
                "repeating-linear-gradient(45deg, rgba(255,255,255,0.22) 0 1px, transparent 1px 5px)",
            }}
          />
          <span
            className="absolute -right-8 -top-8 h-8 w-8"
            style={{
              background:
                "repeating-linear-gradient(-45deg, rgba(255,255,255,0.22) 0 1px, transparent 1px 5px)",
            }}
          />

          <div
            className="grid"
            style={{ gridTemplateColumns: `repeat(${cols}, minmax(0, 1fr))` }}
          >
            {Array.from({ length: cols * rows }).map((_, i) => (
              <div
                key={i}
                className="relative h-[104px] border-b border-r border-white/10 transition-colors duration-700 sm:h-[120px]"
                style={
                  lit.has(i)
                    ? { background: "rgba(255,255,255,0.055)" }
                    : undefined
                }
              >
                {/* dot at the cell's top-left intersection */}
                <span
                  className="absolute -left-[3px] -top-[3px] h-[5px] w-[5px] rounded-full border border-white/30 bg-black"
                />
              </div>
            ))}
          </div>

          {/* sheet labels, mono */}
          <span className="absolute -top-3 left-6 bg-black px-1.5 font-mono text-[10px] uppercase tracking-[0.18em] text-white/40">
            sheet 01 — beam
          </span>
          <span className="absolute -bottom-3 right-6 bg-black px-1.5 font-mono text-[10px] uppercase tracking-[0.18em] text-white/40">
            mental health · 1:1
          </span>
        </div>
      </div>

      {/* film grain */}
      <div className="noise-overlay absolute inset-0" />
    </div>
  );
}
