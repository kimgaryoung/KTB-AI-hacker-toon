let uid = 0;

export default function Sparkline({ data, width = 120, height = 32, color = '#e2a0c9' }) {
  const gradId = `spark${uid++}`;
  const min = Math.min(...data);
  const max = Math.max(...data);
  const pad = 3;
  const pts = data.map((v, i) => {
    const x = pad + (i * (width - 2 * pad)) / (data.length - 1);
    const y = height - pad - (max === min ? 0.5 : (v - min) / (max - min)) * (height - 2 * pad);
    return [x, y];
  });
  const line = pts.map((p, i) => `${i ? 'L' : 'M'}${p[0].toFixed(1)},${p[1].toFixed(1)}`).join(' ');
  const area = `${line} L${pts[pts.length - 1][0].toFixed(1)},${height} L${pts[0][0].toFixed(1)},${height} Z`;
  const last = pts[pts.length - 1];

  return (
    <svg viewBox={`0 0 ${width} ${height}`} preserveAspectRatio="none" width="100%" height={height}>
      <defs>
        <linearGradient id={gradId} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor={color} stopOpacity="0.35" />
          <stop offset="1" stopColor={color} stopOpacity="0" />
        </linearGradient>
      </defs>
      <path d={area} fill={`url(#${gradId})`} />
      <path d={line} fill="none" stroke={color} strokeWidth="1.6" strokeLinejoin="round" strokeLinecap="round" />
      <circle cx={last[0].toFixed(1)} cy={last[1].toFixed(1)} r="2.4" fill={color} />
    </svg>
  );
}
