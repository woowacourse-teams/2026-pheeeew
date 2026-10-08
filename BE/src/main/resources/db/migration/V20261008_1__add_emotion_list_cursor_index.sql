SET LOCAL lock_timeout = '5s';

CREATE INDEX idx_emotions_list_created_at_id
    ON emotions (created_at DESC, id DESC)
    WHERE deleted_at IS NULL
      AND (memo IS NOT NULL OR audio_object_key IS NOT NULL);
