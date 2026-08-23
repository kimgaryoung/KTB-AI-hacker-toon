import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { fetchDashboard } from '../api/dashboard';
import Moon from '../components/Moon';
import Astronaut from '../components/Astronaut';
import NewPersonModal, { useNewPersonModal } from '../components/NewPersonModal';
import { ChevronDownIcon, CheckIcon, PlusIcon, SparkleIcon, WarnIcon } from '../components/Icons';
import { pointImageFor } from '../utils/pointImage';
import { RELATIONSHIP_TYPE_LABELS } from '../data/constants';
import ufoImage from '../assets/images/UFO.png';
import './Dashboard.css';

const SORT_OPTIONS = [
  { value: 'ABS_CHANGE_DESC', label: '변화가 큰 순' },
  { value: 'SCORE_DESC', label: '점수 높은 순' },
  { value: 'SCORE_ASC', label: '점수 낮은 순' },
  { value: 'UPDATED_DESC', label: '최근 업데이트순' },
];

const POINT_LEGEND = [
  { score: 100, label: '100 ~ 80' },
  { score: 70, label: '80 ~ 60' },
  { score: 50, label: '60 ~ 40' },
  { score: 30, label: '40 ~ 20' },
  { score: 10, label: '20 ~ 0' },
];

function relativeTime(iso) {
  if (!iso) return '분석 대기 중';
  const diffMs = Date.now() - new Date(iso).getTime();
  const days = Math.floor(diffMs / 86_400_000);
  if (days <= 0) return '오늘';
  if (days === 1) return '1일 전';
  return `${days}일 전`;
}

function Sparkline({ id, data, width = 64, height = 24, color }) {
  if (!data || data.length < 2) return null;
  const min = Math.min(...data);
  const max = Math.max(...data);
  const pad = 3;
  const points = data.map((v, i) => {
    const x = pad + (i * (width - 2 * pad)) / (data.length - 1);
    const y = height - pad - (max === min ? 0.5 : (v - min) / (max - min)) * (height - 2 * pad);
    return [x, y];
  });
  const line = points.map((p, i) => `${i ? 'L' : 'M'}${p[0].toFixed(1)},${p[1].toFixed(1)}`).join(' ');
  const first = points[0];
  const last = points[points.length - 1];
  const area = `${line} L${last[0].toFixed(1)},${height} L${first[0].toFixed(1)},${height} Z`;
  const gradientId = `spark-${id}`;
  return (
    <svg viewBox={`0 0 ${width} ${height}`} preserveAspectRatio="none">
      <defs>
        <linearGradient id={gradientId} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor={color} stopOpacity="0.35" />
          <stop offset="1" stopColor={color} stopOpacity="0" />
        </linearGradient>
      </defs>
      <path d={area} fill={`url(#${gradientId})`} />
      <path d={line} fill="none" stroke={color} strokeWidth="1.6" strokeLinejoin="round" strokeLinecap="round" />
      <circle cx={last[0].toFixed(1)} cy={last[1].toFixed(1)} r="2.4" fill={color} />
    </svg>
  );
}

