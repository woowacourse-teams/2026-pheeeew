-- 모든 서버가 공유하는 수집 및 집계 진행 상태 한 행만 보관한다.
CREATE TABLE device_activity_aggregation_states
(
    id                  BIGINT      PRIMARY KEY,
    last_aggregated_at   TIMESTAMPTZ,
    last_finalized_date  DATE,
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_device_activity_aggregation_states_singleton CHECK (id = 1)
);
