#!/usr/bin/env python3
"""Draft production migration tool; not yet validated for production use.

The operator must stop CD and other writers before recording quiescence.
No action starts or stops containers.
"""

import argparse
import base64
import fcntl
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import stat
import subprocess
import sys
import time
from datetime import date
from uuid import UUID, uuid4

sys.dont_write_bytecode = True
spec = importlib.util.spec_from_file_location('press_generation', Path(__file__).with_name('press-generation.py'))
core = importlib.util.module_from_spec(spec)
spec.loader.exec_module(core)
lock_spec = importlib.util.spec_from_file_location('prod_lock', Path(__file__).with_name('press-prod-lock.py'))
operations_lock = importlib.util.module_from_spec(lock_spec)
lock_spec.loader.exec_module(operations_lock)
manifest_spec = importlib.util.spec_from_file_location('prod_manifest', Path(__file__).with_name('press-prod-manifest.py'))
release_manifest = importlib.util.module_from_spec(manifest_spec)
manifest_spec.loader.exec_module(release_manifest)
core.STATE_FILE = Path('.press-prod-bootstrap.json')
LEGACY = 'pheeeew-prod-app'
CANDIDATE = 'pheeeew-prod-app-blue'
NGINX = 'pheeeew-prod-nginx'
RETIRED_NGINX = 'pheeeew-prod-nginx-before-press'
DRAIN_SQL = "BEGIN READ ONLY; SELECT json_build_object('generation', NULL, " \
    "'enabled', FALSE, 'activation_date', NULL, 'control_absent', " \
    "to_regclass('press_race_control') IS NULL, 'remaining_sessions', " \
    "(SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() " \
    "AND usename=session_user AND backend_type='client backend' AND pid<>pg_backend_pid()), " \
    "'prepared_transactions', (SELECT count(*) FROM pg_prepared_xacts WHERE database=current_database()), " \
    "'press_rows', (SELECT count(*) FROM device_daily_presses), " \
    "'press_total', (SELECT COALESCE(SUM(press_count), 0) FROM device_daily_presses), " \
    "'press_fingerprint', (SELECT encode(sha256(convert_to(COALESCE(" \
    "string_agg(md5(row_to_json(p)::text), '' ORDER BY p.id), ''), 'UTF8')), 'hex') " \
    "FROM device_daily_presses p)); COMMIT;"
PRESS_SNAPSHOT_SQL = "BEGIN READ ONLY; SELECT json_build_object('generation', NULL, 'enabled', FALSE, " \
    "'activation_date', NULL, 'press_rows', (SELECT count(*) FROM device_daily_presses), " \
    "'press_total', (SELECT COALESCE(SUM(press_count), 0) FROM device_daily_presses), " \
    "'press_fingerprint', (SELECT encode(sha256(convert_to(COALESCE(" \
    "string_agg(md5(row_to_json(p)::text), '' ORDER BY p.id), ''), 'UTF8')), 'hex') " \
    "FROM device_daily_presses p)); COMMIT;"


def prepare(image_id, lock_token):
    if os.path.lexists(core.STATE_FILE) or os.path.lexists(str(core.STATE_FILE) + '.new'):
        raise ValueError('Existing bootstrap journal; inspect it before proceeding')
    if not image_id.startswith('sha256:') or len(image_id) != 71:
        raise ValueError('Candidate image must be identified by immutable image ID')
    int(image_id[7:], 16)
    legacy = core.container(LEGACY)
    proxy = core.container(NGINX)
    env = core.environment(legacy)
    if (env.get('SPRING_PROFILES_ACTIVE') != 'prod'
            or (legacy['Config'].get('Labels') or {}).get(core.CONTRACT_LABEL)
            or env.get(core.GENERATION_KEY)):
        raise ValueError('Running app is not the expected legacy production app')
    inspected = subprocess.run(['docker', 'image', 'inspect', image_id], text=True,
                               capture_output=True, check=True, timeout=10)
    image = json.loads(inspected.stdout)[0]
    if (image['Id'] != image_id
            or (image['Config'].get('Labels') or {}).get(core.CONTRACT_LABEL) != '2'):
        raise ValueError('Candidate image is not the expected contract 2 image')
    control = core.query(env, "BEGIN READ ONLY; SELECT json_build_object('generation', NULL, "
        "'enabled', FALSE, 'activation_date', NULL, 'control_absent', "
        "to_regclass('press_race_control') IS NULL); COMMIT;")
    if control.get('control_absent') is not True:
        raise ValueError('Control table already exists; initial migration is not eligible')
    if core.identity(core.container(LEGACY), True) != core.identity(legacy, True):
        raise ValueError('Legacy app restarted during preparation')
    if core.identity(core.container(NGINX), True) != core.identity(proxy, True):
        raise ValueError('Nginx restarted during preparation')
    state = {'phase': 'PREPARED', 'lock_token': lock_token,
             'legacy_container': LEGACY,
             'legacy_identity': core.identity(legacy, True), 'database': list(core.db_target(env)),
             'legacy_proxy_identity': core.identity(proxy, True),
             'legacy_restart_policy': legacy['HostConfig']['RestartPolicy'],
             'candidate_image': image_id, 'target_generation': str(uuid4())}
    core.save_state(state)
    print(json.dumps({'phase': state['phase'], 'target_generation': state['target_generation']}))


def stopped_legacy(state):
    legacy = core.container(LEGACY, require_running=False)
    if (state['legacy_container'] != LEGACY or core.identity(legacy, True) != state['legacy_identity']
            or legacy['State']['Running']
            or legacy['State']['Restarting']
            or legacy['HostConfig']['RestartPolicy']['Name'] != 'no'):
        raise ValueError('Legacy app must be stopped with restart disabled')
    return legacy


def stopped_proxy(state):
    proxy = core.container(NGINX, require_running=False)
    if (core.identity(proxy, True) != state['legacy_proxy_identity'] or proxy['State']['Running']
            or proxy['State'].get('Restarting', False)
            or proxy['HostConfig']['RestartPolicy']['Name'] != 'no'):
        raise ValueError('Original nginx must be stopped with restart disabled')
    return proxy


