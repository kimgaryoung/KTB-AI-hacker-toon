import { CloseIcon, CheckIcon } from '../../components/Icons';
import '../../components/NewPersonModal.css';

function StepDot({ n, step }) {
  const done = n < step;
  const active = n === step;
  return (
    <div className={`step-dot ${active ? 'active' : ''} ${done ? 'done' : ''}`}>
      {done ? <CheckIcon strokeWidth="3" style={{ width: 13, height: 13 }} /> : n}
    </div>
  );
}

// 실제 NewPersonModal과 동일한 오버레이/카드/스텝퍼 마크업.
export default function DemoModal({ step, nextLabel = '다음', nextDisabled = false, children }) {
  return (
    <div className="modal-overlay intro-modal-overlay">
      <div className="modal-card">
        <button className="modal-close" type="button" tabIndex={-1} aria-label="닫기">
          <CloseIcon />
        </button>
        {step != null && (
          <div className="stepper">
            <StepDot n={1} step={step} />
            <div className={`step-line ${step > 1 ? 'done' : ''}`} />
            <StepDot n={2} step={step} />
            <div className={`step-line ${step > 2 ? 'done' : ''}`} />
            <StepDot n={3} step={step} />
          </div>
        )}
        {children}
        {step != null && (
          <div className="modal-footer">
            <button
              className="btn btn-ghost"
              type="button"
              tabIndex={-1}
              style={{ visibility: step > 1 ? 'visible' : 'hidden' }}
            >
              이전
            </button>
            <button className="btn btn-primary" type="button" tabIndex={-1} disabled={nextDisabled}>
              {nextLabel}
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
