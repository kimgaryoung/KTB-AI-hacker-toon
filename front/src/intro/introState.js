// 새로고침(페이지 로드)마다 인트로를 다시 재생하기 위한 메모리 플래그.
// 같은 로드 안에서의 SPA 이동으로 /login에 돌아올 때만 중복 재생을 막는다.
let playedThisLoad = false;

export function hasIntroPlayed() {
  return playedThisLoad;
}

export function markIntroPlayed() {
  playedThisLoad = true;
}
