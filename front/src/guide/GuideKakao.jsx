import TypeText from '../intro/parts/TypeText';

// 시나리오 데이터 기반 카카오톡 데모 화면 (사용 가이드 전용).
// 인트로 Scene09의 kko-* 스킨을 재사용하되, 메시지 등장 타이밍을 데이터로 받는다.
// history를 주면 이전 대화가 위에 깔린 채로 새 메시지가 그 밑에 이어진다.

function StaticRow({ m, senderName, avatarChar }) {
  if (m.who === 'me') {
    return (
      <div className="kko-row me">
        <span className="kko-time">{m.time}</span>
        <div className="kko-bubble me">{m.text}</div>
      </div>
    );
  }
  return (
    <div className="kko-row other">
      <div className="kko-avatar">{avatarChar}</div>
      <div>
        <div className="kko-name">{senderName}</div>
        <div className="kko-bubble other">{m.text}</div>
      </div>
      <span className="kko-time">{m.time}</span>
    </div>
  );
}

export default function GuideKakao({
  title,
  senderName,
  avatarChar,
  date,
  messages,
  heart = null,
  history = null,
  historyDate = null,
  staticAll = false,
}) {
  return (
    <div className="intro-app kko-app">
      <div className="intro-app-scale">
        <div className="kko-shell">
          <div className="kko-topbar">
            <span className="kko-back">‹</span>
            <span className="kko-title">{title}</span>
            <span className="kko-icons">☰</span>
          </div>
          <div className={`kko-scroll${history ? ' gk-scroll-bottom' : ''}`}>
            {history && (
              <div className="gk-history">
                {historyDate && <div className="kko-date">{historyDate}</div>}
                {history.map((m, i) => (
                  <StaticRow m={m} senderName={senderName} avatarChar={avatarChar} key={i} />
                ))}
              </div>
            )}
            <div className="kko-date">{date}</div>
            {messages.map((m, i) =>
              staticAll ? (
                <StaticRow m={m} senderName={senderName} avatarChar={avatarChar} key={i} />
              ) : m.who === 'me' ? (
                <div className="kko-row me gk-in" style={{ animationDelay: `${m.at}ms` }} key={i}>
                  <span className="kko-time">{m.time}</span>
                  <div className="kko-bubble me">
                    <TypeText text={m.text} delay={m.at + 250} speed={m.speed ?? 28} />
                  </div>
                </div>
              ) : (
                <div className="kko-row other gk-in" style={{ animationDelay: `${m.at}ms` }} key={i}>
                  <div className="kko-avatar">{avatarChar}</div>
                  <div>
                    <div className="kko-name">{senderName}</div>
                    <div className="kko-bubble other">{m.text}</div>
                  </div>
                  <span className="kko-time">{m.time}</span>
                </div>
              )
            )}
            {heart && (
              <div
                className={`kko-row me${staticAll ? '' : ' gk-in'}`}
                style={staticAll ? undefined : { animationDelay: `${heart.at}ms` }}
              >
                <span className="kko-time">{heart.time}</span>
                <div className="kko-heart">❤️</div>
              </div>
            )}
          </div>
          <div className="kko-inputbar">
            <span className="kko-plus">+</span>
            <div className="kko-input" />
            <span className="kko-send">#</span>
          </div>
        </div>
      </div>
    </div>
  );
}
