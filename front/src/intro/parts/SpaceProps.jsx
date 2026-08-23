export function Rocket({ size = 56 }) {
  return (
    <svg viewBox="0 0 100 100" width={size} height={size} fill="none">
      <path d="M50 12c10 8 15 22 15 36 0 8-2 15-5 20H40c-3-5-5-12-5-20 0-14 5-28 15-36z" fill="#f4f0fb" />
      <circle cx="50" cy="40" r="8" fill="#a595e8" />
      <circle cx="50" cy="40" r="4.5" fill="#f6e9ff" />
      <path d="M35 52c-7 3-11 10-11 18 5-1 10-3 13-6" fill="#e2a0c9" />
      <path d="M65 52c7 3 11 10 11 18-5-1-10-3-13-6" fill="#e2a0c9" />
      <path d="M44 70h12l-2 8h-8l-2-8z" fill="#c9bdf0" />
      <path d="M47 80c0 5 1 8 3 11 2-3 3-6 3-11h-6z" fill="#f0b56c" />
    </svg>
  );
}

export function Planet({ size = 56 }) {
  return (
    <svg viewBox="0 0 100 100" width={size} height={size} fill="none">
      <circle cx="50" cy="50" r="24" fill="#a595e8" />
      <circle cx="42" cy="44" r="5" fill="rgba(255,255,255,0.35)" />
      <circle cx="58" cy="58" r="3.4" fill="rgba(255,255,255,0.22)" />
      <ellipse
        cx="50"
        cy="52"
        rx="38"
        ry="10"
        stroke="#e2a0c9"
        strokeWidth="4"
        fill="none"
        transform="rotate(-16 50 52)"
      />
    </svg>
  );
}
