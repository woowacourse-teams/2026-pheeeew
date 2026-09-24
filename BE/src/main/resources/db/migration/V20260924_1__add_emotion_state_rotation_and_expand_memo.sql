ALTER TABLE emotions
    ADD COLUMN state VARCHAR(20),
    ADD COLUMN rotation_degrees DOUBLE PRECISION NOT NULL DEFAULT 0,
    ALTER COLUMN memo TYPE VARCHAR(200),
    ADD CONSTRAINT ck_emotions_state
        CHECK (state IN ('FRUSTRATED', 'IRRITATED', 'EXHAUSTED', 'DISCOURAGED', 'ANGRY')),
    ADD CONSTRAINT ck_emotions_rotation_degrees
        CHECK (rotation_degrees >= 0 AND rotation_degrees < 360);