def verify_drained():
    state = json.loads(core.STATE_FILE.read_text())
    if state['phase'] != 'PREPARED':
        raise ValueError('Unexpected bootstrap phase')
    legacy = stopped_legacy(state)
    stopped_proxy(state)
    env = core.environment(legacy)
    if list(core.db_target(env)) != state['database']:
        raise ValueError('Legacy database target changed')
    result = core.query(env, DRAIN_SQL)
    if (result.get('control_absent') is not True or result.get('remaining_sessions') != 0
            or result.get('prepared_transactions') != 0 or not isinstance(result.get('press_rows'), int)
            or not isinstance(result.get('press_total'), int)
            or not isinstance(result.get('press_fingerprint'), str)
            or len(result['press_fingerprint']) != 64):
        raise ValueError('Legacy sessions, prepared transactions or control table remain')
    stopped_legacy(state)
    stopped_proxy(state)
    state['press_snapshot'] = {key: result[key] for key in ('press_rows', 'press_total', 'press_fingerprint')}
    state['phase'] = 'LEGACY_STOPPED'
    core.save_state(state)
    print(json.dumps({'phase': state['phase']}))


def require_isolated_candidate(candidate, restart_policy='no'):
    host = candidate['HostConfig']
    mode = host['NetworkMode']
    ports = candidate['NetworkSettings']['Ports']
    if (not isinstance(mode, str) or not mode or mode in ('host', 'none')
            or mode.startswith('container:') or host['PublishAllPorts'] is not False
            or any(bindings for bindings in (host['PortBindings'] or {}).values())
            or any(bindings for bindings in (ports or {}).values())
            or host['RestartPolicy']['Name'] != restart_policy):
        raise ValueError('Candidate must have no published ports and automatic restart disabled')


def record_candidate():
    state = json.loads(core.STATE_FILE.read_text())
    if state['phase'] != 'LEGACY_STOPPED':
        raise ValueError('Legacy stop must be verified first')
    ensure_writer_isolation(state)
    verify_blue_environment(state)
    if state.get('blue_config_verified') is not True:
        raise ValueError('Candidate configuration was not checked before startup')
    candidate = core.container(CANDIDATE)
    require_isolated_candidate(candidate)
    env = core.environment(candidate)
    labels = candidate['Config'].get('Labels') or {}
    networks = candidate['NetworkSettings'].get('Networks') or {}
    if (labels.get('com.docker.compose.project') != 'pheeeew-prod'
            or labels.get('com.docker.compose.service') != 'app-blue'
            or set(networks) != {'pheeeew-prod_default'}
            or 'app-blue' not in (networks['pheeeew-prod_default'].get('Aliases') or [])):
        raise ValueError('Candidate is not the production blue compose service')
    if (candidate['Image'] != state['candidate_image'] or env.get('SPRING_PROFILES_ACTIVE') != 'prod'
            or env.get('APP_RELEASE') != state['reviewed_revision']
            or env.get(core.GENERATION_KEY) != state['target_generation']
            or list(core.db_target(env)) != state['database']):
        raise ValueError('Candidate image, generation or database changed')
    ops = core.operations()
    gate = ops.gate(candidate)
    if not gate.startswith('CLOSED '):
        raise ValueError('Candidate admission must remain CLOSED')
    control = core.read_state(env)
    if (control.get('generation') is not None or control.get('enabled') is not False
            or control.get('activation_date') is not None):
        raise ValueError('Initial control row is not NULL/OFF/NULL')
    snapshot = core.query(env, PRESS_SNAPSHOT_SQL)
    if {key: snapshot.get(key) for key in ('press_rows', 'press_total', 'press_fingerprint')} != state['press_snapshot']:
        raise ValueError('Legacy press data changed during migration')
    if core.runtime_generation(candidate, required_code='SERVER_DRAINING') != state['target_generation']:
        raise ValueError('Candidate runtime generation differs from its configuration')
    current_candidate = core.container(CANDIDATE)
    require_isolated_candidate(current_candidate)
    if core.identity(current_candidate, True) != core.identity(candidate, True):
        raise ValueError('Candidate restarted during verification')
    if ops.gate(candidate) != gate:
        raise ValueError('Candidate admission changed during verification')
    stopped_legacy(state)
    stopped_proxy(state)
    state.update(phase='CANDIDATE_CLOSED', candidate_container=CANDIDATE,
                 candidate_identity=core.identity(candidate, True), candidate_gate=gate)
    core.save_state(state)
    print(json.dumps({'phase': state['phase']}))


def checked_candidate(state):
    stopped_legacy(state)
    stopped_proxy(state)
    candidate = core.container(CANDIDATE)
    require_isolated_candidate(candidate)
    env = core.environment(candidate)
    if (core.identity(candidate, True) != state['candidate_identity']
            or candidate['Image'] != state['candidate_image']
            or env.get(core.GENERATION_KEY) != state['target_generation']
            or list(core.db_target(env)) != state['database']
            or core.operations().gate(candidate) != state['candidate_gate']
            or core.runtime_generation(candidate, required_code='SERVER_DRAINING') != state['target_generation']):
        raise ValueError('Candidate identity or admission changed')
    current_candidate = core.container(CANDIDATE)
    require_isolated_candidate(current_candidate)
    if (core.identity(current_candidate, True) != state['candidate_identity']
            or core.operations().gate(candidate) != state['candidate_gate']):
        raise ValueError('Candidate changed during verification')
    stopped_legacy(state)
    stopped_proxy(state)
    return env


def confirm_owner(state):
    if state['phase'] != 'HANDOFF_ATTEMPTED':
        raise ValueError('No recorded handoff attempt')
    env = checked_candidate(state)
    desired = {'generation': state['target_generation'], 'enabled': False, 'activation_date': None}
    if core.read_state(env) != desired:
        raise ValueError('Handoff outcome is unconfirmed; do not replay')
    checked_candidate(state)
    if core.read_state(env) != desired:
        raise ValueError('Handoff state changed during confirmation')
    # This records the DB outcome only; it is not a maintenance or OPEN approval.
    state['phase'] = 'OWNER_CONFIRMED'
    core.save_state(state)
    print(json.dumps({'phase': state['phase']}))


