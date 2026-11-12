// 일반 플레이는 손님 인증과 실시간 연결이 모두 필요하다. 관리자 맵 점검은 별도다.
export function entryState(response) {
  if (response.status === 401) return 'nickname';
  if (response.status === 200 && typeof response.body?.id === 'string') return 'ready';
  return 'unavailable';
}

export function canPlay(admin, guest, status) {
  return admin || Boolean(guest && status === 'online');
}
