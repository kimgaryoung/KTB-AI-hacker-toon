import DemoCursor from '../parts/DemoCursor';
import Astronaut from '../../components/Astronaut';
import Moon from '../../components/Moon';
import { LogoMark, KakaoIcon, SparkleIcon, CheckIcon, ChatIcon } from '../../components/Icons';
import wordmark from '../../assets/images/wouldu-wordmark.png';
import '../../pages/Auth.css';

// 실제 LoginPage와 동일한 마크업을 1240x760 데모 화면으로 재생한다.
export default function Scene01Login() {
  return (
    <div className="intro-app">
      <div className="intro-app-scale">
        <section className="auth-shell">
          <div className="auth-hero">
            <svg className="auth-constellation" viewBox="0 0 500 600" fill="none">
              <g stroke="rgba(244,240,251,0.28)" strokeWidth="1">
                <line x1="60" y1="90" x2="160" y2="150" />
                <line x1="160" y1="150" x2="140" y2="260" />
                <line x1="160" y1="150" x2="280" y2="120" />
                <line x1="280" y1="120" x2="360" y2="200" />
                <line x1="140" y1="260" x2="240" y2="330" />
                <line x1="240" y1="330" x2="360" y2="200" />
                <line x1="240" y1="330" x2="200" y2="450" />
                <line x1="200" y1="450" x2="320" y2="500" />
              </g>
              <g className="auth-constellation-stars" fill="rgba(244,240,251,0.85)">
                <circle cx="60" cy="90" r="3" />
                <circle cx="160" cy="150" r="3.4" />
                <circle cx="280" cy="120" r="2.6" />
                <circle cx="360" cy="200" r="3" />
                <circle cx="140" cy="260" r="2.6" />
                <circle cx="240" cy="330" r="3.4" />
                <circle cx="200" cy="450" r="2.6" />
                <circle cx="320" cy="500" r="3" />
              </g>
            </svg>
            <div className="auth-logo">
              <LogoMark size={38} />
              <img className="auth-logo-name-img" src={wordmark} alt="WouldU" />
            </div>
            <h1 className="auth-catch">
              감이 아니라 데이터로,
              <br />
              <em>관계를 이해하는 시간</em>
            </h1>
            <div className="auth-value-list">
              <div className="auth-value-item">
                <SparkleIcon />
                대화 속에 흩어진 패턴을 읽어, 관계마다 온도를 매겨드려요
              </div>
              <div className="auth-value-item">
                <CheckIcon />
                모든 점수엔 근거가 함께 따라와요, 블랙박스는 없어요
              </div>
              <div className="auth-value-item">
                <ChatIcon />
                확정 짓지 않고, 판단은 늘 당신의 몫으로 남겨둬요
              </div>
            </div>
            <div className="auth-hero-deco" aria-hidden="true">
              <div className="moon-slot">
                <Moon scale={1.9} />
              </div>
              <div className="astro-slot intro-astro-rise">
                <Astronaut size={84} />
              </div>
            </div>
          </div>
          <div className="auth-panel">
            <div className="auth-panel-inner">
              <div className="auth-title">시작하기</div>
              <p className="auth-sub">카카오 계정으로 간편하게 시작하세요</p>
              <span className="kakao-btn">
                <KakaoIcon />
                카카오로 시작하기
              </span>
              <p className="auth-note">
                카카오톡 대화 내보내기 파일을 분석에 활용하기 때문에
                <br />
                카카오 계정으로만 로그인할 수 있어요
              </p>
              <p className="auth-legal">
                <a href="#terms">이용약관</a>·<a href="#privacy">개인정보처리방침</a>
              </p>
            </div>
          </div>
        </section>
        <DemoCursor variant="login" />
      </div>
    </div>
  );
}
