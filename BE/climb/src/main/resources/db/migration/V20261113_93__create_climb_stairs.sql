-- 계단 러시: 검증한 판 기록과 손님별 일일 최고. 기존 등반과 독립적이다.
create table climb_stairs_run (
    id bigint generated always as identity primary key,
    guest_id uuid not null references climb_guest(id) on delete cascade,
    day date not null,
    character varchar(16) not null,
    height int not null check (height between 0 and 5000),
    time_ms int not null check (time_ms between 0 and 600000),
    created_at timestamptz not null default clock_timestamp()
);
create index climb_stairs_run_guest_day on climb_stairs_run(guest_id, day);

create table climb_stairs_best (
    guest_id uuid not null references climb_guest(id) on delete cascade,
    day date not null,
    character varchar(16) not null,
    height int not null check (height between 0 and 5000),
    time_ms int not null check (time_ms between 0 and 600000),
    achieved_at timestamptz not null,
    primary key (guest_id, day)
);
create index climb_stairs_best_ranking on climb_stairs_best(day, height desc, time_ms, achieved_at, guest_id);
