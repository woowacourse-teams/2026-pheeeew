"""격리된 가짜 DB에서 활동 원본 용량과 실제 DAU·MAU 쿼리 비용을 측정한다."""

import json
from pathlib import Path
import re
import statistics
import subprocess
import time
import uuid


ROOT = Path(__file__).resolve().parents[2]
IMAGE = "postgis/postgis:17-3.5"
MIGRATIONS = ROOT / "src/main/resources/db/migration"
REPOSITORY = ROOT / "src/main/java/com/pheeeew/activity/domain/repository/DeviceDailyActivityRepository.java"
CASES = [(1_000, 30), (1_000, 365), (10_000, 365)]


def docker(*args, input=None, check=True):
    return subprocess.run(["docker", *args], input=input, text=True, capture_output=True,
                          check=check, timeout=300)


def sql(container, database, query):
    result = docker("exec", "-i", container, "psql", "-X", "-qAt", "-v", "ON_ERROR_STOP=1",
                    "-U", "postgres", "-d", database, input=query)
    return result.stdout.strip()


def scan_nodes(node):
    scans = []
    if "Scan" in node["Node Type"]:
        scans.append({key: node[key] for key in ("Node Type", "Relation Name", "Index Name", "Actual Rows", "Actual Loops")
                      if key in node})
    for child in node.get("Plans", []):
        scans.extend(scan_nodes(child))
    return scans


def measure(container, daily, days, query):
    database = f"activity_{daily}_{days}"
    sql(container, "postgres", f"CREATE DATABASE {database};")
    for migration in ("V20260909__create_devices.sql", "V20260917_1__create_device_daily_activities.sql"):
        sql(container, database, (MIGRATIONS / migration).read_text())

    # 10개 기기 그룹이 날짜별로 번갈아 활동하며, Android/iOS는 절반씩 구성한다.
    sql(container, database, f"""
        INSERT INTO devices (public_id, request_id, platform, created_at, updated_at)
        SELECT md5('device-' || g)::uuid, md5('request-' || g)::uuid,
               CASE WHEN g % 2 = 0 THEN 'ANDROID' ELSE 'IOS' END, now(), now()
          FROM generate_series(1, {daily * 10}) AS g;
    """)
    for first in range(0, days, 30):
        last = min(first + 29, days - 1)
        sql(container, database, f"""
            INSERT INTO device_daily_activities (device_id, activity_date, created_at, updated_at)
            SELECT (day % 10) * {daily} + device,
                   DATE '2026-09-17' - ({days} - 1 - day),
                   (DATE '2026-09-17' - ({days} - 1 - day))::timestamp AT TIME ZONE 'Asia/Seoul',
                   (DATE '2026-09-17' - ({days} - 1 - day))::timestamp AT TIME ZONE 'Asia/Seoul'
              FROM generate_series({first}, {last}) AS day
             CROSS JOIN generate_series(1, {daily}) AS device
             ORDER BY day, device;
        """)
        print(f"fixture daily={daily} loaded_days={last + 1}/{days}", flush=True)
    sql(container, database, "ANALYZE devices; ANALYZE device_daily_activities;")

    results = json.loads(sql(container, database,
                             f"SET statement_timeout='3s'; SELECT json_agg(t) FROM ({query}) t;"))
    expected = [{"platform": platform, "dau": daily // 2, "mau": daily * 5}
                for platform in ("ANDROID", "IOS")]
    if sorted(results, key=lambda row: row["platform"]) != expected:
        raise AssertionError(results)
    rows = int(sql(container, database, "SELECT count(*) FROM device_daily_activities;"))
    if rows != daily * days:
        raise AssertionError(rows)

    plans = []
    for _ in range(5):
        plans.append(json.loads(sql(container, database,
                                    f"SET statement_timeout='3s'; EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) {query};"))[0])
    sizes = json.loads(sql(container, database, """
        SELECT json_agg(t) FROM (
            SELECT name, pg_table_size(name::regclass) AS table_bytes,
                   pg_indexes_size(name::regclass) AS index_bytes,
                   pg_total_relation_size(name::regclass) AS total_bytes
              FROM (VALUES ('device_daily_activities'), ('devices')) AS tables(name)
        ) t;
    """))
    plan = plans[-1]["Plan"]
    report = {"daily_devices": daily, "days": days, "rows": rows, "counts": results,
              "sizes": sizes, "execution_ms": [item["Execution Time"] for item in plans],
              "median_ms": statistics.median(item["Execution Time"] for item in plans),
              "last_plan_buffers": {key: plan.get(key, 0) for key in
                                    ("Shared Hit Blocks", "Shared Read Blocks", "Temp Read Blocks", "Temp Written Blocks")},
              "scans": scan_nodes(plan)}
    print(json.dumps(report, ensure_ascii=False), flush=True)


def main():
    queries = re.findall(r'@Query\(value = """(.*?)""", nativeQuery = true\)', REPOSITORY.read_text(), re.S)
    query = next(query for query in queries if "COUNT(DISTINCT a.device_id)" in query)
    query = query.replace(":startDate", "DATE '2026-08-19'").replace(":activityDate", "DATE '2026-09-17'").strip()
    container = "pheeeew-activity-benchmark-" + uuid.uuid4().hex[:12]
    try:
        # 운영 연결 정보와 호스트 포트를 받지 않고 자체 생성한 컨테이너만 사용한다.
        docker("run", "--detach", "--rm", "--network", "none", "--name", container,
               "--platform", "linux/amd64", "--cpus=2", "--memory=1g", "--shm-size=256m",
               "--env", "POSTGRES_PASSWORD=fixture", IMAGE)
        for _ in range(90):
            ready = docker("exec", container, "pg_isready", "-h", "127.0.0.1", "-U", "postgres", check=False)
            if ready.returncode == 0:
                break
            time.sleep(1)
        else:
            raise RuntimeError("검증용 PostgreSQL이 준비되지 않았습니다.")
        print(sql(container, "postgres", "SELECT version(); SELECT postgis_full_version(); SHOW shared_buffers; SHOW work_mem;"), flush=True)
        for daily, days in CASES:
            measure(container, daily, days, query)
    finally:
        docker("rm", "--force", "--volumes", container, check=False)


if __name__ == "__main__":
    main()
