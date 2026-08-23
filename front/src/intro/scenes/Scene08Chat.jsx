import DemoAppShell from '../parts/DemoAppShell';
import TypeText from '../parts/TypeText';
import { MiniAstronaut } from '../../components/Astronaut';
import { SendIcon } from '../../components/Icons';
import { avatarGradientFor } from '../../utils/avatar';
import '../../pages/Chat.css';

const SUGGESTED_QUESTIONS = ['요즘 좀 나아진 것 같아', '이 관계 계속 유지해도 될까?'];

const DEFAULT_ANSWER = (
  <>
    연인과의 관계가 예전 같지 않아 많이 속상하셨겠어요.
    <br />
    최근 대화를 분석해보면 7월 20일 · 26일 · 28일에도{' '}
    <span className="intro-accent">'사랑해', '좋아해'</span> 같은 애정 표현이 이어졌고,
    상대방이 먼저 연락한 비율도 약 <span className="intro-accent">70%</span>로 높아요. 또 두
    분이 <span className="intro-accent">한강 이야기</span>를 자주 나누고 있어요.
    <br />
    <span className="intro-hi">먼저 한강 데이트를 제안해보는 건 어떨까요?</span>
  </>
);

export default function Scene08Chat({
  name = '홍길동',
  question = '요즘 권태기인 것 같아.. 대화 분석 결과는 어때?',
  answer = DEFAULT_ANSWER,
  suggested = SUGGESTED_QUESTIONS,
}) {
  return (
    <DemoAppShell active="chat">
      <section className="chat-shell">
        <aside className="rooms-panel">
          <div className="rooms-panel-title">상담 기록</div>
          <button className="room-item active" type="button" tabIndex={-1}>
            <div className="room-avatar" style={{ background: avatarGradientFor('demo') }}>
              {name[0]}
            </div>
            <div style={{ flex: 1, minWidth: 0 }}>
              <div className="room-name-row">
                <span className="room-name">{name}</span>
                <span className="room-time">방금</span>
              </div>
              <div className="room-preview">대화 분석 기반 상담</div>
            </div>
          </button>
        </aside>
        <div className="chat-main">
          <div className="chat-header">
            <h2>{name}님과의 상담</h2>
            <p>{name}님과의 대화 데이터 기반 상담</p>
          </div>
          <div className="chat-scroll">
            <div className="chat-col">
              <div className="bubble-row user">
                <div className="bubble">
                  <TypeText text={question} delay={150} speed={48} />
                </div>
              </div>
              <div className="bubble-row ai intro-ai-think">
                <div className="bubble-avatar">
                  <MiniAstronaut />
                </div>
                <div className="bubble">생각을 정리하고 있어요...</div>
              </div>
              <div className="bubble-row ai intro-ai-answer">
                <div className="bubble-avatar">
                  <MiniAstronaut />
                </div>
                <div className="bubble">{answer}                </div>
              </div>
            </div>
          </div>
          <div className="chat-input-area">
            <div className="chip-suggest-row">
              {suggested.map((q) => (
                <button key={q} className="suggest-chip" type="button" tabIndex={-1}>
                  {q}
                </button>
              ))}
            </div>
            <div className="chat-input-row">
              <input placeholder="궁금한 점을 편하게 물어보세요" readOnly tabIndex={-1} />
              <button className="send-btn" type="button" tabIndex={-1} aria-label="전송">
                <SendIcon />
              </button>
            </div>
          </div>
        </div>
      </section>
    </DemoAppShell>
  );
}
