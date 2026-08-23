import Scene02Dashboard from '../intro/scenes/Scene02Dashboard';
import Scene03Form from '../intro/scenes/Scene03Form';
import Scene04Upload from '../intro/scenes/Scene04Upload';
import Scene05Checkin from '../intro/scenes/Scene05Checkin';
import Scene06Analysis from '../intro/scenes/Scene06Analysis';
import Scene07Report from '../intro/scenes/Scene07Report';
import Scene08Chat from '../intro/scenes/Scene08Chat';
import GuideKakao from './GuideKakao';

// 케이스 B — 배우자와의 관계 고민 (직장 시간대의 날카로운 대화 패턴)
const NAME = '아내';

const BEFORE_MESSAGES = [
  { who: 'me', at: 300, time: '오후 2:12', text: '어제 관리비 냈어? 아직도 안 냈으면 오늘은 꼭 좀 처리해줘.' },
  { who: 'other', at: 1800, time: '오후 2:20', text: '지금 회의 들어가야 해. 나중에 얘기하면 안 돼?' },
  { who: 'me', at: 3000, time: '오후 2:21', text: '매번 나중에래. 지난번에도 그렇게 말하고 잊었잖아.' },
  { who: 'other', at: 4700, time: '오후 2:28', text: '지금 바쁘다고 했잖아. 왜 꼭 이 시간에 따지듯이 물어봐?' },
];

const KAKAO_BEFORE = (
  <GuideKakao
    title="아내 ♥"
    senderName="아내"
    avatarChar="아"
    date="8월 20일 목요일"
    messages={BEFORE_MESSAGES}
  />
);

// 재생 전 포스터: 문제 상황 대화가 전부 보이는 정지 화면
export const PARTNER_GUIDE_POSTER = (
  <GuideKakao
    title="아내 ♥"
    senderName="아내"
    avatarChar="아"
    date="8월 20일 목요일"
    messages={BEFORE_MESSAGES}
    staticAll
  />
);

const KAKAO_AFTER = (
  <GuideKakao
    title="아내 ♥"
    senderName="아내"
    avatarChar="아"
    date="8월 20일 목요일"
    messages={[
      {
        who: 'me',
        at: 300,
        speed: 24,
        time: '오후 6:40',
        text: '아까 내가 회사에서 너무 따지듯이 말한 것 같아. 바쁜데 괜히 기분 상하게 했으면 미안해.',
      },
      {
        who: 'other',
        at: 3000,
        time: '오후 6:47',
        text: '나도 좀 날카롭게 말했던 것 같아. 회사에 있을 때 이야기하니까 서로 더 예민해지는 것 같네.',
      },
      {
        who: 'me',
        at: 4500,
        speed: 26,
        time: '오후 6:49',
        text: '그러게. 다음부터 중요한 얘기는 퇴근하고 편할 때 하자. 내가 먼저 좀 부드럽게 말해볼게 ㅎㅎ',
      },
      { who: 'other', at: 7100, time: '오후 6:52', text: '좋아 ㅎㅎ 나도 그렇게 할게. 이따 집에서 보자 ❤️' },
    ]}
    heart={{ at: 8200, time: '오후 6:53' }}
  />
);

const CHAT_ANSWER = (
  <>
    바쁜 하루 속에서 자꾸 부딪히는 것 같아 마음이 무거우셨겠어요.
    <br />
    두 분은 특히 <span className="intro-accent">직장에 있는 시간대</span>에 대화를 나눌 때 말투가
    평소보다 날카로워지는 패턴이 보여요. 바쁜 상황에서{' '}
    <span className="intro-accent">따지듯 묻는 표현</span>이 반복되면 의도와 달리 상대방의 반감을
    키울 수 있어요.
    <br />
    <span className="intro-hi">
      중요한 이야기는 퇴근 후나 주말로 옮기고, "나는 이렇게 느꼈어"처럼 감정을 먼저 전달해보세요.
    </span>
  </>
);

export const PARTNER_GUIDE_SCENES = [
  { id: 'kakao-before', dur: 6400, label: '이런 대화, 익숙하신가요?', node: KAKAO_BEFORE },
  { id: 'dashboard', dur: 1600, label: '대시보드', node: <Scene02Dashboard /> },
  { id: 'form', dur: 2200, label: '1. 인물 등록', node: <Scene03Form name={NAME} pickType="ROMANTIC_PARTNER" /> },
  { id: 'upload', dur: 2200, label: '2. 대화 업로드', node: <Scene04Upload fileName="아내_대화.txt" /> },
  { id: 'checkin', dur: 2200, label: '3. 체크인', node: <Scene05Checkin /> },
  { id: 'analysis', dur: 2400, label: '4. AI 분석', node: <Scene06Analysis personName={NAME} /> },
  {
    id: 'report',
    dur: 3400,
    label: '5. 리포트',
    node: (
      <Scene07Report
        name={NAME}
        typeLabel="배우자"
        score={61}
        delta="▼ 3"
        deltaDir="down"
        prqcValues={[55, 78, 60, 72, 48, 70]}
        evidences={[
          { tag: '말투', text: '직장 시간대(오전 10시~오후 5시) 대화에서 날카로운 표현의 비율이 평소보다 높게 나타났어요.' },
          { tag: '헌신', text: '저녁·주말 대화에서는 서로를 챙기는 표현이 꾸준히 이어지고 있어요.' },
        ]}
      />
    ),
  },
  {
    id: 'chat',
    dur: 6200,
    label: '6. AI 상담',
    node: (
      <Scene08Chat
        name={NAME}
        question="요즘 아내랑 자꾸 사소한 걸로 부딪혀.. 대화 분석 결과는 어때?"
        answer={CHAT_ANSWER}
        suggested={['어떤 시간대에 대화가 제일 부드러워?', '내 말투에서 고칠 점이 있을까?']}
      />
    ),
  },
  { id: 'kakao-after', dur: 9600, label: '달라진 대화', node: KAKAO_AFTER },
];
