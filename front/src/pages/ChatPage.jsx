import { useEffect, useRef, useState } from 'react';
import { Navigate, useNavigate, useParams } from 'react-router-dom';
import {
  listConsultations,
  listMessages,
  sendMessage as sendMessageApi,
  openMessageStream,
} from '../api/consultations';
import { listRelationships } from '../api/relationships';
import { fetchSupportResources } from '../api/supportResources';
import { avatarGradientFor, initialsOf } from '../utils/avatar';
import { pointImageFor } from '../utils/pointImage';
import { MiniAstronaut } from '../components/Astronaut';
import { SendIcon, WarnIcon } from '../components/Icons';
import './Chat.css';

const SUGGESTED_QUESTIONS = ['요즘 좀 나아진 것 같아', '이 관계 계속 유지해도 될까?', '요즘 대화가 눈에 띄게 줄어든 것 같아'];

export default function ChatPage() {
  const { id } = useParams();
  const navigate = useNavigate();

  const [rooms, setRooms] = useState([]);
  const [roomsLoading, setRoomsLoading] = useState(true);

  useEffect(() => {
    Promise.all([listConsultations(), listRelationships()])
      .then(([consultations, relationships]) => {
        const relationshipById = new Map(relationships.map((relationship) => [relationship.id, relationship]));
        setRooms(consultations.map((consultation) => ({
          ...consultation,
          relationship: {
            ...consultation.relationship,
            ...relationshipById.get(consultation.relationship.id),
          },
        })));
      })
      .finally(() => setRoomsLoading(false));
  }, []);

  if (!roomsLoading && !id && rooms.length > 0) {
    return <Navigate to={`/chat/${rooms[0].id}`} replace />;
  }

  return (
    <section className="chat-shell">
      <aside className="rooms-panel">
        <div className="rooms-panel-title">상담 기록</div>
        {!roomsLoading && rooms.length === 0 && (
          <p style={{ fontSize: 12, color: 'var(--text-muted)', padding: '8px 4px' }}>
            아직 상담이 없어요. 인물별 리포트에서 &quot;AI와 상담하기&quot;로 시작해 보세요.
          </p>
        )}
        {rooms.map((r) => (
          <button
            key={r.id}
            className={`room-item ${r.id === id ? 'active' : ''}`}
            onClick={() => navigate(`/chat/${r.id}`)}
          >
            {pointImageFor(r.relationship.score) ? (
              <img className="room-avatar chat-point-avatar" src={pointImageFor(r.relationship.score)} alt="" />
            ) : (
              <div className="room-avatar" style={{ background: avatarGradientFor(r.relationship.id) }}>
                {r.relationship.initial || initialsOf(r.relationship.name)}
              </div>
            )}
            <div style={{ flex: 1, minWidth: 0 }}>
              <div className="room-name-row">
                <span className="room-name">{r.relationship.name}</span>
                <span className="room-time">{formatTime(r.lastMessageAt)}</span>
              </div>
              <div className="room-preview">{r.lastMessagePreview}</div>
            </div>
          </button>
        ))}
      </aside>

      {id ? <ChatRoom key={id} consultationId={id} rooms={rooms} /> : <EmptyChatMain roomsLoading={roomsLoading} />}
    </section>
  );
}

function ChatRoom({ consultationId, rooms }) {
  const room = rooms.find((r) => r.id === consultationId);
  const [messages, setMessages] = useState([]);
  const [loading, setLoading] = useState(true);
  const [input, setInput] = useState('');
  const [resourceModal, setResourceModal] = useState(null);
  const [sending, setSending] = useState(false);
  const scrollRef = useRef(null);
  const sourceRef = useRef(null);
  const sendingRef = useRef(false);

  useEffect(() => {
    setLoading(true);
    sendingRef.current = false;
    setSending(false);
    listMessages(consultationId)
      .then(setMessages)
      .finally(() => setLoading(false));
    return () => sourceRef.current?.close();
  }, [consultationId]);

  useEffect(() => {
    if (scrollRef.current) scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
  }, [messages]);

  async function refreshMessages() {
    const fresh = await listMessages(consultationId);
    setMessages(fresh);
  }

  async function handleSend(text) {
    const trimmed = text.trim();
    if (!trimmed || sendingRef.current) return;
    sendingRef.current = true;
    setSending(true);
    setInput('');
    try {
      const accepted = await sendMessageApi(consultationId, trimmed);
      setMessages((prev) => [...prev, accepted.userMessage, accepted.assistantMessage]);

      sourceRef.current?.close();
      sourceRef.current = openMessageStream(accepted.streamUrl, {
        onDelta: ({ messageId, delta }) => {
          setMessages((prev) =>
            prev.map((m) => (m.id === messageId ? { ...m, content: (m.content || '') + delta } : m))
          );
        },
        onCompleted: () => {
          sourceRef.current?.close();
          sendingRef.current = false;
          setSending(false);
          refreshMessages();
        },
        onFailed: ({ messageId }) => {
          sourceRef.current?.close();
          sendingRef.current = false;
          setSending(false);
          setMessages((prev) => prev.map((m) => (m.id === messageId ? { ...m, status: 'FAILED' } : m)));
        },
        onError: () => {
          sourceRef.current?.close();
          sendingRef.current = false;
          setSending(false);
        },
      });
    } catch (err) {
      sendingRef.current = false;
      setSending(false);
      window.alert(err.message || '메시지를 보내지 못했어요.');
    }
  }

  async function openResourceModal(safetyNotice) {
    const category = safetyNotice.resourceQuery?.category
      || (safetyNotice.type === 'CRISIS_SUPPORT' ? 'CRISIS_SUPPORT' : 'MENTAL_HEALTH_COUNSELING');
    const region = safetyNotice.resourceQuery?.region || 'KR';
    setResourceModal({ title: safetyNotice.title || '상담 지원 안내', category, region, loading: true, items: [] });
    try {
      const items = await fetchSupportResources({ region, category });
      setResourceModal((prev) => (prev ? { ...prev, loading: false, items } : prev));
    } catch {
      setResourceModal((prev) => (prev ? { ...prev, loading: false, items: [] } : prev));
    }
  }

  return (
    <div className="chat-main">
      <div className="chat-header">
        <div>
          <h2>{room ? `AI 상담 (feat: ${room.relationship.name}님)` : 'AI 상담'}</h2>
          <p>{room ? `${room.relationship.name}님과의 대화 데이터를 바탕으로 관계를 함께 살펴봐요` : ''}</p>
        </div>
      </div>

      <div className="chat-scroll" ref={scrollRef}>
        <div className="chat-col">
          {loading && <p style={{ fontSize: 12.5, color: 'var(--text-muted)', textAlign: 'center' }}>대화를 불러오는 중이에요</p>}
          {!loading &&
            messages.map((m) => (
              <MessageBlock
                key={m.id}
                message={m}
                onOpenResources={() => openResourceModal(m.safetyNotice)}
              />
            ))}
        </div>
      </div>

      <div className="chat-input-area">
        <div className="chip-suggest-row">
          {SUGGESTED_QUESTIONS.map((q) => (
            <button key={q} className="suggest-chip" disabled={sending} onClick={() => handleSend(q)}>{q}</button>
          ))}
        </div>
        <div className="chat-input-row">
          <input
            placeholder="궁금한 점을 편하게 물어보세요"
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={(e) => {
              if (e.key !== 'Enter' || e.isComposing) return;
              e.preventDefault();
              handleSend(input);
            }}
          />
          <button className="send-btn" aria-label="전송" disabled={sending || !input.trim()} onClick={() => handleSend(input)}>
            <SendIcon />
          </button>
        </div>
      </div>
      {resourceModal && (
        <SupportResourceModal resource={resourceModal} onClose={() => setResourceModal(null)} />
      )}
    </div>
  );
}

