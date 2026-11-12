-- 피유 등반(climb 모듈) 전용 테이블. 본체 테이블에 외래 키를 걸지 않는다.
create table climb_guest (
    id           uuid primary key,
    tag          varchar(4)  not null,
    nickname     varchar(10) not null,
    token_hash   bytea       not null unique,
    created_at   timestamptz not null,
    last_seen_at timestamptz not null
);

create index climb_guest_last_seen on climb_guest (last_seen_at);

create table climb_run (
    id             uuid primary key,
    guest_id       uuid        not null references climb_guest (id),
    map_version    varchar(16) not null,
    "character"    varchar(16) not null,
    best_height    int         not null default 0,
    best_at        timestamptz,
    height         int         not null default 0,
    x              int         not null default 0,
    falls          int         not null default 0,
    play_seconds   int         not null default 0,
    finished_at    timestamptz,
    finish_seconds int,
    suspect        boolean     not null default false,
    started_at     timestamptz not null,
    updated_at     timestamptz not null
);

create index climb_run_guest on climb_run (guest_id, updated_at desc);