export default function DashboardPage() {
  const navigate = useNavigate();
  const { open, openModal, closeModal } = useNewPersonModal();
  const [sort, setSort] = useState('ABS_CHANGE_DESC');
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(true);
  const [isSortMenuOpen, setIsSortMenuOpen] = useState(false);
  const sortMenuRef = useRef(null);
  const ufoRef = useRef(null);

  const load = useCallback(async (nextSort) => {
    setLoading(true);
    setError(null);
    try {
      const view = await fetchDashboard({ sort: nextSort });
      setData(view);
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load(sort);
  }, [load, sort]);

  useEffect(() => {
    function closeSortMenu(event) {
      if (!sortMenuRef.current?.contains(event.target)) setIsSortMenuOpen(false);
    }

    function closeOnEscape(event) {
      if (event.key === 'Escape') setIsSortMenuOpen(false);
    }

    document.addEventListener('mousedown', closeSortMenu);
    document.addEventListener('keydown', closeOnEscape);
    return () => {
      document.removeEventListener('mousedown', closeSortMenu);
      document.removeEventListener('keydown', closeOnEscape);
    };
  }, []);

  useEffect(() => {
    const ufo = ufoRef.current;
    const area = ufo?.parentElement;
    if (!ufo || !area) return undefined;

    let timer;
    let firstMoveFrame;
    let cancelled = false;
    const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

    function moveUfo() {
      if (cancelled) return;
      const width = Math.max(area.clientWidth, 320);
      const height = Math.max(area.clientHeight, 520);
      const x = -24 + Math.random() * (width + 48);
      const y = 80 + Math.random() * Math.max(140, height - 180);
      const scale = 0.34 + Math.random() * 0.71;
      const rotation = -18 + Math.random() * 36;
      const duration = 7000 + Math.random() * 11000;

      ufo.style.transition = reducedMotion
        ? 'none'
        : `transform ${duration.toFixed(0)}ms cubic-bezier(0.32, 0.04, 0.22, 1)`;
      ufo.style.transform = `translate3d(${x.toFixed(0)}px, ${y.toFixed(0)}px, 0) rotate(${rotation.toFixed(1)}deg) scale(${scale.toFixed(2)})`;

      if (!reducedMotion) {
        timer = window.setTimeout(moveUfo, duration + 300 + Math.random() * 1800);
      }
    }

    firstMoveFrame = window.requestAnimationFrame(moveUfo);
    return () => {
      cancelled = true;
      window.cancelAnimationFrame(firstMoveFrame);
      window.clearTimeout(timer);
    };
  }, []);

  const isEmpty = !loading && !error && data && data.relationships.length === 0;

  return (
    <section className="view">
      <img ref={ufoRef} className="dashboard-ufo" src={ufoImage} alt="" aria-hidden="true" />
      <div className="page-head">
        <div className="page-head-copy">
          <h1 className="page-title">이번 주 나의 관계 온도</h1>
          <p className="page-tagline">
            <SparkleIcon className="spark" style={{ width: 14, height: 14 }} /> 끝없는 관계의 우주 속, 당신에게
          </p>
        </div>
        <div className="hero-deco" aria-hidden="true">
          <div className="moon-slot">
            <Moon />
          </div>
          <div className="astro-slot">
            <Astronaut size={44} />
          </div>
        </div>
      </div>

      {loading && <LoadingState />}

      {!loading && error && <ErrorState error={error} onRetry={() => load(sort)} />}

      {!loading && !error && isEmpty && (
        <div className="empty-universe">
          <div className="empty-universe-deco" aria-hidden="true">
            <div className="moon-slot">
              <Moon scale={2.15} />
            </div>
            <div className="astro-slot">
              <Astronaut size={96} />
            </div>
          </div>
          <h2 className="empty-title">아직 당신의 우주엔 별이 없어요</h2>
          <p className="empty-sub">
            인물을 등록하면 그 사람과의 관계가 온도를 지닌 별 하나로
            <br />이 우주에 떠올라요. 첫 번째 별을 띄워볼까요?
          </p>
          <button className="btn btn-primary empty-cta" onClick={openModal}>
            <PlusIcon />
            첫 인물 등록하기
          </button>
        </div>
      )}

      {!loading && !error && data && !isEmpty && (
        <div className="dash-grid">
          <div>
            <div className="panel-title-row">
              <span className="panel-title">등록된 인물 ({data.relationships.length})</span>
              <div className="point-legend" aria-label="행성 이미지별 점수 구간">
                <div className="point-legend-items">
                  {POINT_LEGEND.map((item) => (
                    <span className="point-legend-item" key={item.label} title={`${item.label}점`}>
                      <img src={pointImageFor(item.score)} alt="" />
                      <span>{item.label}</span>
                    </span>
                  ))}
                </div>
              </div>
              <div className="panel-controls">
                <div className="sort-menu" ref={sortMenuRef}>
                  <button
                    type="button"
                    className="sort-trigger"
                    aria-haspopup="menu"
                    aria-expanded={isSortMenuOpen}
                    aria-controls="relationship-sort-menu"
                    onClick={() => setIsSortMenuOpen((open) => !open)}
                  >
                    <span>{SORT_OPTIONS.find((option) => option.value === sort)?.label}</span>
                    <ChevronDownIcon className="sort-trigger-icon" />
                  </button>
                  {isSortMenuOpen && (
                    <div id="relationship-sort-menu" className="sort-options" role="menu" aria-label="인물 정렬">
                      {SORT_OPTIONS.map((option) => {
                        const selected = option.value === sort;
                        return (
                          <button
                            key={option.value}
                            type="button"
                            className={`sort-option${selected ? ' selected' : ''}`}
                            role="menuitemradio"
                            aria-checked={selected}
                            onClick={() => {
                              setSort(option.value);
                              setIsSortMenuOpen(false);
                            }}
                          >
                            <span>{option.label}</span>
                            {selected && <CheckIcon aria-hidden="true" />}
                          </button>
                        );
                      })}
                    </div>
                  )}
                </div>
                <button className="btn btn-primary" onClick={openModal}>
                  <PlusIcon />
                  새 인물 등록
                </button>
              </div>
            </div>

            <div className="person-grid">
              {data.relationships.map((p) => {
                const up = (p.change ?? 0) >= 0;
                return (
                  <button
                    key={p.id}
                    className="person-card"
                    onClick={() => navigate(`/report/${p.id}`)}
                  >
                    <div className="person-top">
                      <img className="avatar point-avatar" src={pointImageFor(p.score)} alt="" />
                      <div>
                        <div className="person-name-row">
                          <div className="person-name">{p.name}</div>
                          <span className="chip">{RELATIONSHIP_TYPE_LABELS[p.relationshipType]}</span>
                        </div>
                      </div>
                    </div>
                    <div className="person-score-row">
                      <div className="score-big">{p.score}</div>
                      {p.change != null && (
                        <div className={`score-delta ${up ? 'up' : 'down'}`}>
                          {up ? '▲' : '▼'} {Math.abs(p.change)}
                        </div>
                      )}
                    </div>
                    <div className="person-updated">마지막 업데이트 · {relativeTime(p.lastAnalyzedAt)}</div>
                  </button>
                );
              })}
            </div>
          </div>

          <div className="side-col">
            <div className="side-card">
              <h3>
                <SparkleIcon style={{ color: 'var(--accent-lavender)' }} />
                이번 주 변화가 큰 관계
              </h3>
              {data.largestChanges.length === 0 && <EmptySideNote text="아직 변화를 비교할 이전 주 데이터가 없어요" />}
              {data.largestChanges.map((p) => {
                const up = (p.change ?? 0) >= 0;
                return (
                  <div className="trend-row" key={p.relationshipId}>
                    <div className="trend-info">
                      <img className="avatar point-avatar small-point-avatar" src={pointImageFor(p.score)} alt="" />
                      <div>
                        <div className="trend-name">{p.name}</div>
                        <div className={`score-delta ${up ? 'up' : 'down'}`} style={{ fontSize: 11.5 }}>
                          {up ? '▲' : '▼'} {Math.abs(p.change ?? 0)}
                        </div>
                      </div>
                    </div>
                    <div className="trend-spark">
                      <Sparkline
                        id={p.relationshipId}
                        data={p.sparkline}
                        width={64}
                        height={24}
                        color={up ? '#7fd9b6' : '#e2896f'}
                      />
                    </div>
                  </div>
                );
              })}
            </div>

            <div className="side-card warn">
              <h3>
                <WarnIcon style={{ color: 'var(--accent-amber)' }} />
                주의가 필요한 관계
              </h3>
              {data.needsAttention.length === 0 && <EmptySideNote text="지금은 주의가 필요한 관계가 없어요" />}
              {data.needsAttention.map((p) => (
                <div className="warn-row" key={p.relationshipId}>
                  <div className="trend-info">
                    <img className="avatar point-avatar small-point-avatar" src={pointImageFor(p.score)} alt="" />
                    <div>
                      <div className="trend-name">{p.name}</div>
                      <div style={{ fontSize: 11, color: 'var(--text-muted)' }}>{p.reasonLabel}</div>
                    </div>
                  </div>
                  <div className="warn-score">{p.score}</div>
                </div>
              ))}
            </div>

            <div className="side-card avg-card">
              <div className="avg-label">전체 관계 평균 온도</div>
              <div className="avg-number">{data.summary.averageScore ?? '–'}</div>
              <div className="avg-note">
                {data.summary.averageChange == null
                  ? '아직 비교할 지난주 데이터가 없어요'
                  : (
                    <>
                      지난주보다{' '}
                      <span style={{ color: data.summary.averageChange >= 0 ? 'var(--accent-mint)' : 'var(--accent-coral)', fontWeight: 700 }}>
                        {data.summary.averageChange >= 0 ? '▲' : '▼'} {Math.abs(data.summary.averageChange)}
                      </span>{' '}
                      {data.summary.averageChange >= 0 ? '상승했어요' : '하락했어요'}
                    </>
                  )}
              </div>
            </div>
          </div>
        </div>
      )}

      <NewPersonModal
        open={open}
        onClose={closeModal}
        mode="create"
        onSuccess={(relationship) => {
          load(sort);
          navigate(`/report/${relationship.id}`);
        }}
      />
    </section>
  );
}

function LoadingState() {
  return (
    <div className="empty-universe">
      <div className="empty-universe-deco" aria-hidden="true">
        <div className="astro-slot" style={{ position: 'static', margin: '0 auto' }}>
          <Astronaut size={72} />
        </div>
      </div>
      <p className="empty-sub">관계 온도를 불러오는 중이에요</p>
    </div>
  );
}

function ErrorState({ error, onRetry }) {
  return (
    <div className="empty-universe">
      <h2 className="empty-title">잠시 연결이 어려워요</h2>
      <p className="empty-sub">{error?.message || '대시보드를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.'}</p>
      <button className="btn btn-ghost empty-cta" onClick={onRetry}>다시 시도</button>
    </div>
  );
}

function EmptySideNote({ text }) {
  return <p style={{ fontSize: 12, color: 'var(--text-muted)', padding: '4px 0' }}>{text}</p>;
}
