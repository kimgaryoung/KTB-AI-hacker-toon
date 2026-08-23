export const RELATIONSHIP_TYPES = [
  { value: 'ROMANTIC_PARTNER', label: '연인' },
  { value: 'FRIEND', label: '친구' },
  { value: 'FAMILY', label: '가족' },
  { value: 'COWORKER', label: '직장동료' },
  { value: 'OTHER', label: '기타' },
];

export const RELATIONSHIP_TYPE_LABELS = Object.fromEntries(
  RELATIONSHIP_TYPES.map((t) => [t.value, t.label])
);

export const PRQC_LABELS = {
  satisfaction: '만족감',
  commitment: '헌신',
  intimacy: '친밀감',
  trust: '신뢰',
  passion: '열정',
  love: '애정',
};

export const PRQC_ORDER = ['satisfaction', 'commitment', 'intimacy', 'trust', 'passion', 'love'];

export const ANALYSIS_STAGE_LABELS = {
  LOADING_CONVERSATION: '대화 파일 불러오는 중',
  ANALYZING_MESSAGE_PATTERNS: '메시지 패턴을 살펴보는 중',
  ANALYZING_EMOTIONAL_FLOW: '감정 흐름을 파악하는 중',
  CALCULATING_PRQC: 'PRQC 점수를 계산하는 중',
  CALCULATING_RELATIONSHIP_SCORE: '관계 온도를 측정하는 중',
};

export const RELATIONSHIP_STATUS_LABELS = {
  DRAFT: '등록 이어서 하기',
  ANALYZING: '분석 진행 중',
  ACTIVE: '분석 완료',
  ANALYSIS_FAILED: '분석 실패',
  DELETING: '삭제 처리 중',
};
