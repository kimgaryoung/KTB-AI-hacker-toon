-- 가족 관계·의사소통 상담을 위한 공식 가족센터 안내.
INSERT INTO support_resources (
    id, name, description, category, region, url, phone, hours,
    verified_at, source, created_at, updated_at
) VALUES
(
    '00000000-0000-0000-0000-000000000006',
    '가족센터 전국 대표전화',
    '가족 갈등, 의사소통, 부모·자녀 관계 등 가족 상담과 지역 가족센터 연계를 안내합니다.',
    'RELATIONSHIP_COUNSELING', 'KR',
    'https://www.familynet.or.kr',
    '1577-9337', '운영시간은 지역 가족센터 안내를 확인해 주세요.',
    CURRENT_TIMESTAMP,
    '가족센터 공식 홈페이지',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
),
(
    '00000000-0000-0000-0000-000000000007',
    '가족상담전화',
    '가족 문제와 관계 갈등에 대한 상담 및 관련 지원서비스 연계를 안내합니다.',
    'RELATIONSHIP_COUNSELING', 'KR',
    'https://www.familynet.or.kr',
    '1644-6621', '운영시간은 공식 안내를 확인해 주세요.',
    CURRENT_TIMESTAMP,
    '가족센터 공식 홈페이지',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);
