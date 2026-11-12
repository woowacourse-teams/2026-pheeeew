CREATE TABLE press_events (
    id UUID PRIMARY KEY,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    CHECK (starts_at < ends_at)
);

CREATE TABLE press_event_bests (
    event_id UUID NOT NULL REFERENCES press_events(id),
    device_id BIGINT NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    public_id UUID NOT NULL UNIQUE,
    score INTEGER NOT NULL CHECK (score BETWEEN 0 AND 400),
    achieved_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (event_id, device_id)
);

CREATE INDEX ix_press_event_bests_order
    ON press_event_bests (event_id, score DESC, achieved_at, public_id);

CREATE TABLE press_event_attempts (
    event_id UUID NOT NULL REFERENCES press_events(id),
    device_id BIGINT NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    request_id UUID NOT NULL,
    normalized_counts TEXT NOT NULL,
    total INTEGER NOT NULL CHECK (total BETWEEN 0 AND 400),
    submitted_at TIMESTAMPTZ NOT NULL,
    best_score INTEGER NOT NULL CHECK (best_score BETWEEN total AND 400),
    rank BIGINT NOT NULL CHECK (rank > 0),
    ranked_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (event_id, device_id, request_id)
);

CREATE INDEX ix_press_event_attempts_device_submitted
    ON press_event_attempts (device_id, submitted_at);
