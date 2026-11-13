-- 명예의 전당에 손님·맵마다 가장 빠른 완주(의심 제외)를 따로 둔다. 순서와 처음 완주 값(finish_seconds, falls, finished_at)은 그대로다.
-- 배포 중 옛 서버가 best 없이 줄을 넣을 수 있어 null 을 허용한다. 새 서버의 syncHall 이 채운다.
alter table climb_hall
    add column best_seconds     int,
    add column best_falls       int,
    add column best_finished_at timestamptz;

update climb_hall set best_seconds = finish_seconds, best_falls = falls, best_finished_at = finished_at;

-- 서버의 ClimbRunRepository.syncHall 과 같은 규칙이다. 시간이 같으면 먼저 낸 기록을 둔다.
update climb_hall h
set best_seconds = f.finish_seconds, best_falls = f.falls, best_finished_at = f.finished_at
from (select distinct on (guest_id, map_version) guest_id, map_version, finish_seconds, falls, finished_at
      from climb_run
      where finished_at is not null and finish_seconds is not null and not suspect
      order by guest_id, map_version, finish_seconds, finished_at) f
where h.guest_id = f.guest_id and h.map_version = f.map_version
  and (h.best_seconds is null or f.finish_seconds < h.best_seconds);
