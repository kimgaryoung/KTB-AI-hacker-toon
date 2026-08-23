// Keep label room inside the SVG viewport. In particular, the lower-right
// "친밀감" label needs space to the right of its anchor point.
const cx = 143;
const cy = 132;
const maxR = 96;
// Mirrors the dashboard's "needsAttention" rule (score < 60) so the radar's
// dashed ring lines up with the same threshold used elsewhere in the app.
const ATTENTION_THRESHOLD = 60;

function polar(r, i, n) {
  const angle = ((i * 360) / n - 90) * (Math.PI / 180);
  return [cx + r * Math.cos(angle), cy + r * Math.sin(angle)];
}

export default function RadarChart({ values, labels, activeIndex = null, onActiveChange }) {
  const n = values.length;
  const rings = [0.25, 0.5, 0.75, 1].map((f, ri) => {
    const pts = Array.from({ length: n }, (_, i) => polar(maxR * f, i, n).join(',')).join(' ');
    return (
      <polygon className="radar-ring" key={ri} points={pts} fill="none" stroke="rgba(196,182,255,0.16)" strokeWidth="1" />
    );
  });

  const cutR = maxR * (ATTENTION_THRESHOLD / 100);
  const cutPts = Array.from({ length: n }, (_, i) => polar(cutR, i, n).join(',')).join(' ');

  const dataPts = values.map((v, i) => polar((maxR * v) / 100, i, n));
  const dataPoly = dataPts.map((p) => p.join(',')).join(' ');

  return (
    <svg className={`radar-chart${activeIndex !== null ? ' has-active' : ''}`} viewBox="0 0 286 276" style={{ width: '100%', height: 'auto' }}>
      {rings}
      <polygon
        className="radar-threshold"
        points={cutPts}
        fill="none"
        stroke="var(--accent-amber)"
        strokeWidth="1.4"
        strokeDasharray="4 3"
        opacity="0.85"
      />
      <polygon
        className="radar-data-area"
        points={dataPoly}
        fill="rgba(226,160,201,0.28)"
        stroke="rgba(226,160,201,0.48)"
        strokeWidth="1.4"
      />
      {dataPts.map((p, i) => {
        const low = values[i] < ATTENTION_THRESHOLD;
        return (
          <g key={i}>
            <circle
              className={`radar-point${activeIndex === i ? ' is-active' : ''}`}
              cx={p[0].toFixed(1)}
              cy={p[1].toFixed(1)}
              r="4"
              fill={low ? 'var(--accent-amber)' : 'var(--accent-pink)'}
              style={{ animationDelay: `${i * -0.42}s` }}
              role="button"
              tabIndex="0"
              onMouseEnter={() => onActiveChange?.(i)}
              onMouseLeave={() => onActiveChange?.(null)}
              onFocus={() => onActiveChange?.(i)}
              onBlur={() => onActiveChange?.(null)}
            />
            {activeIndex === i && (
              <circle className="radar-focus-ring" cx={p[0].toFixed(1)} cy={p[1].toFixed(1)} r="8" />
            )}
          </g>
        );
      })}
      {labels.map((label, i) => {
        const [x, y] = polar(maxR + 22, i, n);
        const anchor = Math.abs(x - cx) < 6 ? 'middle' : x > cx ? 'start' : 'end';
        return (
          <text
            key={label}
            className={`radar-label${activeIndex === i ? ' is-active' : ''}`}
            x={x.toFixed(1)}
            y={y.toFixed(1)}
            textAnchor={anchor}
            dominantBaseline="middle"
            fontSize="11.5"
            fontWeight="700"
            fill="var(--text-secondary)"
            role="button"
            tabIndex="0"
            onMouseEnter={() => onActiveChange?.(i)}
            onMouseLeave={() => onActiveChange?.(null)}
            onFocus={() => onActiveChange?.(i)}
            onBlur={() => onActiveChange?.(null)}
          >
            {label}
          </text>
        );
      })}
    </svg>
  );
}
