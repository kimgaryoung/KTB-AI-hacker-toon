import { useEffect, useRef, useState } from 'react';
import { PixelPlayIcon } from '../components/Icons';
import '../intro/intro.css';

// 인트로 데모 화면(.intro-app)의 기준 크기. 카드 폭에 맞춰 통째로 축소한다.
const BASE_W = 843;
const BASE_H = 517;

export default function GuideDemoPlayer({ scenes, poster = null, posterLabel = '데모 재생하기' }) {
  const [state, setState] = useState('idle'); // idle | playing | ended
  const [idx, setIdx] = useState(0);
  const [scale, setScale] = useState(0.4);
  const boxRef = useRef(null);

  useEffect(() => {
    const el = boxRef.current;
    if (!el) return undefined;
    const update = () => setScale(el.clientWidth / BASE_W);
    update();
    const ro = new ResizeObserver(update);
    ro.observe(el);
    return () => ro.disconnect();
  }, []);

  useEffect(() => {
    if (state !== 'playing') return undefined;
    if (idx >= scenes.length) {
      setState('ended');
      return undefined;
    }
    const t = setTimeout(() => setIdx((i) => i + 1), scenes[idx].dur);
    return () => clearTimeout(t);
  }, [state, idx, scenes]);

  const play = () => {
    setIdx(0);
    setState('playing');
  };

  const scene = scenes[Math.min(idx, scenes.length - 1)];

  return (
    <div className="guide-demo" ref={boxRef} style={{ aspectRatio: `${BASE_W} / ${BASE_H}` }}>
      {state === 'playing' && (
        <>
          <div className="guide-demo-scale" style={{ transform: `scale(${scale})` }}>
            <div className="intro-scene-swap" key={scene.id}>
              {scene.node}
            </div>
          </div>
          {scene.label && (
            <div className="guide-demo-caption" key={`caption-${scene.id}`}>
              {scene.label}
            </div>
          )}
        </>
      )}
      {state !== 'playing' && (
        <>
          {poster && (
            <div className="guide-demo-scale" style={{ transform: `scale(${scale})` }}>
              {poster}
            </div>
          )}
          <button
            type="button"
            className={`guide-demo-poster${poster ? ' has-preview' : ''}`}
            onClick={play}
          >
            <span className="pixel-play-chip">
              <PixelPlayIcon />
              {state === 'ended' ? '다시 보기' : posterLabel}
            </span>
          </button>
        </>
      )}
    </div>
  );
}
