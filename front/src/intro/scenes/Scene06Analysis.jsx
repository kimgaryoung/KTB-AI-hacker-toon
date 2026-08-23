import { useEffect, useState } from 'react';
import DemoAppShell from '../parts/DemoAppShell';
import DashboardDemo from '../parts/DashboardDemo';
import DemoModal from '../parts/DemoModal';
import DemoCursor from '../parts/DemoCursor';
import Astronaut from '../../components/Astronaut';
import { CheckIcon } from '../../components/Icons';

const CIRCUMFERENCE = 2 * Math.PI * 27;
const STAGES = ['메시지 패턴을 살펴보는 중', '감정 흐름을 파악하는 중', '관계 온도를 측정하는 중'];

export default function Scene06Analysis({ personName = '홍길동' }) {
  const [stage, setStage] = useState(0);
  const [done, setDone] = useState(false);
  useEffect(() => {
    const timers = [
      setTimeout(() => setStage(1), 500),
      setTimeout(() => setStage(2), 1000),
      setTimeout(() => setDone(true), 1500),
    ];
    return () => timers.forEach(clearTimeout);
  }, []);
  return (
    <DemoAppShell
      active="dashboard"
      cursor={
        <>
          <DemoModal step={null}>
            {!done ? (
              <div className="loading-state">
                <div className="loading-astro">
                  <Astronaut size={76} />
                </div>
                <div className="loading-title">대화를 분석하고 있어요</div>
                <div className="loading-sub">{STAGES[stage]}</div>
                <svg className="progress-ring" viewBox="0 0 64 64">
                  <circle className="progress-track" cx="32" cy="32" r="27" />
                  <circle
                    className="progress-bar intro-progress"
                    cx="32"
                    cy="32"
                    r="27"
                    strokeDasharray={CIRCUMFERENCE.toFixed(1)}
                  />
                </svg>
              </div>
            ) : (
              <div className="success-state">
                <div className="success-badge">
                  <CheckIcon strokeWidth="2.4" />
                </div>
                <div className="loading-title">분석이 끝났어요</div>
                <div className="loading-sub">{personName}님과의 관계 온도가 반영됐어요</div>
                <button className="btn btn-primary" type="button" tabIndex={-1}>
                  리포트 보기
                </button>
              </div>
            )}
          </DemoModal>
          {done && <DemoCursor variant="analysis" />}
        </>
      }
    >
      <DashboardDemo />
    </DemoAppShell>
  );
}
