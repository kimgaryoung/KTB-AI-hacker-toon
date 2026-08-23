import { useEffect, useState } from 'react';
import { Navigate, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { CheckIcon, InfoIcon } from '../components/Icons';
import userLogo from '../assets/images/UserLogo.png';
import './Auth.css';

const REQUIRED_KEYS = ['terms', 'privacy', 'data'];
const ALL_KEYS = [...REQUIRED_KEYS, 'marketing'];

export default function ConsentPage() {
  const { status, isLoggedIn, hasOnboarded, user, completeOnboarding } = useAuth();
  const navigate = useNavigate();
  const [checked, setChecked] = useState({ terms: false, privacy: false, data: false, marketing: false });
  const [showDetail, setShowDetail] = useState(false);

  useEffect(() => {
    if (hasOnboarded) navigate('/dashboard', { replace: true });
  }, [hasOnboarded, navigate]);

  if (status === 'checking') return null;
  if (!isLoggedIn) return <Navigate to="/login" replace />;

  const allRequiredChecked = REQUIRED_KEYS.every((k) => checked[k]);
  const allChecked = ALL_KEYS.every((k) => checked[k]);

  function toggle(key) {
    setChecked((prev) => ({ ...prev, [key]: !prev[key] }));
  }
  function toggleAll() {
    const next = !allChecked;
    setChecked({ terms: next, privacy: next, data: next, marketing: next });
  }
  function handleStart() {
    if (!allRequiredChecked) return;
    completeOnboarding();
    navigate('/dashboard');
  }

  return (
    <section className="consent-shell">
      <div className="consent-card">
        <div className="consent-check-badge">
          <CheckIcon strokeWidth="2.4" />
        </div>
        <div className="consent-tag">카카오 로그인 완료</div>
        <div className="consent-title">거의 다 됐어요</div>

        <div className="consent-profile">
          <div className="avatar user-logo-frame consent-profile-avatar">
            <img className="user-logo-image" src={userLogo} alt="사용자 프로필" />
          </div>
          <div>
            <div className="consent-profile-name">{user?.displayName || '우주인'}님</div>
            <div className="consent-profile-sub">카카오 계정에서 가져온 프로필이에요</div>
          </div>
        </div>

        <div className="consent-list">
          <div className="consent-row all">
            <button className={`cb ${allChecked ? 'checked' : ''}`} onClick={toggleAll} role="checkbox" aria-checked={allChecked} type="button">
              <CheckIcon strokeWidth="3" />
            </button>
            <div className="consent-label-wrap">
              <label className="consent-label" onClick={toggleAll}>약관 전체 동의</label>
            </div>
          </div>

          <ConsentRow label="서비스 이용약관 동의" required checked={checked.terms} onToggle={() => toggle('terms')} />
          <ConsentRow label="개인정보 수집 및 이용 동의" required checked={checked.privacy} onToggle={() => toggle('privacy')} />

          <div className="consent-row">
            <button className={`cb ${checked.data ? 'checked' : ''}`} onClick={() => toggle('data')} role="checkbox" aria-checked={checked.data} type="button">
              <CheckIcon strokeWidth="3" />
            </button>
            <div className="consent-label-wrap">
              <label className="consent-label" onClick={() => toggle('data')}>
                대화 데이터 이용 동의 <span className="consent-required">필수</span>
                <button
                  className="consent-info-btn"
                  aria-label="자세히 보기"
                  type="button"
                  onClick={(e) => { e.stopPropagation(); setShowDetail((v) => !v); }}
                >
                  <InfoIcon />
                </button>
              </label>
              {showDetail && (
                <div className="consent-detail">
                  본인 소유의 대화 데이터만 업로드하며, 제3자 정보를 무단으로 수집하거나 다른 목적에 활용하지 않아요.
                  업로드된 대화는 관계 온도 분석 용도로만 사용돼요.
                </div>
              )}
            </div>
          </div>

          <ConsentRow label="마케팅 정보 수신 동의" optional checked={checked.marketing} onToggle={() => toggle('marketing')} />
        </div>

        <div className="consent-cta">
          <button className="btn btn-primary btn-block" disabled={!allRequiredChecked} onClick={handleStart}>
            시작하기
          </button>
        </div>
      </div>
    </section>
  );
}

function ConsentRow({ label, required, optional, checked, onToggle }) {
  return (
    <div className="consent-row">
      <button className={`cb ${checked ? 'checked' : ''}`} onClick={onToggle} role="checkbox" aria-checked={checked} type="button">
        <CheckIcon strokeWidth="3" />
      </button>
      <div className="consent-label-wrap">
        <label className="consent-label" onClick={onToggle}>
          {label} {required && <span className="consent-required">필수</span>}
          {optional && <span className="consent-optional">선택</span>}
        </label>
      </div>
    </div>
  );
}
