"use client";

import { useEffect, useRef } from "react";

/**
 * Particle sphere on a 2D canvas — the "thinking" indicator.
 * Points are projected from a rotating 3D sphere; the far hemisphere dims.
 */
export default function ThinkingOrb({
  size = 44,
  className = "",
}: {
  size?: number;
  className?: string;
}) {
  const ref = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    const canvas = ref.current;
    if (!canvas) return;
    const ctx = canvas.getContext("2d");
    if (!ctx) return;

    const dpr = Math.min(window.devicePixelRatio || 1, 2);
    canvas.width = size * dpr;
    canvas.height = size * dpr;
    ctx.scale(dpr, dpr);

    const N = 140;
    const pts: { x: number; y: number; z: number }[] = [];
    // fibonacci sphere
    const golden = Math.PI * (3 - Math.sqrt(5));
    for (let i = 0; i < N; i++) {
      const y = 1 - (i / (N - 1)) * 2;
      const r = Math.sqrt(1 - y * y);
      const a = golden * i;
      pts.push({ x: Math.cos(a) * r, y, z: Math.sin(a) * r });
    }

    const reduced = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    let raf = 0;
    let t = 0;
    const R = size * 0.36;

    const draw = () => {
      t += 0.014;
      ctx.clearRect(0, 0, size, size);
      const cx = size / 2;
      const cy = size / 2;
      const cosA = Math.cos(t), sinA = Math.sin(t);
      const cosB = Math.cos(t * 0.7), sinB = Math.sin(t * 0.7);

      for (const p of pts) {
        // rotate Y then X
        const x1 = p.x * cosA + p.z * sinA;
        const z1 = -p.x * sinA + p.z * cosA;
        const y2 = p.y * cosB - z1 * sinB;
        const z2 = p.y * sinB + z1 * cosB;

        const persp = 1 / (1 + z2 * 0.35);
        const sx = cx + x1 * R * persp;
        const sy = cy + y2 * R * persp;

        const depth = (z2 + 1) / 2; // 0 back, 1 front
        const alpha = 0.18 + depth * 0.72;
        const rad = 0.6 + depth * 1.15;

        ctx.beginPath();
        ctx.fillStyle = `rgba(196, 181, 253, ${alpha})`;
        ctx.arc(sx, sy, rad, 0, Math.PI * 2);
        ctx.fill();
      }
      if (!reduced) raf = requestAnimationFrame(draw);
    };
    draw();

    return () => cancelAnimationFrame(raf);
  }, [size]);

  return (
    <canvas
      ref={ref}
      style={{ width: size, height: size }}
      aria-hidden
      className={className}
    />
  );
}
