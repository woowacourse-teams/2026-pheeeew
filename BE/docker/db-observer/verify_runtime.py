"""로컬 Docker 전용: 격리 PostGIS와 실제 수집기 컨테이너 계약 검증."""

import json
import os
from pathlib import Path
import subprocess
import tempfile
import time
import uuid

ROOT = Path(__file__).resolve().parent
NAME = "observer-check-" + uuid.uuid4().hex[:10]


def command(*args, check=True, raw=False, **kwargs):
    result = subprocess.run(args, capture_output=True, text=True, timeout=180, **kwargs)
    if check and result.returncode:
        raise AssertionError("verification command failed (output suppressed)")
    return result if raw else result.stdout.strip()


def wait_for(test):
    deadline = time.monotonic() + 25
    while time.monotonic() < deadline:
        if test():
            return
        time.sleep(0.1)
    raise AssertionError("verification deadline exceeded")


with tempfile.TemporaryDirectory() as temp:
    env_file = Path(temp) / '.env.observer'
    env_file.write_text("PGHOST=database\nPGPORT=5432\nPGDATABASE=observer\nPGUSER=observer\n"
                        "PGSSLMODE=disable\n")
    env_file.chmod(0o600)
    password = Path(temp) / 'db.pass'
    password.write_text('database:5432:observer:observer:local-verification-only\n')
    password.chmod(0o600)
    env = os.environ | {'DB_OBSERVER_ENV_FILE': str(env_file), 'DB_OBSERVER_NETWORK': NAME,
                       'DB_OBSERVER_PASSFILE': str(password), 'DB_OBSERVER_VOLUME': NAME + '-samples'}
    def compose(*args, **kwargs):
        return command('docker', 'compose', '-p', NAME, '-f', str(ROOT / 'compose.yml'),
                       *args, env=env, **kwargs)
    def sql(query):
        return command('docker', 'exec', NAME, 'psql', '-U', 'observer', '-d', 'observer', '-Atc', query)
    try:
        command('docker', 'run', '--rm', '-v', f'{temp}:/fixture', 'python:3.13-alpine',
                'chown', '10001:10001', '/fixture/db.pass')
        command('docker', 'network', 'create', NAME)
        command('docker', 'run', '-d', '--name', NAME, '--network', NAME, '--network-alias', 'database',
                '-e', 'POSTGRES_USER=observer', '-e', 'POSTGRES_DB=observer',
                '-e', 'POSTGRES_PASSWORD=local-verification-only', 'postgis/postgis:17-3.5')
        wait_for(lambda: command('docker', 'exec', NAME, 'pg_isready', '-h', '127.0.0.1', '-U', 'observer',
                                 '-d', 'observer', check=False).endswith('accepting connections'))
        assert sql('SHOW server_version').startswith('17.')
        wait_for(lambda: command('docker', 'exec', '-e', 'PGPASSWORD=local-verification-only', NAME,
                                 'psql', '-h', '127.0.0.1', '-U', 'observer', '-d', 'observer',
                                 '-Atc', 'SELECT postgis_lib_version()', check=False).startswith('3.5'))
        command('docker', 'volume', 'create', NAME + '-samples')
        compose('build')
        compose('--profile', 'setup', 'run', '--rm', '--no-deps', 'init-storage')
        compose('up', '-d', 'db-observer')
        container = compose('ps', '-q', 'db-observer')
        def inside(code):
            return command('docker', 'exec', container, 'python3', '-c', code)
        inside("import os; s=os.stat('/data'); assert s.st_uid == os.getuid() == 10001; assert s.st_mode & 0o777 == 0o750")
        read = "import pathlib; print(''.join(p.read_text() for p in pathlib.Path('/data').glob('sample-*.jsonl')))"
        wait_for(lambda: bool(inside(read)))
        first = json.loads(inside(read).splitlines()[0])
        assert first['status'] == 'ok' and first['snapshot']['visibility'] == 'visible'
        assert first['snapshot']['sessions'] == []
        assert command('docker', 'inspect', '-f', '{{.HostConfig.RestartPolicy.Name}}', container) == 'unless-stopped'
        # 실제 Alloy UID/GID 및 보조 그룹으로 읽기만 허용되는지 검증한다.
        command('docker', 'run', '--rm', '--network', 'none', '--user', '473:473', '--group-add', '10001',
                '--entrypoint', '/bin/bash', '-v', f'{NAME}-samples:/samples:ro',
                '-v', f'{password}:/credential:ro', 'grafana/alloy:v1.19.2', '-ec',
                'cat /samples/sample-*.jsonl >/dev/null; '
                '! touch /samples/forbidden; ! cat /credential; ! cat /samples/.lock')
        # 앱에 해당하는 별도 컨테이너를 재생성해도 수집기 ID와 기록이 유지된다.
        app = NAME + '-app'
        command('docker', 'run', '-d', '--name', app, '--network', NAME, 'python:3.13-alpine', 'sleep', '60')
        command('docker', 'rm', '-f', app)
        command('docker', 'run', '-d', '--name', app, '--network', NAME, 'python:3.13-alpine', 'sleep', '60')
        before = len(inside(read).splitlines())
        wait_for(lambda: len(inside(read).splitlines()) > before)
        assert compose('ps', '-q', 'db-observer') == container
        # 컨테이너의 child 수집기 프로세스 비정상 종료는 자동 복구한다.
        command('docker', 'exec', container, 'python3', '-c',
                "import os; os.kill(int(open('/proc/1/task/1/children').read().split()[0]), 9)", check=False)
        wait_for(lambda: int(command('docker', 'inspect', '-f', '{{.RestartCount}}', container)) > 0)
        wait_for(lambda: command('docker', 'exec', container, 'python3', '-c', 'print(1)', check=False) == '1')
        # 실제 DB에서 실행 제한과 read-only를 검증한다. probe는 전용 volume에만 만든다.
        probe = """
import collect, os, pathlib, time
from threading import Event
collect.SQL = pathlib.Path('/data/probe.sql')
try:
    for query in ('SELECT pg_sleep(2)', 'CREATE TABLE forbidden_write(id int)'):
        collect.SQL.write_text(query)
        start = time.monotonic()
        assert collect.collect_once(Event())['reason'] == 'psql_failed'
        assert time.monotonic() - start < 2
    os.environ['PGPORT'] = '1'
    assert collect.collect_once(Event())['reason'] == 'psql_failed'
finally:
    collect.SQL.unlink()
"""
        inside(probe)
        assert sql("SELECT count(*) FROM pg_tables WHERE tablename='forbidden_write'") == '0'
        # 별도 클라이언트의 active 실행을 실제 표본에서 확인한다.
        command('docker', 'exec', '-d', NAME, 'psql', '-U', 'observer', '-d', 'observer',
                '-c', 'SELECT pg_sleep(20) /* private-query-marker */')
        wait_for(lambda: any(row['wait_event'] == 'PgSleep' for line in inside(read).splitlines()
                             for row in json.loads(line).get('snapshot', {}).get('sessions', [])))
        assert 'private-query-marker' not in inside(read)
        duplicate = command('docker', 'exec', container, 'python3', 'main.py', check=False, raw=True)
        assert duplicate.returncode == 1 and not duplicate.stdout
        assert json.loads(duplicate.stderr)['reason'] == 'collector_stopped'
        compose('stop', '-t', '6')
        assert command('docker', 'inspect', '-f', '{{.State.ExitCode}}', container) == '0'
        wait_for(lambda: sql("SELECT count(*) FROM pg_stat_activity WHERE application_name='pheeeew-db-observer'") == '0')
        # 기존 0700/0600 저장소를 이행하고 완성된 첫 기록을 보존한다.
        command('docker', 'run', '--rm', '--user', '10001:10001', '-v', f'{NAME}-samples:/data',
                'python:3.13-alpine', 'python3', '-c',
                "import pathlib,os; os.chmod('/data',0o700); "
                "[p.chmod(0o600) for p in pathlib.Path('/data').glob('sample-*.jsonl')]")
        compose('--profile', 'setup', 'run', '--rm', '--no-deps', 'init-storage')
        compose('up', '-d', 'db-observer')
        wait_for(lambda: command('docker', 'exec', container, 'python3', '-c',
                 "import pathlib; print(all(p.stat().st_mode & 0o777 == 0o640 for p in pathlib.Path('/data').glob('sample-*.jsonl')))", check=False) == 'True')
        assert json.loads(inside(read).splitlines()[0]) == first
        compose('stop', '-t', '6', 'db-observer')
        time.sleep(1)
        assert command('docker', 'inspect', '-f', '{{.State.Running}}', container) == 'false'
        command('docker', 'run', '--rm', '-v', f'{temp}:/fixture', 'python:3.13-alpine',
                'chmod', '0644', '/fixture/db.pass')
        rejected = compose('run', '--rm', '--no-deps', 'db-observer', check=False, raw=True)
        assert rejected.returncode == 1 and not rejected.stdout
        assert '"reason": "collector_stopped"' in rejected.stderr and 'Traceback' not in rejected.stderr
        missing = compose('run', '--rm', '--no-deps', '-e', 'PGUSER=', 'db-observer', check=False, raw=True)
        assert missing.returncode == 1 and 'configuration_missing' in missing.stderr
        logs = compose('logs', '--no-color')
        assert 'local-verification-only' not in logs and 'Traceback' not in logs
        print('PASS: snapshot, active wait, privacy, read-only, timeout, connection failure, lock, '
              'SIGTERM, disconnect, credential permissions, missing config, group read-only sharing, '
              'legacy migration, independent lifetime and automatic restart')
    finally:
        compose('down', '--volumes', '--remove-orphans', check=False)
        command('docker', 'rm', '-f', NAME + '-app', check=False)
        command('docker', 'volume', 'rm', NAME + '-samples', check=False)
        command('docker', 'rm', '-f', '-v', NAME, check=False)
        command('docker', 'network', 'rm', NAME, check=False)
