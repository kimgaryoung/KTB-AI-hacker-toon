import { LogoMark, DashboardIcon, PersonIcon, ChatIcon, GuideIcon, ChevronsLeftIcon } from '../../components/Icons';
import wordmark from '../../assets/images/wouldu-wordmark.png';
import '../../components/AppLayout.css';

// 실제 AppLayout과 동일한 마크업/클래스를 쓰는 인트로 전용 데모 셸.
// 1240x760 가상 화면을 0.68 배율로 축소해 실제 서비스 화면처럼 보여준다.
export default function DemoAppShell({ active, children, cursor = null }) {
  return (
    <div className="intro-app">
      <div className="intro-app-scale">
        <div className="app-shell">
          <aside className="sidebar">
            <div className="brand">
              <LogoMark size={34} />
              <div className="brand-text">
                <img className="brand-name-img" src={wordmark} alt="WouldU" />
                <div className="brand-tag">끝없는 관계의 우주</div>
              </div>
              <button className="sidebar-toggle" type="button" tabIndex={-1}>
                <ChevronsLeftIcon />
              </button>
            </div>
            <nav className="nav">
              <span className={`nav-item ${active === 'dashboard' ? 'active' : ''}`}>
                <DashboardIcon />
                <span className="nav-label">메인 대시보드</span>
              </span>
              <span className={`nav-item ${active === 'report' ? 'active' : ''}`}>
                <PersonIcon />
                <span className="nav-label">인물별 관계</span>
              </span>
              <span className={`nav-item ${active === 'chat' ? 'active' : ''}`}>
                <ChatIcon />
                <span className="nav-label">AI 챗봇</span>
              </span>
              <span className="nav-item">
                <GuideIcon />
                <span className="nav-label">사용 가이드</span>
              </span>
            </nav>
            <div className="sidebar-spacer" />
            <div className="profile">
              <div className="profile-avatar">우</div>
              <div className="profile-text" style={{ flex: 1, minWidth: 0 }}>
                <div className="profile-name">우주인님</div>
                <span className="profile-sub">로그아웃</span>
              </div>
            </div>
          </aside>
          <main className="main">{children}</main>
        </div>
        {cursor}
      </div>
    </div>
  );
}
