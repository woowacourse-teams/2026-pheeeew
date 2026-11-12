-- 명예의 전당: 완주한 순간을 그대로 남긴다. 손님·등반이 지워져도 남도록 외래 키를 걸지 않는다.
-- 손님·맵마다 첫 완주 한 줄만 둔다(먼저 도착한 순서로 상을 준다).
create table climb_hall (
    run_id         uuid primary key,
    guest_id       uuid        not null,
    map_version    varchar(16) not null,
    tag            varchar(4)  not null,
    nickname       varchar(10) not null,
    "character"    varchar(16) not null,
    finish_seconds int         not null,
    falls          int         not null,
    finished_at    timestamptz not null,
    unique (guest_id, map_version)
);

create index climb_hall_order on climb_hall (map_version, finished_at);

-- 일회성: 행사 운영자 요청으로 파라디(#f88b)의 완주 기록을 지운다. 손님과 완주하지 않은 등반은 남긴다.
-- 그 손님이 없는 DB 에서는 0줄이다. 먼저 지워서 아래 옮기기에도 들어가지 않는다.
delete from climb_run
where finished_at is not null
  and guest_id in (select id from climb_guest where tag = 'f88b' and nickname = '파라디');

-- 지금까지의 완주를 옮긴다. 서버의 ClimbRunRepository.syncHall 과 같은 규칙이다.
insert into climb_hall (run_id, guest_id, map_version, tag, nickname, "character", finish_seconds, falls, finished_at)
select distinct on (r.guest_id, r.map_version)
       r.id, r.guest_id, r.map_version, g.tag, g.nickname, r."character", r.finish_seconds, r.falls, r.finished_at
from climb_run r join climb_guest g on g.id = r.guest_id
where r.finished_at is not null and r.finish_seconds is not null and not r.suspect
order by r.guest_id, r.map_version, r.finished_at
on conflict do nothing;
