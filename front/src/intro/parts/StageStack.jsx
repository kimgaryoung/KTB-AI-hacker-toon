export default function StageStack({ stages }) {
  if (!stages.length) return null;
  return (
    <div className="stage-stack">
      {stages.map((s) => (
        <div key={s} className="stage-chip">
          {s} ✓
        </div>
      ))}
    </div>
  );
}
