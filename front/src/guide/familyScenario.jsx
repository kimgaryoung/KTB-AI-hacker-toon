import Scene02Dashboard from '../intro/scenes/Scene02Dashboard';
import Scene03Form from '../intro/scenes/Scene03Form';
import Scene04Upload from '../intro/scenes/Scene04Upload';
import Scene05Checkin from '../intro/scenes/Scene05Checkin';
import Scene06Analysis from '../intro/scenes/Scene06Analysis';
import Scene07Report from '../intro/scenes/Scene07Report';
import Scene08Chat from '../intro/scenes/Scene08Chat';
import GuideKakao from './GuideKakao';

// 케이스 A — 30~50대 · 사춘기 자녀와의 관계 고민
const NAME = '아들';

// 문제 상황 대화: 아들은 짧게, 피곤함이 드러나게.
const BEFORE_MESSAGES = [
  { who: 'other', time: '오후 10:00', text: '아 몰라, 나 지금 너무 피곤해.' },
  {
    who: 'me',
    time: '오후 10:02',
    text: '아빠는 그냥 요즘 학교 생활 어땠는지 궁금해서 물어본 건데, 왜 그렇게 화부터 내니?',
  },
  { who: 'other', time: '오후 10:04', text: '하루 종일 학원 갔다 오니까 진짜 지쳤어.. 잔소리 들을 힘도 없어.' },
  { who: 'other', time: '오후 10:12', text: '오늘은 그냥 잘게. 나중에 얘기해.' },
];

const KAKAO_BEFORE = (
  <GuideKakao
    title="아들"
    senderName="아들"
    avatarChar="아"
    date="8월 20일 목요일"
    messages={[
      { ...BEFORE_MESSAGES[0], at: 400 },
      { ...BEFORE_MESSAGES[1], at: 2000 },
      { ...BEFORE_MESSAGES[2], at: 4200 },
      { ...BEFORE_MESSAGES[3], at: 6200 },
    ]}
  />
);

// 재생 전 포스터: 문제 상황 대화가 전부 보이는 정지 화면
export const FAMILY_GUIDE_POSTER = (
  <GuideKakao
    title="아들"
    senderName="아들"
    avatarChar="아"
    date="8월 20일 목요일"
    messages={BEFORE_MESSAGES}
    staticAll
  />
);

const KAKAO_AFTER = (
  <GuideKakao
    title="아들"
    senderName="아들"
    avatarChar="아"
    historyDate="8월 20일 목요일"
    history={BEFORE_MESSAGES}
    date="8월 22일 토요일"
    messages={[
      {
        who: 'me',
        at: 500,
        speed: 26,
        time: '오전 11:02',
        text: '요즘 많이 피곤해 보이더라. 공부하느라 힘들지? 이번 주말엔 푹 자고 맛있는 거 먹자. 비타민도 하나 사놨어 ㅎㅎ',
      },
      {
        who: 'other',
        at: 4300,
        time: '오전 11:10',
        text: 'ㅜㅜ고마워 아빠. 요즘 좀 정신없고 피곤하긴 했어. 그래서 괜히 짜증냈던 것 같아. 주말에는 같이 밥 먹자.',
      },
      { who: 'me', at: 6200, speed: 30, time: '오전 11:12', text: '그래, 공부 얘기는 안 할 테니까 걱정 마.. 푹 쉬자.' },
      { who: 'other', at: 8400, time: '오전 11:13', text: '네 ㅠㅠ 감사해요' },
    ]}
    heart={{ at: 9500, time: '오전 11:14' }}
  />
);

const CHAT_ANSWER = (
  <>
    아이가 자꾸 멀어지는 것 같아 많이 속상하셨겠어요.
    <br />
    최근 한 달 동안 자녀가 <span className="intro-accent">피로와 관련된 표현</span>을 한 날이{' '}
    <span className="intro-accent">19일</span>로 자주 나타났어요. 특히 학업을 마친 늦은 시간에는 이미
    에너지가 떨어져 있어, 연락에도 평소보다 짧거나 예민하게 반응하는 모습이 보여요. 관계의 문제라기보다는{' '}
    <span className="intro-accent">학업 부담과 피로</span>의 영향일 가능성이 커요.
    <br />
    <span className="intro-hi">비타민이나 간식을 챙겨주고, 충분히 쉰 주말 오후에 편안하게 대화를 시도해보세요.</span>
  </>
);

export const FAMILY_GUIDE_SCENES = [
  { id: 'kakao-before', dur: 8800, label: '이런 대화, 익숙하신가요?', node: KAKAO_BEFORE },
  { id: 'dashboard', dur: 1600, label: '대시보드', node: <Scene02Dashboard /> },
  { id: 'form', dur: 2200, label: '1. 인물 등록', node: <Scene03Form name={NAME} pickType="FAMILY" /> },
  { id: 'upload', dur: 2200, label: '2. 대화 업로드', node: <Scene04Upload fileName="아들_대화.txt" /> },
  {
    id: 'checkin',
    dur: 2200,
    label: '3. 체크인',
    node: <Scene05Checkin q1Init={5} q2Init={5} q1Steps={[[600, 4]]} q2Steps={[[1200, 4]]} />,
  },
  { id: 'analysis', dur: 2400, label: '4. AI 분석', node: <Scene06Analysis personName={NAME} /> },
  {
    id: 'report',
    dur: 3400,
    label: '5. 리포트',
    node: (
      <Scene07Report
        name={NAME}
        typeLabel="가족"
        score={58}
        delta="▼ 4"
        deltaDir="down"
        prqcValues={[52, 68, 45, 66, 38, 74]}
        evidences={[
          { tag: '피로', text: '최근 한 달 중 19일에서 피로 관련 표현이 나타났어요. 특히 학업을 마친 늦은 밤에 집중돼요.' },
          { tag: '친밀감', text: '답장이 짧고 예민해졌지만, 대화를 아예 끊지는 않고 이어가고 있어요.' },
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
        question="요즘 아이가 자꾸 짜증만 내고 방에만 있어.. 대화 분석 결과는 어때?"
        answer={CHAT_ANSWER}
        suggested={['어떻게 말을 걸면 좋을까?', '아이가 요즘 제일 많이 하는 얘기는 뭐야?']}
      />
    ),
  },
  { id: 'kakao-after', dur: 11000, label: '달라진 대화', node: KAKAO_AFTER },
];
