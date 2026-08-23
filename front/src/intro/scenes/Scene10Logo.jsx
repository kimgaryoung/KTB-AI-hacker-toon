import Astronaut from '../../components/Astronaut';
import Moon from '../../components/Moon';
import { LogoMark } from '../../components/Icons';
import { Rocket, Planet } from '../parts/SpaceProps';
import wordmark from '../../assets/images/wouldu-wordmark.png';

const OBJS = [Rocket, Astronaut, Planet, () => <Moon scale={0.5} />];

export default function Scene10Logo() {
  return (
    <div className="s10-wrap">
      <div className="s10-stage">
        <div className="s10-objs">
          {OBJS.map((Obj, i) => (
            <span key={i} className="s10-slot" style={{ '--i': i }}>
              <span className="s10-obj">
                <Obj size={54} />
              </span>
            </span>
          ))}
        </div>
        <div className="s10-brand">
          <span className="s10-mark">
            <LogoMark size={44} />
          </span>
          <img className="s10-wordmark" src={wordmark} alt="WouldU" />
        </div>
      </div>
      <p className="s10-tag">당신의 대화 속에 관계를 이해할 힌트가 있어요.</p>
    </div>
  );
}
