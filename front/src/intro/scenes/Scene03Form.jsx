import DemoAppShell from '../parts/DemoAppShell';
import DashboardDemo from '../parts/DashboardDemo';
import DemoModal from '../parts/DemoModal';
import DemoCursor from '../parts/DemoCursor';
import TypeText from '../parts/TypeText';
import { RELATIONSHIP_TYPES } from '../../data/constants';

export default function Scene03Form({ name = '홍길동', pickType = 'ROMANTIC_PARTNER' }) {
  return (
    <DemoAppShell
      active="dashboard"
      cursor={
        <>
          <DemoModal step={1}>
            <div>
              <div className="modal-title">어떤 관계인가요?</div>
              <div className="modal-sub">등록할 인물의 정보를 알려주세요</div>
              <label className="field-label">이름</label>
              <div className="text-input intro-fake-input" style={{ marginBottom: 8 }}>
                <TypeText text={name} delay={150} speed={110} />
              </div>
              <label className="field-label" style={{ marginTop: 10 }}>
                관계 유형
              </label>
              <div className="chip-row">
                {RELATIONSHIP_TYPES.map((t) => (
                  <button
                    key={t.value}
                    type="button"
                    tabIndex={-1}
                    className={`chip-btn ${t.value === pickType ? 'intro-pick' : ''}`}
                  >
                    {t.label}
                  </button>
                ))}
              </div>
            </div>
          </DemoModal>
          <DemoCursor variant="form" />
        </>
      }
    >
      <DashboardDemo />
    </DemoAppShell>
  );
}
