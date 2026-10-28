#!/usr/bin/env python3
"""Targeted PostgreSQL contract check for the initial generation handoff."""

import importlib.util
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import time
from unittest import mock
from uuid import uuid4

sys.dont_write_bytecode = True
spec = importlib.util.spec_from_file_location('press_generation', Path(__file__).with_name('press-generation.py'))
core = importlib.util.module_from_spec(spec)
spec.loader.exec_module(core)
bootstrap_spec = importlib.util.spec_from_file_location('prod_bootstrap', Path(__file__).with_name('press-prod-bootstrap.py'))
bootstrap = importlib.util.module_from_spec(bootstrap_spec)
bootstrap_spec.loader.exec_module(bootstrap)
name = 'press-owner-test-' + uuid4().hex[:12]
env = {'DB_URL': 'jdbc:postgresql://fixture:5432/postgres?sslmode=disable',
       'DB_USERNAME': 'postgres', 'DB_PASSWORD': 'fixture-only'}

try:
    subprocess.run(['docker', 'run', '-d', '--rm', '--name', name,
                    '-e', 'POSTGRES_PASSWORD=fixture-only', 'postgis/postgis:17-3.5-alpine',
                    'postgres', '-c', 'max_prepared_transactions=5'],
                   check=True, stdout=subprocess.DEVNULL)
    for _ in range(60):
        logs = subprocess.run(['docker', 'logs', name], capture_output=True, text=True)
        initialized = 'PostgreSQL init process complete; ready for start up.' in logs.stdout
        if initialized and subprocess.run(['docker', 'exec', name, 'pg_isready', '-U', 'postgres'],
                          stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode == 0:
            break
        time.sleep(0.5)
    else:
        raise RuntimeError('PostgreSQL fixture did not become ready')
    subprocess.run(['docker', 'exec', '-i', name, 'psql', '-U', 'postgres', '-v', 'ON_ERROR_STOP=1'],
                   input='CREATE TABLE press_race_control (id boolean PRIMARY KEY, enabled boolean NOT NULL, '
                         'activation_date date, active_server_generation uuid); '
                         'INSERT INTO press_race_control VALUES (true, false, NULL, NULL); '
                         'CREATE TABLE devices (id bigint PRIMARY KEY, public_id uuid NOT NULL); '
                         "INSERT INTO devices VALUES (1, '11111111-1111-4111-8111-111111111111'); "
                         'CREATE TABLE device_daily_presses (id integer PRIMARY KEY, device_id bigint NOT NULL, '
                         'press_date date NOT NULL, state text NOT NULL, press_count integer NOT NULL); '
                         'CREATE TABLE press_daily_scores (device_id bigint, press_date date, total bigint); '
                         "INSERT INTO device_daily_presses VALUES (1, 1, (clock_timestamp() AT TIME ZONE 'Asia/Seoul')::date, "
                         "'FRUSTRATED', 3), (2, 1, (clock_timestamp() AT TIME ZONE 'Asia/Seoul')::date, 'ANGRY', 7); "
                         "INSERT INTO press_daily_scores VALUES (1, (clock_timestamp() AT TIME ZONE 'Asia/Seoul')::date, 10);",
                   text=True, check=True, stdout=subprocess.DEVNULL)
    with tempfile.TemporaryDirectory() as directory:
        wrapper = Path(directory) / 'psql'
        wrapper.write_text('#!/bin/sh\nexec docker exec -i -e PGDATABASE -e PGUSER -e PGPASSWORD '
                           '-e PGOPTIONS ' + name + ' psql "$@"\n')
        wrapper.chmod(0o755)
        with mock.patch.dict(os.environ, {'PATH': directory + os.pathsep + os.environ['PATH']}):
            generation = str(uuid4())
            def sql(statement):
                subprocess.run(['docker', 'exec', '-i', name, 'psql', '-U', 'postgres',
                                '-v', 'ON_ERROR_STOP=1'], input=statement, text=True,
                               check=True, stdout=subprocess.DEVNULL)

            sql("BEGIN; SELECT 1; PREPARE TRANSACTION 'fixture-prepared'")
            drained = core.query(env, bootstrap.DRAIN_SQL)
            assert (drained['press_rows'], drained['press_total']) == (2, 10), drained
            assert drained['prepared_transactions'] == 1 and drained['remaining_sessions'] == 0, drained
            sql("ROLLBACK PREPARED 'fixture-prepared'")
            drained = core.query(env, bootstrap.DRAIN_SQL)
            assert drained['prepared_transactions'] == 0 and drained['remaining_sessions'] == 0, drained
            print('PASS: real PostgreSQL detects and clears a prepared transaction')
            original = core.query(env, bootstrap.PRESS_SNAPSHOT_SQL)
            sql('UPDATE device_daily_presses SET press_count = CASE id WHEN 1 THEN 4 ELSE 6 END')
            modified = core.query(env, bootstrap.PRESS_SNAPSHOT_SQL)
            assert original['press_total'] == modified['press_total']
            assert original['press_fingerprint'] != modified['press_fingerprint']
            sql('UPDATE device_daily_presses SET press_count = CASE id WHEN 1 THEN 3 ELSE 7 END')
            assert core.query(env, bootstrap.PRESS_SNAPSHOT_SQL)['press_fingerprint'] == original['press_fingerprint']
            print('PASS: unchanged total cannot hide changed press rows')
            probe = bootstrap.press_probe_snapshot(env, '11111111-1111-4111-8111-111111111111')
            assert probe['probe_device_id'] == 1 and probe['press_count'] == 3, probe
            assert probe['raw_total'] == probe['projected_total'] == 10, probe
            sql('UPDATE device_daily_presses SET press_count = press_count + 1 WHERE id = 1')
            probe = bootstrap.press_probe_snapshot(env, '11111111-1111-4111-8111-111111111111')
            assert (probe['press_count'], probe['raw_total'], probe['projected_total']) == (4, 11, 10), probe
            print('PASS: test-device daily press count is observed in PostgreSQL')

            idle = subprocess.Popen(['docker', 'exec', '-i', '-e', 'PGAPPNAME=idle-fixture',
                                     name, 'psql', '-U', 'postgres', '-qAt'],
                                    stdin=subprocess.PIPE, stdout=subprocess.DEVNULL,
                                    stderr=subprocess.DEVNULL, text=True)
            try:
                idle.stdin.write('SELECT 1;\n')
                idle.stdin.flush()
                for _ in range(30):
                    observed = core.query(env, "BEGIN READ ONLY; SELECT json_build_object('generation', NULL, "
                        "'enabled', FALSE, 'activation_date', NULL, 'idle_count', "
                        "(SELECT count(*) FROM pg_stat_activity WHERE application_name='idle-fixture' "
                        "AND state='idle')); COMMIT;")
                    if observed['idle_count'] == 1:
                        break
                    time.sleep(0.1)
                else:
                    raise AssertionError('Fixture idle DB session did not become visible')
                assert core.query(env, bootstrap.DRAIN_SQL)['remaining_sessions'] == 1
            finally:
                idle.stdin.close()
                try:
                    idle.wait(timeout=5)
                except subprocess.TimeoutExpired:
                    idle.kill()
                    idle.wait()
            assert core.query(env, bootstrap.DRAIN_SQL)['remaining_sessions'] == 0
            print('PASS: real PostgreSQL drain query detects an idle legacy-account session')

            for field, value in (('enabled', 'true'), ('activation_date', "'2026-10-29'")):
                sql(f'UPDATE press_race_control SET {field}={value}')
                try:
                    core.change(env, None, generation, None)
                except ValueError:
                    pass
                else:
                    raise AssertionError(f'Unexpected initial {field} was accepted')
                assert core.read_state(env)['generation'] is None
                sql('UPDATE press_race_control SET enabled=false, activation_date=NULL')

            actual_run = subprocess.run

            def lose_commit_response(*args, **kwargs):
                result = actual_run(*args, **kwargs)
                if 'UPDATE press_race_control' in kwargs.get('input', '') and result.returncode == 0:
                    result.returncode = 1
                return result

            with mock.patch.object(core.subprocess, 'run', side_effect=lose_commit_response):
                try:
                    core.change(env, None, generation, None)
                except ValueError:
                    pass
                else:
                    raise AssertionError('Lost response was not reported as uncertain')
            expected = {'generation': generation, 'enabled': False, 'activation_date': None}
            assert core.read_state(env) == expected
            try:
                core.change(env, None, str(uuid4()), None)
            except ValueError:
                pass
            else:
                raise AssertionError('Stale NULL owner was accepted')
            assert core.read_state(env) == expected
    print('PASS: real PostgreSQL commit survives lost response; stale reassignment is rejected')
finally:
    subprocess.run(['docker', 'rm', '-f', name], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
