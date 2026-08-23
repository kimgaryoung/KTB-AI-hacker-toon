let uid = 0;

export default function Astronaut({ size = 64, className }) {
  const visorId = `astroVisor${uid++}`;
  return (
    <svg viewBox="0 0 100 100" width={size} height={size} fill="none" className={className}>
      <ellipse cx="50" cy="82" rx="16" ry="5" fill="rgba(0,0,0,0.18)" />
      <rect x="38" y="52" width="24" height="16" rx="6" fill="#c9bdf0" />
      <path d="M32 58c-6 2-9 8-7 13" stroke="#e2a0c9" strokeWidth="7" strokeLinecap="round" fill="none" />
      <path d="M68 58c6 2 9 6 6 12" stroke="#e2a0c9" strokeWidth="7" strokeLinecap="round" fill="none" />
      <rect x="40" y="66" width="9" height="14" rx="4" fill="#e9e3fb" />
      <rect x="51" y="66" width="9" height="14" rx="4" fill="#e9e3fb" />
      <ellipse cx="50" cy="46" rx="26" ry="24" fill="#f4f0fb" />
      <circle cx="50" cy="44" r="18" fill={`url(#${visorId})`} />
      <path
        d="M42 40c2-4 6-6 10-6"
        stroke="rgba(255,255,255,0.55)"
        strokeWidth="3"
        strokeLinecap="round"
        fill="none"
      />
      <path
        d="M44 46l3 3 6-7"
        stroke="#fff"
        strokeWidth="2.4"
        strokeLinecap="round"
        strokeLinejoin="round"
        fill="none"
        opacity="0.85"
      />
      <circle cx="27" cy="30" r="2" fill="#f0b56c" />
      <circle cx="76" cy="55" r="1.6" fill="#a595e8" />
      <defs>
        <radialGradient id={visorId} cx="0.35" cy="0.3" r="0.9">
          <stop offset="0" stopColor="#f6e9ff" />
          <stop offset="0.55" stopColor="#c9a8e8" />
          <stop offset="1" stopColor="#6f5cc4" />
        </radialGradient>
      </defs>
    </svg>
  );
}

export function MiniAstronaut({ size = 20 }) {
  const visorId = `miniVisor${uid++}`;
  return (
    <svg viewBox="0 0 100 100" width={size} height={size} fill="none">
      <ellipse cx="50" cy="46" rx="26" ry="24" fill="#f4f0fb" />
      <circle cx="50" cy="44" r="18" fill={`url(#${visorId})`} />
      <defs>
        <radialGradient id={visorId} cx="0.35" cy="0.3" r="0.9">
          <stop offset="0" stopColor="#f6e9ff" />
          <stop offset="0.55" stopColor="#c9a8e8" />
          <stop offset="1" stopColor="#6f5cc4" />
        </radialGradient>
      </defs>
    </svg>
  );
}
