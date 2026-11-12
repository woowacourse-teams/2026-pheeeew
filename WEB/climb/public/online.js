// 일반 플레이는 손님 인증과 실시간 연결이 모두 필요하다. 관리자 맵 점검은 별도다.
export function entryState(response) {
  if (response.status === 401) return 'nickname';
  if (response.status === 200 && typeof response.body?.id === 'string') return 'ready';
  return 'unavailable';
}

export function canPlay(admin, guest, status) {
  return admin || Boolean(guest && status === 'online');
}

// 관리자: 실시간 연결로 다른 사람과 순위를 보기만 한다. 서버도 관리자 연결의 보고는 버리지만, 기록이 될 보고는 아예 보내지 않는다.
// 자세(st)는 보낸다: 서버가 내 높이 근처 사람을 고르는 데만 쓰고 다른 사람에게는 보내지 않는다(BE/climb/README.md).
export function watchOnly(net) {
  const connect = net.connect;
  for (const k of ['reportPos', 'reportZone', 'reportFinish', 'restart', 'createGuest', 'rename']) net[k] = () => ({ status: 0 });
  net.connect = ({ character }) => connect({ character }); // 넘은 구간 수를 넘기지 않는다(연결할 때 zone 을 보내지 않게)
  return net;
}
