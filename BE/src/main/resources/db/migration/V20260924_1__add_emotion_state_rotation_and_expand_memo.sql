ALTER TABLE emotions
    ADD COLUMN state VARCHAR(20),
    ADD COLUMN rotation_degrees DOUBLE PRECISION NOT NULL DEFAULT 0,
    ADD COLUMN group_stamp_id BIGINT,
    ADD COLUMN audio_object_key TEXT,
    ALTER COLUMN memo TYPE VARCHAR(200),
    ADD CONSTRAINT ck_emotions_state
        CHECK (state IN ('FRUSTRATED', 'IRRITATED', 'EXHAUSTED', 'DISCOURAGED', 'ANGRY')),
    ADD CONSTRAINT ck_emotions_rotation_degrees
        CHECK (rotation_degrees >= 0 AND rotation_degrees < 360),
    ADD CONSTRAINT ck_emotions_content_exclusive
        CHECK (memo IS NULL OR audio_object_key IS NULL),
    ADD CONSTRAINT fk_emotions_group_stamp
        FOREIGN KEY (group_stamp_id) REFERENCES group_stamps (id);
