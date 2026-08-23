import { useEffect, useState } from 'react';

// 경과 시간 기반 타이핑: 탭 전환 등으로 타이머가 스로틀되어도
// CSS 애니메이션과의 상대 타이밍이 유지된다.
export default function TypeText({ text, delay = 0, speed = 70, className = '' }) {
  const [n, setN] = useState(0);
  useEffect(() => {
    const start = performance.now();
    let iv = setInterval(() => {
      const elapsed = performance.now() - start - delay;
      const next = Math.max(0, Math.min(text.length, Math.floor(elapsed / speed)));
      setN(next);
      if (next >= text.length) clearInterval(iv);
    }, Math.min(speed, 50));
    return () => clearInterval(iv);
  }, [text, delay, speed]);
  return (
    <span className={`type-text ${className}`}>
      {text.slice(0, n)}
      <i className="type-caret" />
    </span>
  );
}
