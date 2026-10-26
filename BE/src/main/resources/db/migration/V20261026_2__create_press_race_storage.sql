-- 미게시 초기 스키마. 동점 표시는 날짜 내 고정 public_id로 정하고 전역 도달·변경 번호를 저장하지 않는다.
-- 확장만 수행한다. 활성 서버 지정과 경쟁 활성화는 별도 운영 절차이며 부팅으로 획득하지 않는다.
CREATE TABLE press_race_control (
    id BOOLEAN PRIMARY KEY DEFAULT TRUE CHECK (id),
    active_server_generation UUID,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    activation_date DATE
);
INSERT INTO press_race_control (id) VALUES (TRUE);

CREATE TABLE press_device_controls (
    device_id BIGINT PRIMARY KEY REFERENCES devices(id) ON DELETE CASCADE,
    connection_generation UUID,
    ready BOOLEAN NOT NULL DEFAULT FALSE,
    tokens NUMERIC NOT NULL DEFAULT 100 CHECK (tokens >= 0 AND tokens <= 100),
    refilled_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CHECK (NOT ready OR connection_generation IS NOT NULL)
);

CREATE TABLE press_daily_scores (
    device_id BIGINT NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    press_date DATE NOT NULL,
    total BIGINT NOT NULL CHECK (total > 0),
    last_applied_at TIMESTAMPTZ NOT NULL,
    public_id UUID NOT NULL UNIQUE,
    PRIMARY KEY (device_id, press_date)
);

CREATE TABLE press_inputs (
    device_id BIGINT NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    request_id UUID NOT NULL,
    normalized_counts TEXT NOT NULL,
    content_hash TEXT NOT NULL,
    first_received_at TIMESTAMPTZ,
    original_generation UUID,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'APPLIED', 'REJECTED')),
    rejection_code VARCHAR(50),
    applied_date DATE,
    finalized_at TIMESTAMPTZ,
    recovered_by_generation UUID,
    PRIMARY KEY (device_id, request_id),
    CHECK (
        (first_received_at IS NOT NULL AND original_generation IS NOT NULL)
        OR (first_received_at IS NULL AND original_generation IS NULL
            AND status = 'REJECTED' AND rejection_code IS NOT NULL
            AND rejection_code = 'NOT_APPLIED_ON_RECOVERY'
            AND recovered_by_generation IS NOT NULL AND finalized_at IS NOT NULL)
    ),
    CHECK (
        (status = 'PENDING' AND rejection_code IS NULL AND finalized_at IS NULL
            AND applied_date IS NULL
            AND recovered_by_generation IS NULL)
        OR (status = 'APPLIED' AND rejection_code IS NULL AND finalized_at IS NOT NULL
            AND applied_date IS NOT NULL AND recovered_by_generation IS NULL)
        OR (status = 'REJECTED' AND rejection_code IS NOT NULL AND finalized_at IS NOT NULL
            AND applied_date IS NULL
            AND ((rejection_code = 'NOT_APPLIED_ON_RECOVERY' AND recovered_by_generation IS NOT NULL)
                OR (rejection_code <> 'NOT_APPLIED_ON_RECOVERY' AND recovered_by_generation IS NULL)))
    )
);
-- 닫힌 세대의 모든 미확정 접수를 정산한다. 처리 이력 자동 삭제는 하지 않는다.
CREATE INDEX ix_press_inputs_pending ON press_inputs(device_id) WHERE status = 'PENDING';
