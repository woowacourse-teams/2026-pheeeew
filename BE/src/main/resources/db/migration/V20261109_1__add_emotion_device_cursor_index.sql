SET LOCAL lock_timeout = '5s';

-- 내 감정(MY) 지도 조회가 캐시를 쓸 수 없을 때 작성 기기의 기록만 최신순으로 읽는다.
CREATE INDEX idx_emotions_device_created_at_id
    ON emotions (device_id, created_at DESC, id DESC)
    WHERE deleted_at IS NULL
      AND (memo IS NOT NULL OR audio_object_key IS NOT NULL);
