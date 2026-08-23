import { useEffect, useState } from 'react';

let uid = 0;

export default function Gauge({ score, size = 176 }) {
  const gradId = `gauge${uid++}`;
  const r = 76;
  const c = 2 * Math.PI * r;
  const off = c * (1 - score / 100);
  const [isDrawn, setIsDrawn] = useState(false);

  useEffect(() => {
    let frame;
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      frame = window.requestAnimationFrame(() => setIsDrawn(true));
      return () => window.cancelAnimationFrame(frame);
    }
    frame = window.requestAnimationFrame(() => {
      setIsDrawn(false);
      frame = window.requestAnimationFrame(() => setIsDrawn(true));
    });
    return () => window.cancelAnimationFrame(frame);
  }, [score]);

  return (
    <svg viewBox="0 0 176 176" width={size} height={size} style={{ transform: 'rotate(-90deg)' }}>
      <circle cx="88" cy="88" r={r} fill="none" stroke="rgba(196,182,255,0.14)" strokeWidth="12" />
      <circle
        cx="88"
        cy="88"
        r={r}
        fill="none"
        stroke={`url(#${gradId})`}
        strokeWidth="12"
        strokeLinecap="round"
        strokeDasharray={c.toFixed(1)}
        strokeDashoffset={(isDrawn ? off : c).toFixed(1)}
        style={{ transition: 'stroke-dashoffset 950ms cubic-bezier(0.22, 0.9, 0.28, 1)' }}
      />
      <defs>
        <linearGradient id={gradId} x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stopColor="#e2a0c9" />
          <stop offset="1" stopColor="#a595e8" />
        </linearGradient>
      </defs>
    </svg>
  );
}