def attest_quiescence(state, acknowledgement):
    if state['phase'] != 'LEGACY_STOPPED' or acknowledgement != 'CD_STOPPED_AND_NO_OTHER_WRITERS':
        raise ValueError('Explicit CD and writer quiescence acknowledgement is required')
    stopped_legacy(state)
    stopped_proxy(state)
    # This is an operator assertion, not an automated GitHub or database audit.
    state['operator_asserted_cd_and_writers_stopped'] = True
    core.save_state(state)
    print(json.dumps({'phase': state['phase'], 'operator_assertion_recorded': True}))


def ensure_writer_isolation(state):
    if state.get('operator_asserted_cd_and_writers_stopped') is not True:
        raise ValueError('Operator CD and writer quiescence assertion is required')
    stopped_legacy(state)
    stopped_proxy(state)


def blue_environment_bytes(state):
    return f"PRESS_BLUE_GENERATION={str(UUID(state['target_generation']))}\nPRESS_GREEN_GENERATION=\n".encode()


def verify_blue_environment(state):
    if state.get('blue_environment_written') is not True:
        raise ValueError('Blue generation configuration has not been recorded')
    expected = blue_environment_bytes(state)
    fd = os.open(core.ENV_FILE, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
    try:
        if not stat.S_ISREG(os.fstat(fd).st_mode) or os.read(fd, len(expected) + 1) != expected:
            raise ValueError('Blue generation configuration changed')
    finally:
        os.close(fd)


def write_blue_environment(state):
    if state['phase'] != 'LEGACY_STOPPED':
        raise ValueError('Blue configuration requires a verified stop')
    ensure_writer_isolation(state)
    expected = blue_environment_bytes(state)
    path = core.ENV_FILE
    try:
        fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
    except FileExistsError:
        fd = os.open(path, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
        try:
            if not stat.S_ISREG(os.fstat(fd).st_mode) or os.read(fd, len(expected) + 1) != expected:
                raise ValueError('Existing generation configuration differs; inspect it')
        finally:
            os.close(fd)
    else:
        try:
            if os.write(fd, expected) != len(expected):
                raise OSError('Incomplete blue generation configuration')
            os.fsync(fd)
        finally:
            os.close(fd)
        directory = os.open(str(path.parent), os.O_RDONLY)
        try:
            os.fsync(directory)
        finally:
            os.close(directory)
    state['blue_environment_written'] = True
    core.save_state(state)
    print(json.dumps({'phase': state['phase'], 'blue_environment_written': True}))


def check_blue_configuration(state):
    if state['phase'] != 'LEGACY_STOPPED':
        raise ValueError('Candidate configuration requires verified downtime')
    ensure_writer_isolation(state)
    verify_blue_environment(state)
    if not re.fullmatch(r'[0-9a-f]{40}', state.get('reviewed_revision', '')):
        raise ValueError('Candidate release commit has not been confirmed')
    existing = subprocess.run(['docker', 'container', 'ls', '-a', '--filter',
                               'name=^/' + CANDIDATE + '$', '--format', '{{.Names}}'],
                              text=True, capture_output=True, check=True, timeout=10)
    if existing.stdout.strip():
        raise ValueError('Blue candidate already exists; migration may already have started')
    image_ref = os.environ['APP_IMAGE']
    if os.environ.get('APP_RESTART_POLICY') != 'no':
        raise ValueError('Initial blue startup must explicitly disable automatic restart')
    legacy = stopped_legacy(state)
    old_env = core.environment(legacy)
    rendered = subprocess.run(['docker', 'compose', '--env-file', '.env', '--env-file',
                               'press-generations.env', '-f', 'compose.yml', '--profile',
                               'blue', 'config', '--format', 'json'],
                              check=True, capture_output=True, timeout=30)
    app = json.loads(rendered.stdout)['services']['app-blue']
    env = app['environment']
    image = subprocess.run(['docker', 'image', 'inspect', image_ref], text=True,
                           capture_output=True, check=True, timeout=10)
    image_id = json.loads(image.stdout)[0]['Id']
    if (image_id != state['candidate_image'] or app.get('image') != image_ref
            or app.get('container_name') != CANDIDATE or app.get('restart') != 'no'
            or env.get('SPRING_PROFILES_ACTIVE') != 'prod'
            or env.get(core.GENERATION_KEY) != state['target_generation']
            or env.get('DB_URL') != old_env.get('DB_URL')
            or env.get('DB_USERNAME') != old_env.get('DB_USERNAME')
            or list(core.db_target(env)) != state['database']):
        raise ValueError('Blue startup configuration differs from the recorded production target')
    ensure_writer_isolation(state)
    state['blue_config_verified'] = True
    state['blue_image_ref'] = image_ref
    core.save_state(state)
    print(json.dumps({'phase': state['phase'], 'blue_config_verified': True}))


def attest_image_release(state, revision):
    if state['phase'] not in ('PREPARED', 'LEGACY_STOPPED') or not re.fullmatch(r'[0-9a-f]{40}', revision):
        raise ValueError('Initial release commit cannot be recorded in this phase')
    result = subprocess.run(['docker', 'image', 'inspect', state['candidate_image']], text=True,
                            capture_output=True, check=True, timeout=10)
    image = json.loads(result.stdout)[0]
    if (image['Id'] != state['candidate_image']
            or (image['Config'].get('Labels') or {}).get(core.CONTRACT_LABEL) != '2'
            or core.environment(image).get('APP_RELEASE') != revision):
        raise ValueError('Candidate image is not the reviewed release commit')
    state['reviewed_revision'] = revision
    core.save_state(state)
    print(json.dumps({'phase': state['phase'], 'reviewed_revision': revision}))


def assign_owner(state):
    ensure_writer_isolation(state)
    if state['phase'] != 'CANDIDATE_CLOSED':
        raise ValueError('Candidate must be recorded CLOSED before handoff')
    env = checked_candidate(state)
    if core.read_state(env) != {'generation': None, 'enabled': False, 'activation_date': None}:
        raise ValueError('Initial control state changed')
    state['phase'] = 'HANDOFF_ATTEMPTED'
    core.save_state(state)
    # A missing response cannot establish whether COMMIT happened. Never replay here.
    core.change(env, None, state['target_generation'], None)
    confirm_owner(state)


def confirm_open(state):
    if state['phase'] != 'OPEN_ATTEMPTED':
        raise ValueError('No recorded candidate OPEN attempt')
    commands = [item for item in state.get('gate_commands', []) if item['purpose'] == 'bootstrap-open']
    if len(commands) != 1:
        raise ValueError('OPEN intent is missing or ambiguous')
    command = commands[0]
    candidate = core.container(CANDIDATE)
    require_isolated_candidate(candidate)
    desired = {'generation': state['target_generation'], 'enabled': False, 'activation_date': None}
    expected_gate = command['replacement']
    if (command['container_id'] != candidate['Id'] or command['expected'] != state['candidate_gate']
            or not expected_gate.startswith('OPEN ') or core.identity(candidate, True) != state['candidate_identity']
            or candidate['Image'] != state['candidate_image']
            or list(core.db_target(core.environment(candidate))) != state['database']):
        raise ValueError('Candidate OPEN intent or identity differs')
    stopped_legacy(state)
    stopped_proxy(state)
    env = core.environment(candidate)
    ops = core.operations()
    if (ops.gate(candidate) != expected_gate or core.read_state(env) != desired
            or core.runtime_generation(candidate, required_code='DEVICE_NOT_FOUND') != state['target_generation']):
        raise ValueError('Candidate OPEN outcome is uncertain; do not replay')
    current = core.container(CANDIDATE)
    require_isolated_candidate(current)
    if (core.identity(current, True) != state['candidate_identity']
            or ops.gate(current) != expected_gate or core.read_state(env) != desired):
        raise ValueError('Candidate changed while confirming OPEN')
    stopped_legacy(state)
    stopped_proxy(state)
    state['phase'] = 'OPEN_CONFIRMED'
    core.save_state(state)
    print(json.dumps({'phase': state['phase']}))


def verify_open_blue(state, restart_policy='no'):
    stopped_legacy(state)
    verify_blue_environment(state)
    candidate = core.container(CANDIDATE)
    require_isolated_candidate(candidate, restart_policy)
    env = core.environment(candidate)
    commands = [item for item in state.get('gate_commands', []) if item['purpose'] == 'bootstrap-open']
    expected = {'generation': state['target_generation'], 'enabled': False, 'activation_date': None}
    if (len(commands) != 1 or commands[0]['container_id'] != candidate['Id']
            or not commands[0]['replacement'].startswith('OPEN ')
            or core.identity(candidate, True) != state['candidate_identity']
            or candidate['Image'] != state['candidate_image']
            or env.get(core.GENERATION_KEY) != state['target_generation']
            or list(core.db_target(env)) != state['database']
            or core.operations().gate(candidate) != commands[0]['replacement']
            or core.read_state(env) != expected
            or core.runtime_generation(candidate, required_code='DEVICE_NOT_FOUND') != state['target_generation']):
        raise ValueError('Open blue identity or ownership changed')
    if core.identity(core.container(CANDIDATE), True) != state['candidate_identity']:
        raise ValueError('Blue restarted while checking its open state')
    return candidate


def verify_retired_proxy(state):
    old = core.container(state['legacy_proxy_identity']['id'], require_running=False)
    if (core.identity(old, True) != state['legacy_proxy_identity']
            or old.get('Name') != '/' + RETIRED_NGINX or old['State']['Running']
            or old['State'].get('Restarting', False)
            or old['HostConfig']['RestartPolicy']['Name'] != 'no'):
        raise ValueError('Original nginx retirement is unconfirmed')


def attest_proxy_config(state, expected):
    if state['phase'] not in ('LEGACY_STOPPED', 'CANDIDATE_CLOSED', 'OWNER_CONFIRMED'):
        raise ValueError('Proxy config must be pinned before opening the candidate')
    ensure_writer_isolation(state)
    if not re.fullmatch(r'[0-9a-f]{64}', expected):
        raise ValueError('Expected config SHA-256 is invalid')
    if proxy_config_digest() != expected:
        raise ValueError('Proxy config differs from the reviewed release')
    state['expected_proxy_config_sha256'] = expected
    core.save_state(state)
    print(json.dumps({'phase': state['phase'], 'proxy_config_sha256': expected}))


def proxy_config_digest():
    fd = os.open('nginx/api-bluegreen.conf', os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
    try:
        if not stat.S_ISREG(os.fstat(fd).st_mode):
            raise ValueError('Proxy config is not a regular file')
        with os.fdopen(fd, 'rb', closefd=False) as source:
            content = source.read(1024 * 1024 + 1)
        if len(content) > 1024 * 1024:
            raise ValueError('Proxy config exceeds the size limit')
        actual = hashlib.sha256(content).hexdigest()
    finally:
        os.close(fd)
    return actual


def confirm_proxy_retired(state):
    if state['phase'] != 'PROXY_RETIRE_ATTEMPTED':
        raise ValueError('No proxy retirement intent')
    verify_open_blue(state)
    verify_retired_proxy(state)
    state['phase'] = 'PROXY_RETIRED'
    core.save_state(state)
    print(json.dumps({'phase': state['phase'], 'retired_proxy': RETIRED_NGINX}))


def retire_proxy(state):
    if state['phase'] != 'OPEN_CONFIRMED':
        raise ValueError('Blue must be confirmed OPEN before retiring nginx')
    verify_open_blue(state)
    old = stopped_proxy(state)
    state['phase'] = 'PROXY_RETIRE_ATTEMPTED'
    core.save_state(state)
    try:
        subprocess.run(['docker', 'rename', old['Id'], RETIRED_NGINX],
                       check=True, capture_output=True, timeout=10)
    except (OSError, subprocess.SubprocessError):
        pass  # A lost response cannot establish whether Docker completed the rename.
    confirm_proxy_retired(state)


def confirm_proxy_live(state):
    if state['phase'] != 'PROXY_RETIRED':
        raise ValueError('Original nginx must be retired before confirming the new proxy')
    candidate = verify_open_blue(state)
    verify_retired_proxy(state)
    proxy = core.container(NGINX)
    labels = proxy['Config'].get('Labels') or {}
    mounts = {item['Destination']: item for item in proxy.get('Mounts', [])}
    expected_mounts = {'/etc/nginx/conf.d/default.conf': '/opt/pheeeew/nginx/api-bluegreen.conf',
                       '/etc/nginx/upstream': '/opt/pheeeew/nginx/upstream'}
    if (labels.get('com.docker.compose.project') != 'pheeeew-nginx-prod'
            or labels.get('com.docker.compose.service') != 'nginx'
            or set(proxy['NetworkSettings'].get('Networks') or {}) != {'pheeeew-prod_default'}):
        raise ValueError('New nginx is not the expected production compose service')
    for destination, source in expected_mounts.items():
        mount = mounts.get(destination) or {}
        if mount.get('Type') != 'bind' or mount.get('Source') != source or mount.get('RW') is not False:
            raise ValueError('New nginx configuration mount differs')
    config = read_regular('nginx/api-bluegreen.conf', 1024 * 1024)
    if hashlib.sha256(config).hexdigest() != state.get('expected_proxy_config_sha256'):
        raise ValueError('New nginx configuration is not the pinned release config')
    actual = subprocess.run(['docker', 'exec', proxy['Id'], 'cat', '/etc/nginx/conf.d/default.conf'],
                            capture_output=True, check=True, timeout=10).stdout
    if actual != config:
        raise ValueError('New nginx configuration differs from its mounted source')
    subprocess.run(['docker', 'exec', proxy['Id'], 'nginx', '-t'], check=True, capture_output=True, timeout=10)
    core.operations().route('app-blue', 443, 'api.pheeeew.com')
    if core.probe_generation(core.environment(candidate), ['curl'],
            'https://api.pheeeew.com/api/v1/press-race', required_code='DEVICE_NOT_FOUND') != state['target_generation']:
        raise ValueError('Public authenticated WebSocket did not reach blue')
    if core.identity(core.container(NGINX), True) != core.identity(proxy, True):
        raise ValueError('New nginx restarted during public confirmation')
    verify_open_blue(state)
    verify_retired_proxy(state)
    state.update(phase='PUBLIC_CONFIRMED', proxy_identity=core.identity(proxy, True),
                 proxy_config_sha256=hashlib.sha256(config).hexdigest())
    core.save_state(state)
    print(json.dumps({'phase': state['phase']}))


def press_probe_snapshot(env, device, day=None):
    subject = str(UUID(device))
    recorded_day = 'today' if day is None else f"DATE '{date.fromisoformat(day).isoformat()}'"
    sql = f"""BEGIN ISOLATION LEVEL REPEATABLE READ READ ONLY;
    SELECT json_build_object('generation', c.active_server_generation, 'enabled', c.enabled,
      'activation_date', c.activation_date, 'probe_date', t.day, 'current_date', t.today, 'probe_device_id', d.id,
      'press_count', COALESCE((SELECT press_count FROM device_daily_presses p
        WHERE p.device_id = d.id AND p.press_date = t.day AND p.state = 'FRUSTRATED'), 0),
      'raw_total', (SELECT COALESCE(SUM(p.press_count), 0) FROM device_daily_presses p
        WHERE p.device_id = d.id AND p.press_date = t.day),
      'projected_total', (SELECT s.total FROM press_daily_scores s
        WHERE s.device_id = d.id AND s.press_date = t.day))
    FROM press_race_control c
    CROSS JOIN (SELECT today, {recorded_day} AS day FROM
      (SELECT (clock_timestamp() AT TIME ZONE 'Asia/Seoul')::date AS today) clock) t
    LEFT JOIN devices d ON d.public_id = '{subject}'::uuid WHERE c.id = TRUE;
    COMMIT;"""
    return core.query(env, sql)


def verify_probe_route(state, restart_policy='no'):
    candidate = verify_open_blue(state, restart_policy=restart_policy)
    verify_retired_proxy(state)
    proxy = core.container(NGINX)
    if core.identity(proxy, True) != state['proxy_identity']:
        raise ValueError('Public nginx changed; inspect before continuing')
    expected = state['expected_proxy_config_sha256']
    actual = subprocess.run(['docker', 'exec', proxy['Id'], 'cat', '/etc/nginx/conf.d/default.conf'],
                            capture_output=True, check=True, timeout=10).stdout
    if proxy_config_digest() != expected or hashlib.sha256(actual).hexdigest() != expected:
        raise ValueError('Public nginx configuration no longer matches the pinned release')
    core.operations().route('app-blue', 443, 'api.pheeeew.com')
    return candidate


def begin_press_probe(state, device):
    if state['phase'] != 'PUBLIC_CONFIRMED':
        raise ValueError('Public path must be confirmed before preparing a press probe')
    subject = str(UUID(device))
    candidate = verify_probe_route(state)
    observed = press_probe_snapshot(core.environment(candidate), subject)
    expected = {'generation': state['target_generation'], 'enabled': False, 'activation_date': None}
    if ({key: observed[key] for key in expected} != expected
            or not isinstance(observed.get('probe_device_id'), int)
            or not isinstance(observed.get('press_count'), int)
            or observed['press_count'] < 0):
        raise ValueError('Test device or competition state is not eligible')
    day = date.fromisoformat(observed['probe_date']).isoformat()
    verify_probe_route(state)
    state['press_probe'] = {'device': subject, 'device_id': observed['probe_device_id'],
                            'date': day, 'before': observed['press_count'], 'state': 'FRUSTRATED'}
    state['phase'] = 'PRESS_PROBE_PREPARED'
    core.save_state(state)
    print(json.dumps({'phase': state['phase'], 'state': 'FRUSTRATED', 'press_date': day}))


def confirm_press_probe(state):
    if state['phase'] != 'PRESS_PROBE_ATTEMPTED':
        raise ValueError('No recorded single press probe')
    probe = state['press_probe']
    candidate = verify_probe_route(state)
    observed = press_probe_snapshot(core.environment(candidate), probe['device'], probe['date'])
    expected = {'generation': state['target_generation'], 'enabled': False, 'activation_date': None}
    if ({key: observed[key] for key in expected} != expected
            or observed.get('probe_date') != probe['date']
            or observed.get('probe_device_id') != probe['device_id']
            or observed.get('press_count') != probe['before'] + 1
            or not isinstance(observed.get('raw_total'), int)
            or observed.get('projected_total') != observed['raw_total']):
        raise ValueError('Single press outcome is unconfirmed; do not resend it')
    verify_probe_route(state)
    state['phase'] = 'PRESS_PROBE_CONFIRMED'
    core.save_state(state)
    print(json.dumps({'phase': state['phase'], 'press_date': probe['date']}))


def press_probe_token(env, device):
    now = int(time.time())
    header = core.encode(b'{"alg":"RS256","typ":"JWT"}')
    claims = core.encode(json.dumps({'iss': 'https://pheeeew.com', 'sub': str(UUID(device)),
                                     'iat': now, 'exp': now + 60, 'use': 'ACCESS'},
                                    separators=(',', ':')).encode())
    signing = (header + '.' + claims).encode('ascii')
    read_fd, write_fd = os.pipe()
    try:
        os.write(write_fd, base64.b64decode(env['JWT_PRIVATE_KEY_BASE64'], validate=True))
        os.close(write_fd)
        write_fd = None
        signature = subprocess.run(['openssl', 'dgst', '-sha256', '-keyform', 'DER',
                                    '-sign', f'/dev/fd/{read_fd}'], input=signing,
                                   capture_output=True, pass_fds=(read_fd,), timeout=5, check=True).stdout
    finally:
        os.close(read_fd)
        if write_fd is not None:
            os.close(write_fd)
    return signing.decode('ascii') + '.' + core.encode(signature)


def send_press_probe(state):
    if state['phase'] != 'PRESS_PROBE_PREPARED':
        raise ValueError('Single press was not prepared or has already been attempted')
    probe = state['press_probe']
    candidate = verify_probe_route(state)
    env = core.environment(candidate)
    observed = press_probe_snapshot(env, probe['device'], probe['date'])
    expected = {'generation': state['target_generation'], 'enabled': False, 'activation_date': None}
    if ({key: observed[key] for key in expected} != expected
            or observed.get('current_date') != probe['date']
            or observed.get('probe_device_id') != probe['device_id']
            or observed.get('press_count') != probe['before']):
        raise ValueError('Test device changed after preparation; do not send')
    token = press_probe_token(env, probe['device'])
    config = '\n'.join(('url = "https://api.pheeeew.com/api/v2/emotions/presses"',
        f'header = "Authorization: Bearer {token}"', 'header = "Content-Type: application/json"',
        'request = "POST"', r'data = "{\"counts\":{\"FRUSTRATED\":1}}"')) + '\n'
    state['phase'] = 'PRESS_PROBE_ATTEMPTED'
    core.save_state(state)
    try:
        subprocess.run(['curl', '-q', '--noproxy', '*', '--silent', '--show-error', '--max-time', '10',
                        '--output', '/dev/null', '--config', '-'], input=config.encode(),
                       capture_output=True, check=True, timeout=12)
    except (OSError, subprocess.SubprocessError):
        pass  # The HTTP request has no request ID and must never be replayed.
    confirm_press_probe(state)


def confirm_restart_policy(state):
    if state['phase'] != 'RESTART_POLICY_ATTEMPTED':
        raise ValueError('No recorded restart policy change')
    candidate = verify_probe_route(state, restart_policy='unless-stopped')
    if core.identity(core.container(CANDIDATE), True) != core.identity(candidate, True):
        raise ValueError('Blue restarted while confirming restart policy')
    state['phase'] = 'RESTART_POLICY_CONFIRMED'
    core.save_state(state)
    print(json.dumps({'phase': state['phase']}))


def enable_restart_policy(state):
    if state['phase'] != 'PRESS_PROBE_CONFIRMED':
        raise ValueError('Single press must be confirmed before enabling restart')
    candidate = verify_probe_route(state)
    state['phase'] = 'RESTART_POLICY_ATTEMPTED'
    core.save_state(state)
    try:
        subprocess.run(['docker', 'update', '--restart', 'unless-stopped', candidate['Id']],
                       check=True, capture_output=True, timeout=10)
    except (OSError, subprocess.SubprocessError):
        pass
    confirm_restart_policy(state)


def read_regular(path, limit):
    fd = os.open(path, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
    try:
        if not stat.S_ISREG(os.fstat(fd).st_mode):
            raise ValueError('Release artifact is not a regular file')
        with os.fdopen(fd, 'rb', closefd=False) as source:
            content = source.read(limit + 1)
    finally:
        os.close(fd)
    if len(content) > limit:
        raise ValueError('Release artifact exceeds its size limit')
    return content


def verify_release_files(state, expected_hash):
    if not re.fullmatch(r'[0-9a-f]{64}', expected_hash):
        raise ValueError('Expected release manifest SHA-256 is invalid')
    raw = read_regular('.press-release-manifest.json', 65536)
    if hashlib.sha256(raw).hexdigest() != expected_hash:
        raise ValueError('Release manifest differs from the reviewed commit')
    manifest = json.loads(raw)
    if (not isinstance(manifest, dict) or not re.fullmatch(r'[0-9a-f]{40}', manifest.get('source_revision', ''))
            or not isinstance(manifest.get('files'), dict)
            or set(manifest['files']) != set(release_manifest.FILES)):
        raise ValueError('Release manifest is incomplete')
    candidate = verify_probe_route(state, restart_policy='unless-stopped')
    if core.environment(candidate).get('APP_RELEASE') != manifest['source_revision']:
        raise ValueError('Candidate image is not built from the reviewed release commit')
    for path, expected in manifest['files'].items():
        if not isinstance(expected, str) or not re.fullmatch(r'[0-9a-f]{64}', expected):
            raise ValueError('Release artifact digest is invalid')
        if hashlib.sha256(read_regular(path, 2 * 1024 * 1024)).hexdigest() != expected:
            raise ValueError('Installed release artifact does not match the reviewed commit')
    if manifest['files']['nginx/api-bluegreen.conf'] != state['expected_proxy_config_sha256']:
        raise ValueError('Pinned proxy config does not match the release manifest')
    return manifest


def attest_release_files(state, expected_hash):
    if state['phase'] != 'RESTART_POLICY_CONFIRMED':
        raise ValueError('Restart policy must be confirmed before validating release files')
    manifest = verify_release_files(state, expected_hash)
    if manifest['source_revision'] != state.get('reviewed_revision'):
        raise ValueError('Release manifest differs from the reviewed initial image')
    state.update(phase='RELEASE_FILES_CONFIRMED', release_manifest_sha256=expected_hash,
                 release_revision=manifest['source_revision'])
    core.save_state(state)
    print(json.dumps({'phase': state['phase'], 'release_revision': state['release_revision']}))


def verify_monitoring(expected_config_sha256):
    alloy = core.container('pheeeew-prod-alloy')
    labels = alloy['Config'].get('Labels') or {}
    mounts = {item['Destination']: item for item in alloy.get('Mounts', [])}
    config = mounts.get('/etc/alloy/config.alloy') or {}
    if (labels.get('com.docker.compose.project') != 'pheeeew-monitoring'
            or labels.get('com.docker.compose.service') != 'alloy'
            or alloy['Config'].get('Image') != 'grafana/alloy:v1.19.2'
            or alloy['State'].get('Health', {}).get('Status') != 'healthy'
            or alloy['HostConfig']['RestartPolicy']['Name'] != 'unless-stopped'
            or set(alloy['NetworkSettings'].get('Networks') or {}) != {'pheeeew-prod_default'}
            or config.get('Type') != 'bind' or config.get('RW') is not False
            or config.get('Source') != '/opt/pheeeew/alloy/config.prod-bluegreen.alloy'):
        raise ValueError('Production Alloy is not healthy or does not use the expected configuration')
    for color in ('blue', 'green'):
        mount = mounts.get('/var/log/pheeeew-' + color) or {}
        if (mount.get('Type') != 'volume' or mount.get('RW') is not False
                or mount.get('Name') != 'pheeeew-prod_app-logs-' + color):
            raise ValueError('Production Alloy does not have both color log volumes')
    config_content = subprocess.run(['docker', 'exec', alloy['Id'], 'cat', '/etc/alloy/config.alloy'],
                                    check=True, capture_output=True, timeout=10).stdout
    if hashlib.sha256(config_content).hexdigest() != expected_config_sha256:
        raise ValueError('Running Alloy does not use the reviewed configuration')
    command = r'''set -eu
test -r /var/log/pheeeew-blue/application.json
test -r /var/log/pheeeew-green/application.json
exec 3<>/dev/tcp/app-blue/8080
printf 'GET /actuator/prometheus HTTP/1.0\r\nHost: app-blue\r\n\r\n' >&3
IFS= read -r response <&3
case "$response" in *" 200 "*) ;; *) exit 1;; esac
'''
    subprocess.run(['docker', 'exec', alloy['Id'], 'bash', '-ec', command],
                   check=True, capture_output=True, timeout=10)
    if core.identity(core.container('pheeeew-prod-alloy'), True) != core.identity(alloy, True):
        raise ValueError('Production Alloy restarted during verification')


def finalize_bootstrap(state):
    if state['phase'] != 'RELEASE_FILES_CONFIRMED' or os.path.lexists(str(core.STATE_FILE) + '.new'):
        raise ValueError('Release files or durable journal are not ready')
    manifest = verify_release_files(state, state['release_manifest_sha256'])
    if manifest['source_revision'] != state['release_revision']:
        raise ValueError('Release revision changed during final validation')
    verify_monitoring(manifest['files']['alloy/config.prod-bluegreen.alloy'])
    candidate = verify_probe_route(state, restart_policy='unless-stopped')
    probe = state['press_probe']
    observed = press_probe_snapshot(core.environment(candidate), probe['device'], probe['date'])
    expected = {'generation': state['target_generation'], 'enabled': False, 'activation_date': None}
    if ({key: observed[key] for key in expected} != expected
            or observed.get('probe_device_id') != probe['device_id']
            or observed.get('probe_date') != probe['date']
            or not isinstance(observed.get('press_count'), int)
            or observed['press_count'] < probe['before'] + 1
            or not isinstance(observed.get('raw_total'), int)
            or observed.get('projected_total') != observed['raw_total']):
        raise ValueError('Recorded test press or its projection no longer matches')
    if core.probe_generation(core.environment(candidate), ['curl'],
            'https://api.pheeeew.com/api/v1/press-race', required_code='DEVICE_NOT_FOUND') != state['target_generation']:
        raise ValueError('Public authenticated WebSocket did not reach the recorded owner')
    verify_probe_route(state, restart_policy='unless-stopped')
    state['phase'] = 'FINALIZED'
    core.save_state(state)
    print(json.dumps({'phase': state['phase'], 'release_revision': state['release_revision']}))


def open_candidate(state):
    ensure_writer_isolation(state)
    if state['phase'] != 'OWNER_CONFIRMED':
        raise ValueError('Candidate OPEN requires confirmed ownership')
    expected_config = state.get('expected_proxy_config_sha256', '')
    if not re.fullmatch(r'[0-9a-f]{64}', expected_config) or proxy_config_digest() != expected_config:
        raise ValueError('Reviewed nginx configuration is not pinned')
    env = checked_candidate(state)
    if core.read_state(env) != {'generation': state['target_generation'], 'enabled': False, 'activation_date': None}:
        raise ValueError('Candidate ownership or competition status changed')
    candidate = core.container(CANDIDATE)
    require_isolated_candidate(candidate)
    candidate_env = core.environment(candidate)
    if (core.identity(candidate, True) != state['candidate_identity']
            or candidate['Image'] != state['candidate_image']
            or candidate_env.get(core.GENERATION_KEY) != state['target_generation']
            or list(core.db_target(candidate_env)) != state['database']
            or core.operations().gate(candidate) != state['candidate_gate']):
        raise ValueError('Candidate changed before OPEN intent')
    stopped_legacy(state)
    replacement = f"OPEN {uuid4()} {state['target_generation']}\n"
    state['gate_commands'] = [dict(purpose='bootstrap-open', container_id=candidate['Id'],
                                   expected=state['candidate_gate'], replacement=replacement)]
    state['phase'] = 'OPEN_ATTEMPTED'
    core.save_state(state)
    # The exact intent is durable before the CAS. Never invoke this action twice.
    current = core.container(CANDIDATE)
    require_isolated_candidate(current)
    if (core.identity(current, True) != state['candidate_identity']
            or core.operations().gate(current) != state['candidate_gate']):
        raise ValueError('Candidate changed before OPEN CAS; preserve the recorded intent')
    core.operations().set_gate(state, candidate, 'OPEN', 'bootstrap-open')
    confirm_open(state)


def abort_open(state):
    if state['phase'] not in ('OPEN_ATTEMPTED', 'ABORT_ATTEMPTED'):
        raise ValueError('No uncertain OPEN attempt to close')
    commands = [item for item in state.get('gate_commands', []) if item['purpose'] == 'bootstrap-open']
    if len(commands) != 1:
        raise ValueError('OPEN intent is missing or ambiguous')
    candidate = core.container(CANDIDATE)
    require_isolated_candidate(candidate)
    if core.identity(candidate, True) != state['candidate_identity'] or candidate['Image'] != state['candidate_image']:
        raise ValueError('Candidate changed; preserve the journal for manual recovery')
    ops = core.operations()
    previous = ops.gate(candidate)
    opened = commands[0]
    aborts = [item for item in state['gate_commands'] if item['purpose'].startswith('bootstrap-abort-')]
    if (opened['container_id'] != candidate['Id'] or opened['expected'] != state['candidate_gate']
            or any(item['container_id'] != candidate['Id'] for item in aborts)):
        raise ValueError('Admission is not a recorded OPEN state')
    recorded_closed = [item['replacement'] for item in aborts]
    if previous in recorded_closed:
        closed = previous
    elif previous in (opened['expected'], opened['replacement']):
        purpose = 'bootstrap-abort-' + str(len(aborts) + 1)
        replacement = f"CLOSED {uuid4()} {state['target_generation']}\n"
        state['gate_commands'].append(dict(purpose=purpose, container_id=candidate['Id'],
                                           expected=previous, replacement=replacement))
        state['phase'] = 'ABORT_ATTEMPTED'
        core.save_state(state)
        # A new CLOSED revision invalidates a delayed OPEN CAS using the old revision.
        closed = ops.set_gate(state, candidate, 'CLOSED', purpose)
    else:
        raise ValueError('Admission is not a recorded OPEN or CLOSED state')
    ops.wait_closed(candidate, closed)
    current = core.container(CANDIDATE)
    require_isolated_candidate(current)
    if core.identity(current, True) != state['candidate_identity'] or ops.gate(current) != closed:
        raise ValueError('Candidate changed during OPEN cancellation')
    stopped_legacy(state)
    state['phase'] = 'ABORT_CLOSED'
    core.save_state(state)
    print(json.dumps({'phase': state['phase']}))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=('prepare', 'verify-drained', 'record-candidate',
                                           'assign-owner', 'confirm-owner', 'open-candidate', 'confirm-open', 'abort-open',
                                           'attest-quiescence', 'write-blue-environment', 'check-blue-configuration', 'attest-image-release',
                                           'retire-proxy', 'confirm-proxy-retired', 'confirm-proxy-live',
                                           'attest-proxy-config', 'begin-press-probe', 'send-press-probe', 'confirm-press-probe',
                                           'enable-restart-policy', 'confirm-restart-policy',
                                           'attest-release-files', 'finalize-bootstrap'))
    parser.add_argument('parameter', nargs='?')
    args = parser.parse_args()
    if (args.action in ('prepare', 'attest-quiescence', 'attest-proxy-config',
                        'begin-press-probe', 'attest-release-files', 'attest-image-release')) != (args.parameter is not None):
        parser.error('This action requires one parameter, or does not accept one')
    # Inherited libpq settings must not redirect a check away from the recorded DB.
    for name in list(os.environ):
        if name.startswith('PG'):
            del os.environ[name]
    try:
        if Path.cwd() != Path('/opt/pheeeew'):
            raise ValueError('Bootstrap must run from the production state directory')
        with open('.press-prod-bootstrap.lock', 'a') as lock:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
            if args.action == 'prepare':
                token = 'bootstrap-' + str(uuid4())
                operations_lock.acquire(token)
                prepare(args.parameter, token)
            else:
                state = json.loads(core.STATE_FILE.read_text())
                with operations_lock.mutex():
                    operations_lock.check_owner(state['lock_token'])
                if args.action == 'verify-drained':
                    verify_drained()
                elif args.action == 'record-candidate':
                    record_candidate()
                elif args.action == 'assign-owner':
                    assign_owner(state)
                elif args.action == 'confirm-owner':
                    confirm_owner(state)
                elif args.action == 'open-candidate':
                    open_candidate(state)
                elif args.action == 'attest-quiescence':
                    attest_quiescence(state, args.parameter)
                elif args.action == 'write-blue-environment':
                    write_blue_environment(state)
                elif args.action == 'check-blue-configuration':
                    check_blue_configuration(state)
                elif args.action == 'attest-image-release':
                    attest_image_release(state, args.parameter)
                elif args.action == 'attest-proxy-config':
                    attest_proxy_config(state, args.parameter)
                elif args.action == 'retire-proxy':
                    retire_proxy(state)
                elif args.action == 'confirm-proxy-retired':
                    confirm_proxy_retired(state)
                elif args.action == 'confirm-proxy-live':
                    confirm_proxy_live(state)
                elif args.action == 'begin-press-probe':
                    begin_press_probe(state, args.parameter)
                elif args.action == 'send-press-probe':
                    send_press_probe(state)
                elif args.action == 'confirm-press-probe':
                    confirm_press_probe(state)
                elif args.action == 'enable-restart-policy':
                    enable_restart_policy(state)
                elif args.action == 'confirm-restart-policy':
                    confirm_restart_policy(state)
                elif args.action == 'attest-release-files':
                    attest_release_files(state, args.parameter)
                elif args.action == 'finalize-bootstrap':
                    finalize_bootstrap(state)
                elif args.action == 'confirm-open':
                    confirm_open(state)
                else:
                    abort_open(state)
        return 0
    except (ValueError, KeyError, OSError, subprocess.SubprocessError, json.JSONDecodeError):
        print('Bootstrap preparation refused; inspect the existing app, image and database state.', file=sys.stderr)
        return 1


if __name__ == '__main__':
    sys.exit(main())
