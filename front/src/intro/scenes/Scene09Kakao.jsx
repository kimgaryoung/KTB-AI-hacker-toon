import TypeText from '../parts/TypeText';

// 실제 카카오톡 채팅방 스킨을 재현한 데모 화면 (인트로 전용).
export default function Scene09Kakao() {
  return (
    <div className="intro-app kko-app">
      <div className="intro-app-scale">
        <div className="kko-shell">
          <div className="kko-topbar">
            <span className="kko-back">‹</span>
            <span className="kko-title">홍길동 ♥</span>
            <span className="kko-icons">☰</span>
          </div>
          <div className="kko-scroll">
            <div className="kko-date">8월 20일 수요일</div>
            <div className="kko-row me">
              <span className="kko-time">오후 9:12</span>
              <div className="kko-bubble me">
                <TypeText
                  text="나 최근에.. 우리 관계가 조금 권태기인 것 같아. 같이 한강 가서 이야기해볼까?"
                  delay={150}
                  speed={40}
                />
              </div>
            </div>
            <div className="kko-row other intro-kko-think">
              <div className="kko-avatar">홍</div>
              <div className="kko-bubble other kko-typing">
                <i />
                <i />
                <i />
              </div>
            </div>
            <div className="kko-row other intro-kko-reply">
              <div className="kko-avatar">홍</div>
              <div>
                <div className="kko-name">홍길동</div>
                <div className="kko-bubble other">
                  그랬구나.. 몰라줘서 미안해.
                  <br />
                  한강 가서 같이 맛있는 것도 먹고 이야기해보자 :)
                </div>
              </div>
              <span className="kko-time">오후 9:13</span>
            </div>
            <div className="kko-row me intro-kko-heart">
              <span className="kko-time">오후 9:14</span>
              <div className="kko-heart">❤️</div>
            </div>
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
