import Moon from '../../components/Moon';
import Astronaut from '../../components/Astronaut';
import { PlusIcon, SparkleIcon } from '../../components/Icons';
import ufoImage from '../../assets/images/UFO.png';
import '../../pages/Dashboard.css';

// 실제 DashboardPage의 빈 우주 상태와 동일한 마크업.
export default function DashboardDemo() {
  return (
    <section className="view">
      <img className="dashboard-ufo" src={ufoImage} alt="" aria-hidden="true" />
      <div className="page-head">
        <div className="page-head-copy">
          <h1 className="page-title">이번 주 나의 관계 온도</h1>
          <p className="page-tagline">
            <SparkleIcon className="spark" style={{ width: 14, height: 14 }} /> 끝없는 관계의 우주
            속, 당신에게
          </p>
        </div>
        <button className="btn btn-primary" type="button" tabIndex={-1}>
          <PlusIcon />새 인물 등록
        </button>
        <div className="hero-deco" aria-hidden="true">
          <div className="moon-slot">
            <Moon />
          </div>
          <div className="astro-slot">
            <Astronaut size={44} />
          </div>
        </div>
      </div>
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
        <button className="btn btn-primary empty-cta" type="button" tabIndex={-1}>
          <PlusIcon />첫 인물 등록하기
        </button>
      </div>
    </section>
  );
}