function MessageBlock({ message, onOpenResources }) {
  const isUser = message.role === 'USER';
  return (
    <>
      <div className={`bubble-row ${isUser ? 'user' : 'ai'}`}>
        {!isUser && (
          <div className="bubble-avatar">
            <MiniAstronaut />
          </div>
        )}
        <div className="bubble">
          {message.status === 'GENERATING' && !message.content ? '생각을 정리하고 있어요...' : message.content}
          {message.status === 'FAILED' && ' (답변을 생성하지 못했어요)'}
        </div>
      </div>
      {message.safetyNotice && (
        <div className="risk-card">
          <div className="risk-card-top">
            <WarnIcon />
            <span className="risk-card-title">{message.safetyNotice.title || '변화 감지'}</span>
          </div>
          <div className="risk-card-text">{message.safetyNotice.message}</div>
          <button className="btn btn-ghost" style={{ fontSize: 12, padding: '8px 14px' }} onClick={onOpenResources}>
            상담센터·지원기관 보기
          </button>
        </div>
      )}
    </>
  );
}

function SupportResourceModal({ resource, onClose }) {
  return (
    <div className="support-modal-backdrop" role="presentation" onMouseDown={onClose}>
      <section
        className="support-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="support-modal-title"
        onMouseDown={(e) => e.stopPropagation()}
      >
        <div className="support-modal-head">
          <div>
            <div className="support-modal-kicker">도움이 필요할 때</div>
            <h3 id="support-modal-title">{resource.title}</h3>
          </div>
          <button className="support-modal-close" aria-label="닫기" onClick={onClose}>×</button>
        </div>
        <p className="support-modal-description">혼자 감당하지 않아도 괜찮아요. 아래 기관에 바로 문의할 수 있어요.</p>
        {resource.loading && <p className="support-modal-empty">상담센터 정보를 불러오는 중이에요...</p>}
        {!resource.loading && resource.items.length === 0 && (
          <p className="support-modal-empty">현재 등록된 상담센터 정보가 없어요.</p>
        )}
        {!resource.loading && resource.items.length > 0 && (
          <div className="support-resource-list">
            {resource.items.map((item) => (
              <article className="support-resource-item" key={item.id}>
                <div className="support-resource-name">{item.name}</div>
                <div className="support-resource-description">{item.description}</div>
                <div className="support-resource-meta">
                  {item.phone && <a href={`tel:${item.phone}`}>전화 {item.phone}</a>}
                  {item.hours && <span>운영시간 {item.hours}</span>}
                </div>
                {item.url && <a className="support-resource-link" href={item.url} target="_blank" rel="noreferrer">공식 홈페이지 열기</a>}
              </article>
            ))}
          </div>
        )}
        <button className="btn btn-primary support-modal-footer" onClick={onClose}>확인</button>
      </section>
    </div>
  );
}

function EmptyChatMain({ roomsLoading }) {
  return (
    <div className="chat-main" style={{ alignItems: 'center', justifyContent: 'center', display: 'flex' }}>
      <p style={{ fontSize: 13, color: 'var(--text-muted)' }}>
        {roomsLoading ? '상담 기록을 불러오는 중이에요' : '인물별 리포트에서 상담을 시작해 보세요'}
      </p>
    </div>
  );
}

function formatTime(iso) {
  if (!iso) return '';
  const diffMs = Date.now() - new Date(iso).getTime();
  const hours = Math.floor(diffMs / 3_600_000);
  if (hours < 1) return '방금';
  if (hours < 24) return `${hours}시간 전`;
  const days = Math.floor(hours / 24);
  if (days === 1) return '어제';
  return `${days}일 전`;
}
