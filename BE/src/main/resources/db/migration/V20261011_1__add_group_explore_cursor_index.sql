SET LOCAL lock_timeout = '5s';

-- /api/v2/groups/explore 의 (created_at, id) 커서 조회 전용입니다.
-- 컬럼 순서, 방향, 부분 조건이 그 쿼리의 ORDER BY 와 WHERE 와 정확히 같아야 범위 스캔이 됩니다.
CREATE INDEX idx_groups_explore_created_at_id
    ON groups (created_at DESC, id DESC)
    WHERE deleted_at IS NULL;
