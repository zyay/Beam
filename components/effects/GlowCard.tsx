"use client";

import { useCallback, useRef, type ReactNode } from "react";

/**
 * 21st.dev-style glass card: the border lights up where the cursor is
 * (radial gradient masked to the border) + a soft inner glow.
 */
export default function GlowCard({
  children,
  className = "",
}: {
  children: ReactNode;
  className?: string;
}) {
  const ref = useRef<HTMLDivElement>(null);

  const onMove = useCallback((e: React.MouseEvent) => {
    const el = ref.current;
    if (!el) return;
    const r = el.getBoundingClientRect();
    el.style.setProperty("--mx", `${e.clientX - r.left}px`);
    el.style.setProperty("--my", `${e.clientY - r.top}px`);
  }, []);

  return (
    <div ref={ref} onMouseMove={onMove} className={`glow-card ${className}`}>
      <div className="glow-inner" aria-hidden />
      {children}
    </div>
  );
}
