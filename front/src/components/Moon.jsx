import './Moon.css';

export default function Moon({ scale = 1, className = '' }) {
  return (
    <div className={`moon-scale-wrap ${className}`} style={{ transform: `scale(${scale})` }}>
      <div className="moon-orbit">
        <div className="moon">
          <i />
          <i className="b" />
        </div>
      </div>
    </div>
  );
}
