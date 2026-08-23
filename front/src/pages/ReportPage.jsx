import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { listRelationships, getRelationship } from '../api/relationships';
import { fetchReport } from '../api/reports';
import { createConsultation } from '../api/consultations';
import { avatarGradientFor, initialsOf } from '../utils/avatar';
import { pointImageFor } from '../utils/pointImage';
import { PRQC_ORDER, PRQC_LABELS, RELATIONSHIP_TYPE_LABELS, RELATIONSHIP_STATUS_LABELS } from '../data/constants';
import Gauge from '../components/charts/Gauge';
import RadarChart from '../components/charts/RadarChart';
import TrendLineChart from '../components/charts/TrendLineChart';
import Astronaut from '../components/Astronaut';
import NewPersonModal, { useNewPersonModal } from '../components/NewPersonModal';
import { SearchIcon, PlusIcon, QuoteIcon, ChatIcon, PixelInfoIcon } from '../components/Icons';
import './Report.css';

export default function ReportPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { open, openModal, closeModal } = useNewPersonModal();

  const [query, setQuery] = useState('');
  const [people, setPeople] = useState([]);
  const [peopleLoading, setPeopleLoading] = useState(true);

  const loadPeople = useCallback(async (search) => {
    setPeopleLoading(true);
    try {
      const list = await listRelationships({ search: search || undefined });
      setPeople(list);
    } finally {
      setPeopleLoading(false);
    }
  }, []);

  useEffect(() => {
    const t = setTimeout(() => loadPeople(query), 250);
    return () => clearTimeout(t);
  }, [query, loadPeople]);

  const selected = useMemo(() => people.find((p) => p.id === id) ?? null, [people, id]);

  const [report, setReport] = useState(null);
  const [reportLoading, setReportLoading] = useState(false);
  const [reportError, setReportError] = useState(null);
  const [fallbackRelationship, setFallbackRelationship] = useState(null);
  const [consultLoading, setConsultLoading] = useState(false);

  useEffect(() => {
    if (!id) return;
    setReport(null);
    setReportError(null);
    setFallbackRelationship(null);
    setReportLoading(true);
    fetchReport(id)
      .then(setReport)
      .catch(async (err) => {
        setReportError(err);
        // Report doesn't exist yet (relationship still DRAFT/ANALYZING/FAILED).
        // Fetch the bare relationship so we can still show its name/status.
        try {
          setFallbackRelationship(await getRelationship(id));
        } catch {
          /* relationship itself is gone or not owned — reportError covers it */
        }
      })
      .finally(() => setReportLoading(false));
  }, [id]);

  async function handleStartConsultation() {
    if (!id) return;
    setConsultLoading(true);
    try {
      const consultation = await createConsultation(id);
      navigate(`/chat/${consultation.id}`);
    } catch (err) {
      window.alert(err.message || '상담을 시작하지 못했어요.');
    } finally {
      setConsultLoading(false);
    }
  }

  return (
    <section className="report-shell">
      <aside className="people-panel">
        <div className="search-box">
          <SearchIcon />
          <input placeholder="인물 검색" value={query} onChange={(e) => setQuery(e.target.value)} />
        </div>
        {!peopleLoading && people.length === 0 && (
          <p style={{ fontSize: 12, color: 'var(--text-muted)', padding: '8px 4px' }}>등록된 인물이 없어요</p>
        )}
        {people.map((p) => (
          <button
            key={p.id}
            className={`mini-person ${p.id === id ? 'active' : ''}`}
            onClick={() => navigate(`/report/${p.id}`)}
          >
            {pointImageFor(p.score) ? (
              <img className="mini-avatar point-avatar" src={pointImageFor(p.score)} alt="" />
            ) : (
              <div className="mini-avatar" style={{ background: avatarGradientFor(p.id) }}>
                {p.initial || initialsOf(p.name)}
              </div>
            )}
            <div>
              <div className="mini-name">{p.name}</div>
              <div className="mini-score">
                {p.status === 'ACTIVE' ? `${p.score}점 · ` : ''}
                {RELATIONSHIP_TYPE_LABELS[p.relationshipType]}
                {p.status !== 'ACTIVE' && ` · ${RELATIONSHIP_STATUS_LABELS[p.status] ?? p.status}`}
              </div>
            </div>
          </button>
        ))}
      </aside>

      <div className="report-main">
        {!id && <SelectPrompt />}

        {id && reportLoading && <CenteredNote text="리포트를 불러오는 중이에요" />}

        {id && !reportLoading && reportError && (
          <NoReportState
            relationship={selected || fallbackRelationship}
            onAddData={openModal}
          />
        )}

        {id && !reportLoading && !reportError && report && (
          <ReportBody
            report={report}
            onAddData={openModal}
            onConsult={handleStartConsultation}
            consultLoading={consultLoading}
          />
        )}
      </div>

      <NewPersonModal
        open={open}
        onClose={closeModal}
        mode="add-data"
        relationship={selected || fallbackRelationship || { id, name: '' }}
        onSuccess={() => {
          loadPeople(query);
          fetchReport(id).then(setReport).then(() => setReportError(null));
        }}
      />
    </section>
  );
}

