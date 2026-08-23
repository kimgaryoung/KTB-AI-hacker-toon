-- 공식 출처를 확인한 국내 상담·위기 지원 리소스.
-- 전화번호와 운영시간은 배포 전에도 각 기관의 최신 안내를 재확인한다.
INSERT INTO support_resources (
    id, name, description, category, region, url, phone, hours,
    verified_at, source, created_at, updated_at
) VALUES
(
    '00000000-0000-0000-0000-000000000002',
    '자살예방 상담전화 109',
    '자살예방 및 위기 상황 상담을 받을 수 있는 국가 상담전화입니다.',
    'CRISIS_SUPPORT', 'KR',
    'https://www.mohw.go.kr/menu.es?mid=a10716040000',
    '109', '365일 24시간',
    CURRENT_TIMESTAMP,
    '보건복지부 자살예방 정책 추진',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
),
(
    '00000000-0000-0000-0000-000000000003',
    '정신건강위기상담전화 1577-0199',
    '정신건강 위기상담과 정신건강 관련 도움을 받을 수 있는 상담전화입니다.',
    'CRISIS_SUPPORT', 'KR',
    'https://www.mentalhealth.go.kr/portal/disease/diseaseDetail.do?dissId=26',
    '1577-0199', '365일 24시간',
    CURRENT_TIMESTAMP,
    '국가정신건강정보포털',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
),
(
    '00000000-0000-0000-0000-000000000004',
    '보건복지상담센터 129',
    '보건복지 정보와 상담을 제공하며 자살예방·긴급복지 등 위기대응 상담을 받을 수 있습니다.',
    'CRISIS_SUPPORT', 'KR',
    'https://www.mohw.go.kr/menu.es?mid=a10201020000',
    '129', '위기대응 상담 365일 24시간 · 일반상담 평일 09:00~18:00',
    CURRENT_TIMESTAMP,
    '보건복지부 민원이용안내',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
),
(
    '00000000-0000-0000-0000-000000000005',
    '여성긴급전화 1366',
    '가정폭력·성폭력·성매매·스토킹 등 폭력 피해에 대한 긴급상담과 보호 연계를 제공합니다.',
    'CRISIS_SUPPORT', 'KR',
    'https://www.mogef.go.kr/cc/wcc/cc_wcc_f001.do',
    '1366', '365일 24시간',
    CURRENT_TIMESTAMP,
    '성평등가족부 여성긴급전화1366',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);
