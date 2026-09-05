export default function BeamMark({ size = 26 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 32 32" fill="none" aria-hidden>
      <defs>
        <linearGradient id="bm" x1="4" y1="26" x2="28" y2="6">
          <stop offset="0" stopColor="#ff8fd6" />
          <stop offset="0.5" stopColor="#a78bfa" />
          <stop offset="1" stopColor="#6ee7b7" />
        </linearGradient>
      </defs>
      <path
        d="M5 24 C 12 24, 13 8, 20 8 C 25 8, 27 12, 27 16"
        stroke="url(#bm)"
        strokeWidth="3.2"
        strokeLinecap="round"
      />
      <circle cx="27" cy="16" r="2.6" fill="url(#bm)" />
    </svg>
  );
}