function PrqcInfoTip() {
  const [open, setOpen] = useState(false);
  const wrapRef = useRef(null);

  useEffect(() => {
    if (!open) return undefined;
    function handleOutside(e) {
      if (wrapRef.current && !wrapRef.current.contains(e.target)) setOpen(false);
    }
    window.addEventListener('mousedown', handleOutside);
    return () => window.removeEventListener('mousedown', handleOutside);
  }, [open]);

  return (
    <div className="prqc-info" ref={wrapRef}>
      <button
        type="button"
        className={`pixel-info-btn${open ? ' is-open' : ''}`}
        onClick={() => setOpen((v) => !v)}
        aria-expanded={open}
        aria-label="PRQC 설명 보기"
      >
        <PixelInfoIcon />
      </button>
      {open && (
        <div className="pixel-tooltip" role="note">
          PRQC는 만족·헌신·친밀·신뢰·열정·사랑 6가지 요소로 관계의 질을 측정하는 지표예요 :{' '}
          <a
            className="pixel-tooltip-link"
            href="https://journals.sagepub.com/doi/10.1177/0146167200265007"
            target="_blank"
            rel="noreferrer"
          >
            링크 ↗
          </a>
        </div>
      )}
    </div>
  );
}

function ReportBody({ report, onAddData, onConsult, consultLoading }) {
  const up = (report.overall.change ?? 0) >= 0;
  const prqcValues = PRQC_ORDER.map((k) => report.prqc[k]);
  const hasTrend = report.trend.length >= 2;
  const [activePrqcIndex, setActivePrqcIndex] = useState(null);

  return (
    <>
      <div className="report-head">
        <div className="report-who">
          {pointImageFor(report.overall.score) ? (
            <img className="report-avatar point-avatar" src={pointImageFor(report.overall.score)} alt="" />
          ) : (
            <div className="report-avatar" style={{ background: avatarGradientFor(report.relationship.id) }}>
              {report.relationship.initial || initialsOf(report.relationship.name)}
            </div>
          )}
          <div>
            <div className="report-name">{report.relationship.name}</div>
            <span className="chip">{RELATIONSHIP_TYPE_LABELS[report.relationship.relationshipType]}</span>
          </div>
        </div>
        <button className="btn btn-ghost" onClick={onAddData}>
          <PlusIcon />
          대화 내역 추가
        </button>
      </div>

      <div className="report-grid">
        <div className="card overview-card">
          <h3>종합 온도</h3>
          <div className="gauge-wrap">
            <Gauge score={report.overall.score} size={216} />
            <div className="gauge-center">
              <AnimatedScore score={report.overall.score} />
              <div className="gauge-max">/ 100</div>
              {report.overall.change != null && (
                <div className={`gauge-delta score-delta ${up ? 'up' : 'down'}`}>
                  {up ? '▲' : '▼'} {Math.abs(report.overall.change)}
                </div>
              )}
            </div>
          </div>
          <div className={`prqc-mini${activePrqcIndex !== null ? ' has-active' : ''}`} aria-label="PRQC 관계 품질 점수">
            <div className="prqc-mini-title">PRQC 관계 품질</div>
            {PRQC_ORDER.map((key, index) => {
              const score = Math.max(0, Math.min(100, Math.round(report.prqc[key] ?? 0)));
              return (
                <div
                  className={`prqc-mini-row${activePrqcIndex === index ? ' is-active' : ''}`}
                  key={key}
                  tabIndex="0"
                  onMouseEnter={() => setActivePrqcIndex(index)}
                  onMouseLeave={() => setActivePrqcIndex(null)}
                  onFocus={() => setActivePrqcIndex(index)}
                  onBlur={() => setActivePrqcIndex(null)}
                >
                  <span className="prqc-mini-label">{PRQC_LABELS[key]}</span>
                  <div className="prqc-mini-track" aria-hidden="true">
                    <span className="prqc-mini-fill" style={{ width: `${score}%` }} />
                  </div>
                  <strong className="prqc-mini-score">{score}</strong>
                </div>
              );
            })}
          </div>
        </div>

        <div className="card prqc-card">
          <div className="card-title-row">
            <h3>PRQC 관계 품질 6요소</h3>
            <PrqcInfoTip />
          </div>
          <RadarChart
            values={prqcValues}
            labels={PRQC_ORDER.map((k) => PRQC_LABELS[k])}
            activeIndex={activePrqcIndex}
            onActiveChange={setActivePrqcIndex}
          />
          <div className="radar-legend">
            <div className="radar-legend-item">
              <span className="radar-legend-swatch" style={{ background: 'var(--accent-pink)' }} />
              현재 점수 (100점 만점)
            </div>
            <div className="radar-legend-item">
              <span className="radar-legend-swatch" style={{ background: 'var(--accent-amber)', borderRadius: 0 }} />
              위험 기준선 (60점)
            </div>
          </div>
        </div>
      </div>

      <div className="evidence-row report-analysis">
        {report.evidences.length > 0 ? (
          report.evidences.map((ev) => {
            const evidencePrqcIndex = PRQC_ORDER.indexOf(ev.component);
            const isActive = evidencePrqcIndex >= 0 && activePrqcIndex === evidencePrqcIndex;
            return (
            <div
              className={`evidence-card${isActive ? ' is-active' : ''}${activePrqcIndex !== null && evidencePrqcIndex !== activePrqcIndex ? ' is-dimmed' : ''}`}
              key={ev.id}
              tabIndex="0"
              onMouseEnter={() => evidencePrqcIndex >= 0 && setActivePrqcIndex(evidencePrqcIndex)}
              onMouseLeave={() => setActivePrqcIndex(null)}
              onFocus={() => evidencePrqcIndex >= 0 && setActivePrqcIndex(evidencePrqcIndex)}
              onBlur={() => setActivePrqcIndex(null)}
            >
              <div className="evidence-top">
                <QuoteIcon />
                <span className="evidence-tag">{PRQC_LABELS[ev.component] ?? ev.component}</span>
              </div>
              <div className="evidence-text">{ev.summary}</div>
            </div>
            );
          })
        ) : (
          <div className="evidence-card positive">
            <div className="evidence-top">
              <QuoteIcon />
            </div>
            <div className="evidence-text">뚜렷한 위험 신호는 관찰되지 않았어요. 지금처럼 편안한 대화가 이어지고 있어요.</div>
          </div>
        )}
      </div>

      {report.selfReportComparison?.trim() && (
        <section className="card self-report-comparison" aria-labelledby="self-report-comparison-title">
          <div className="self-report-comparison-heading">
            <span className="self-report-comparison-kicker">체크인과 대화 분석</span>
            <h3 id="self-report-comparison-title">내가 느낀 관계와 대화에서 보인 모습</h3>
          </div>
          <p>
            {report.selfReportComparison}
          </p>
          <span className="self-report-comparison-note">
            이 내용은 자기보고와 대화에서 관찰된 신호를 비교한 참고 설명이에요.
          </span>
        </section>
      )}

      <div className="card trend-card">
        <h3>시간에 따른 변화</h3>
        <div className="trend-chart-wrap">
          {hasTrend ? (
            <TrendLineChart data={report.trend.map((t) => t.score)} labels={report.trend.map((t) => t.label)} />
          ) : (
            <p style={{ fontSize: 12.5, color: 'var(--text-muted)' }}>
              다음 분석부터 추이 그래프를 볼 수 있어요.
            </p>
          )}
        </div>
      </div>

      <p style={{ fontSize: 11.5, color: 'var(--text-muted)', marginBottom: 20, lineHeight: 1.6 }}>
        {report.disclaimer}
      </p>

      <div className="consult-cta">
        <button className="btn btn-primary" onClick={onConsult} disabled={consultLoading}>
          <ChatIcon />
          {consultLoading ? '연결하는 중...' : 'AI와 상담하기'}
        </button>
      </div>
    </>
  );
}

