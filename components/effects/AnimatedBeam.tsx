"use client";

/**
 * 21st.dev "Animated Beam" — a glowing line that travels along a path
 * between two nodes. Pure SVG + CSS dash animation, zero JS per frame.
 */
export default function AnimatedBeam({
  width,
  height,
  curvature = 0.4,
  reverse = false,
  className = "",
  delay = 0,
}: {
  width: number;
  height: number;
  curvature?: number;
  reverse?: boolean;
  className?: string;
  delay?: number;
}) {
  const x1 = 0;
  const y1 = height / 2;
  const x2 = width;
  const y2 = height / 2;
  const cx = (x1 + x2) / 2;
  const cy = (y1 + y2) / 2 + (height / 2) * curvature;
  const d = `M ${x1},${y1} Q ${cx},${cy} ${x2},${y2}`;

  return (
    <svg
      width={width}
      height={height}
      viewBox={`0 0 ${width} ${height}`}
      fill="none"
      aria-hidden
      className={`pointer-events-none ${className}`}
    >
      <defs>
        <linearGradient id={`beamGrad-${reverse ? "r" : "f"}-${width}-${height}`} x1="0%" y1="0%" x2="100%" y2="0%">
          <stop offset="0%" stopColor="#ff8fd6" stopOpacity="0" />
          <stop offset="35%" stopColor="#ff8fd6" />
          <stop offset="55%" stopColor="#a78bfa" />
          <stop offset="75%" stopColor="#6ee7b7" />
          <stop offset="100%" stopColor="#6ee7b7" stopOpacity="0" />
        </linearGradient>
      </defs>
      {/* base path */}
      <path d={d} stroke="rgba(255,255,255,0.08)" strokeWidth="1.5" />
      {/* traveling beam */}
      <path
        d={d}
        stroke={`url(#beamGrad-${reverse ? "r" : "f"}-${width}-${height})`}
        strokeWidth="2"
        strokeLinecap="round"
        strokeDasharray="60 240"
        className="beam-dash"
        style={{ animationDelay: `${delay}s`, animationDirection: reverse ? "reverse" : "normal" }}
      />
      {/* node dots */}
      <circle cx={x1} cy={y1} r="3" fill="#a78bfa" opacity="0.7" />
      <circle cx={x2} cy={y2} r="3" fill="#6ee7b7" opacity="0.7" />
    </svg>
  );
}
