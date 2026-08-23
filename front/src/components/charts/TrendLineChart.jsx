// Assumes data has at least 2 points — callers should show a fallback
// message instead of rendering this when a relationship only has one
// analyzed week so far.
export default function TrendLineChart({ data, labels }) {
  const w = 760;
  const h = 210;
  const padL = 34;
  const padR = 18;
  const padT = 16;
  const padB = 26;
  const min = Math.min(...data) - 6;
  const max = Math.max(...data) + 6;
  const X = (i) => padL + (i * (w - padL - padR)) / (data.length - 1);
  const Y = (v) => h - padB - ((v - min) / (max - min)) * (h - padT - padB);

  const gridLevels = [0, 25, 50, 75, 100].filter((v) => v >= min - 6 && v <= max + 6);
  const pts = data.map((v, i) => [X(i), Y(v)]);
  const line = pts.map((p, i) => `${i ? 'L' : 'M'}${p[0].toFixed(1)},${p[1].toFixed(1)}`).join(' ');
  const area = `${line} L${pts[pts.length - 1][0].toFixed(1)},${h - padB} L${pts[0][0].toFixed(1)},${h - padB} Z`;
  const last = pts[pts.length - 1];

  return (
    <svg viewBox={`0 0 ${w} ${h}`} style={{ width: '100%', height: 'auto', minWidth: 520 }}>
      <defs>
        <linearGradient id="lineArea" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor="#e2a0c9" stopOpacity="0.32" />
          <stop offset="1" stopColor="#e2a0c9" stopOpacity="0" />
        </linearGradient>
      </defs>
      {gridLevels.map((v) => (
        <g key={v}>
          <line
            x1={padL}
            y1={Y(v).toFixed(1)}
            x2={w - padR}
            y2={Y(v).toFixed(1)}
            stroke="rgba(196,182,255,0.10)"
          />
          <text x="8" y={(Y(v) + 4).toFixed(1)} fontSize="10.5" fill="var(--text-muted)">
            {v}
          </text>
        </g>
      ))}
      <path d={area} fill="url(#lineArea)" />
      <path d={line} fill="none" stroke="var(--accent-pink)" strokeWidth="2.4" strokeLinejoin="round" strokeLinecap="round" />
      {pts.slice(0, -1).map((p, i) => (
        <circle
          key={i}
          cx={p[0].toFixed(1)}
          cy={p[1].toFixed(1)}
          r="2.6"
          fill="var(--bg-deep)"
          stroke="var(--accent-pink)"
          strokeWidth="1.6"
        />
      ))}
      <circle cx={last[0].toFixed(1)} cy={last[1].toFixed(1)} r="5.5" fill="var(--accent-pink)" stroke="var(--bg-deep)" strokeWidth="2" />
      {data.map((_, i) => (
        <text key={i} x={X(i).toFixed(1)} y={h - 6} fontSize="10" textAnchor="middle" fill="var(--text-muted)">
          {labels?.[i] ?? `${i + 1}주`}
        </text>
      ))}
    </svg>
  );
}