function AnimatedScore({ score }) {
  const target = Math.round(Number(score) || 0);
  const [displayScore, setDisplayScore] = useState(0);

  useEffect(() => {
    const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (reducedMotion) {
      setDisplayScore(target);
      return undefined;
    }

    const duration = 950;
    let frame;
    let start;

    function tick(now) {
      const progress = Math.min((now - start) / duration, 1);
      const eased = 1 - (1 - progress) ** 3;
      setDisplayScore(Math.round(target * eased));
      if (progress < 1) frame = window.requestAnimationFrame(tick);
    }

    frame = window.requestAnimationFrame(() => {
      setDisplayScore(0);
      start = performance.now();
      frame = window.requestAnimationFrame(tick);
    });
    return () => window.cancelAnimationFrame(frame);
  }, [target]);

  return <div className="gauge-score" aria-label={`종합 온도 ${target}점`}>{displayScore}</div>;
}

function NoReportState({ relationship, onAddData }) {
  return (
    <div className="empty-universe" style={{ padding: '80px 20px' }}>
      <div style={{ margin: '0 auto 18px' }}>
        <Astronaut size={80} />
      </div>
      <h2 className="empty-title">
        {relationship?.name ? `${relationship.name}님의 리포트가 아직 없어요` : '리포트가 아직 없어요'}
      </h2>
      <p className="empty-sub">
        대화 데이터를 올리고 짧은 체크인을 마치면
        <br />PRQC 기반 관계 온도를 확인할 수 있어요.
      </p>
      <button className="btn btn-primary empty-cta" onClick={onAddData}>
        <PlusIcon />
        대화 데이터 올리기
      </button>
    </div>
  );
}

function SelectPrompt() {
  return (
    <div className="empty-universe" style={{ padding: '100px 20px' }}>
      <div style={{ margin: '0 auto 18px' }}>
        <Astronaut size={80} />
      </div>
      <h2 className="empty-title">왼쪽에서 인물을 선택해 주세요</h2>
      <p className="empty-sub">등록된 인물을 고르면 관계 온도 리포트를 볼 수 있어요.</p>
    </div>
  );
}

function CenteredNote({ text }) {
  return (
    <div className="empty-universe" style={{ padding: '100px 20px' }}>
      <p className="empty-sub">{text}</p>
    </div>
  );
}
