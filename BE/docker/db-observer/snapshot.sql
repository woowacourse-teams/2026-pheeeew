-- 새 read-only 트랜잭션에서 statement_timeout=500ms로 실행한다. SQL 원문은 선택하지 않는다.
WITH activity AS MATERIALIZED (
    SELECT pid, backend_start, backend_type, state, wait_event_type, wait_event,
           query_start, xact_start, state_change
    FROM pg_stat_activity
    WHERE datname = current_database() AND pid <> pg_backend_pid()
), counts AS (
    SELECT count(*) AS session_count,
           count(*) FILTER (WHERE state IS NULL) AS unknown_state_count,
           count(*) FILTER (WHERE state = 'disabled') AS disabled_count,
           count(*) FILTER (WHERE state NOT IN ('idle', 'disabled')) AS eligible_count,
           count(*) FILTER (WHERE state = 'active') AS active_count,
           count(*) FILTER (WHERE state IN ('idle in transaction', 'idle in transaction (aborted)'))
               AS idle_in_transaction_count,
           count(*) FILTER (WHERE state NOT IN ('idle', 'disabled') AND wait_event_type = 'Lock')
               AS lock_wait_count,
           count(*) FILTER (WHERE state NOT IN ('idle', 'disabled') AND wait_event_type = 'IO')
               AS io_wait_count,
           count(*) FILTER (WHERE state NOT IN ('idle', 'disabled')
               AND wait_event_type NOT IN ('Lock', 'IO')) AS other_wait_count,
           count(*) FILTER (WHERE state NOT IN ('idle', 'disabled') AND wait_event_type IS NULL)
               AS no_wait_count
    FROM activity
), selected AS MATERIALIZED (
    SELECT * FROM activity WHERE state NOT IN ('idle', 'disabled')
    ORDER BY (wait_event_type = 'Lock') DESC NULLS LAST, xact_start NULLS LAST, pid
    LIMIT 20
), blocked AS MATERIALIZED (
    SELECT s.*, CASE WHEN wait_event_type = 'Lock' THEN ARRAY(
        SELECT DISTINCT blocker FROM unnest(pg_blocking_pids(s.pid)) AS blocker ORDER BY blocker
    ) END AS blockers
    FROM selected s
)
SELECT jsonb_build_object(
    'sampled_at', to_char(statement_timestamp() AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"'),
    'visibility', CASE WHEN unknown_state_count + disabled_count > 0 THEN 'partial' ELSE 'visible' END,
    'read_all_stats', pg_has_role(current_user, 'pg_read_all_stats', 'USAGE'),
    'session_count', session_count, 'unknown_state_count', unknown_state_count,
    'disabled_count', disabled_count, 'eligible_count', eligible_count,
    'omitted_count', greatest(eligible_count - 20, 0),
    -- 상세 20개 제한 이전에 집계한 관측 가능한 세션 수. NULL 대기는 CPU 사용을 뜻하지 않는다.
    'active_count', active_count, 'idle_in_transaction_count', idle_in_transaction_count,
    'lock_wait_count', lock_wait_count, 'io_wait_count', io_wait_count,
    'other_wait_count', other_wait_count, 'no_wait_count', no_wait_count,
    'sessions', coalesce((SELECT jsonb_agg(jsonb_build_object(
        'pid', pid, 'backend_start', backend_start, 'backend_type', backend_type,
        'state', state, 'wait_event_type', wait_event_type, 'wait_event', wait_event,
        'query_elapsed_ms', CASE WHEN state = 'active' AND query_start IS NOT NULL
            THEN greatest(0, extract(epoch FROM statement_timestamp() - query_start) * 1000) END,
        'transaction_elapsed_ms', CASE WHEN xact_start IS NOT NULL
            THEN greatest(0, extract(epoch FROM statement_timestamp() - xact_start) * 1000) END,
        'state_elapsed_ms', CASE WHEN state_change IS NOT NULL
            THEN greatest(0, extract(epoch FROM statement_timestamp() - state_change) * 1000) END,
        'blocker_pids', blockers[1:5], 'blocker_count', cardinality(blockers)
    ) ORDER BY pid) FROM blocked), '[]'::jsonb)
) FROM counts;
