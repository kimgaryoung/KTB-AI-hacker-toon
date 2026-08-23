import DemoAppShell from '../parts/DemoAppShell';
import DemoCursor from '../parts/DemoCursor';
import Gauge from '../../components/charts/Gauge';
import RadarChart from '../../components/charts/RadarChart';
import { SearchIcon, PlusIcon, QuoteIcon, ChatIcon, PixelInfoIcon } from '../../components/Icons';
import { PRQC_ORDER, PRQC_LABELS } from '../../data/constants';
import { pointImageFor } from '../../utils/pointImage';
import '../../pages/Report.css';

const DEFAULT_EVIDENCES = [
  { tag: '애정', text: "7월 20일 · 26일 · 28일에도 '사랑해', '좋아해' 같은 애정 표현이 이어졌어요." },
  { tag: '친밀감', text: '상대방이 먼저 연락한 비율이 약 70%였고, 한강 이야기를 자주 나누고 있어요.' },
];

export default function Scene07Report({
  name = '홍길동',
  typeLabel = '연인',
  score = 72,
  delta = '▲ 3',
  deltaDir = 'up',
  prqcValues = [78, 74, 80, 76, 70, 82],
  evidences = DEFAULT_EVIDENCES,
}) {
  return (
    <DemoAppShell active="report" cursor={<DemoCursor variant="report" />}>
      <section className="report-shell">
        <aside className="people-panel">
          <div className="search-box">
            <SearchIcon />
            <input placeholder="인물 검색" readOnly tabIndex={-1} />
          </div>
          <button className="mini-person active" type="button" tabIndex={-1}>
            <img className="mini-avatar point-avatar" src={pointImageFor(score)} alt="" />
            <div>
              <div className="mini-name">{name}</div>
              <div className="mini-score">{score}점 · {typeLabel}</div>
            </div>
          </button>
        </aside>
        <div className="report-main">
          <div className="report-head intro-rise" style={{ '--i': 0 }}>
            <div className="report-who">
              <img className="report-avatar point-avatar" src={pointImageFor(score)} alt="" />
              <div>
                <div className="report-name">{name}</div>
                <span className="chip">{typeLabel}</span>
              </div>
            </div>
            <button className="btn btn-ghost" type="button" tabIndex={-1}>
              <PlusIcon />
              대화 내역 추가
            </button>
          </div>
          <div className="report-grid">
            <div className="card overview-card intro-rise" style={{ '--i': 1 }}>
              <h3>종합 온도</h3>
              <div className="gauge-wrap">
                <Gauge score={score} size={216} />
                <div className="gauge-center">
                  <div className="gauge-score">{score}</div>
                  <div className="gauge-max">/ 100</div>
                  <div className={`gauge-delta score-delta ${deltaDir}`}>{delta}</div>
                </div>
              </div>
              <div className="prqc-mini" aria-label="PRQC 관계 품질 점수">
                <div className="prqc-mini-title">PRQC 관계 품질</div>
                {PRQC_ORDER.map((key, index) => (
                  <div className="prqc-mini-row" key={key}>
                    <span className="prqc-mini-label">{PRQC_LABELS[key]}</span>
                    <div className="prqc-mini-track" aria-hidden="true">
                      <span className="prqc-mini-fill" style={{ width: `${prqcValues[index]}%` }} />
                    </div>
                    <strong className="prqc-mini-score">{prqcValues[index]}</strong>
                  </div>
                ))}
              </div>
            </div>
            <div className="card prqc-card intro-rise" style={{ '--i': 2 }}>
              <div className="card-title-row">
                <h3>PRQC 관계 품질 6요소</h3>
                <div className="prqc-info">
                  <button type="button" className="pixel-info-btn" tabIndex={-1} aria-label="PRQC 설명 보기">
                    <PixelInfoIcon />
                  </button>
                </div>
              </div>
              <RadarChart values={prqcValues} labels={PRQC_ORDER.map((k) => PRQC_LABELS[k])} />
            </div>
          </div>
          <div className="evidence-row report-analysis">
            {evidences.map((ev, i) => (
              <div className="evidence-card intro-rise" style={{ '--i': 3 + i }} key={ev.tag}>
                <div className="evidence-top">
                  <QuoteIcon />
                  <span className="evidence-tag">{ev.tag}</span>
                </div>
                <div className="evidence-text">{ev.text}</div>
              </div>
            ))}
          </div>
          <div className="consult-cta intro-rise" style={{ '--i': 5 }}>
            <button className="btn btn-primary intro-consult-glow" type="button" tabIndex={-1}>
              <ChatIcon />
              AI와 상담하기
            </button>
          </div>
        </div>
      </section>
    </DemoAppShell>
  );
}
