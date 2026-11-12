// 공개 게임 도메인에서만 원격 API를 선택한다. 나머지는 로컬 프록시 기본값을 쓴다.
export function apiForHostname(hostname) {
  if (hostname === 'climb.pheeeew.com') return 'https://api.pheeeew.com/api/v1/climb';
  if (hostname === 'climb-dev.pheeeew.com') return 'https://api-dev.pheeeew.com/api/v1/climb';
  return undefined;
}
