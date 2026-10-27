#!/usr/bin/env python3
"""Explicit development deployment fencing; never runs during app startup."""

import argparse
import base64
import fcntl
from datetime import date
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import selectors
import subprocess
import sys
import time
from types import SimpleNamespace
from urllib.parse import parse_qs, urlsplit
from uuid import UUID, uuid4

GENERATION_KEY = 'PHEEEEW_PRESS_RACE_SERVER_GENERATION'
CONTRACT_LABEL = 'com.pheeeew.press-generation-contract'
STATE_FILE = Path('.press-deploy.json')
ENV_FILE = Path('press-generations.env')
READ_STATE = """SELECT json_build_object('generation', active_server_generation,
    'enabled', enabled, 'activation_date', activation_date)
    FROM press_race_control WHERE id = TRUE;"""


def container(name: str, require_running: bool = True) -> dict:
    result = subprocess.run(['docker', 'inspect', name], capture_output=True, text=True, check=True)
    inspected = json.loads(result.stdout)[0]
    if require_running and not inspected['State']['Running']:
        raise ValueError('Container is not running')
    return inspected


def environment(inspected: dict) -> dict:
    return dict(item.split('=', 1) for item in inspected['Config']['Env'] if '=' in item)


def identity(inspected: dict, legacy: bool = False) -> dict:
    result = {'id': inspected['Id'], 'image': inspected['Image']}
    if legacy or (inspected['Config'].get('Labels') or {}).get(CONTRACT_LABEL) == '2':
        result.update(started_at=inspected['State']['StartedAt'], restarts=inspected['RestartCount'])
    return result


def encode(value: bytes) -> str:
    return base64.urlsafe_b64encode(value).rstrip(b'=').decode('ascii')


def probe_output(command: list[str], config: bytes, key: str) -> str:
    # Bound collected evidence, not the lifetime traffic of the upgraded connection.
    process = subprocess.Popen(command, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)
    try:
        process.stdin.write(config)
        process.stdin.close()
        output = bytearray()
        deadline = time.monotonic() + 5
        with selectors.DefaultSelector() as selector:
            selector.register(process.stdout, selectors.EVENT_READ)
            while True:
                remaining = deadline - time.monotonic()
                if remaining <= 0 or not selector.select(remaining):
                    raise ValueError('Runtime generation probe timed out')
                chunk = os.read(process.stdout.fileno(), min(4096, 65537 - len(output)))
                if not chunk:
                    if process.wait(timeout=max(0.01, deadline - time.monotonic())) not in (0, 28):
                        raise ValueError('Runtime generation probe failed')
                    raise ValueError('Incomplete generation probe response')
                output.extend(chunk)
                if len(output) > 65536:
                    raise ValueError('Runtime generation probe exceeded response limit')
                generation = probe_response(bytes(output), key)
                if generation is not None:
                    if process.poll() not in (None, 0, 28):
                        raise ValueError('Runtime generation probe failed')
                    return generation
    finally:
        # Reap our local client only. docker-exec cancellation does not prove that
        # remote curl stopped; its unchanged 3-second limit bounds the read-only probe.
        if process.poll() is None:
            process.kill()
        process.wait()
        process.stdout.close()


def probe_generation(env: dict, curl_command: list[str], url: str = 'http://127.0.0.1:8080/api/v1/press-race') -> str:
    """Read the runtime UUID via the existing nonexistent-device error; credentials use stdin/FD only."""
    subject = str(uuid4())
    absent = READ_STATE.replace("json_build_object(",
        f"json_build_object('probe_absent', NOT EXISTS (SELECT 1 FROM devices WHERE public_id = '{subject}'::uuid), ")
    if not query(env, 'BEGIN READ ONLY;\n' + absent + '\nCOMMIT;')['probe_absent']:
        raise ValueError('Probe device unexpectedly exists')
    now = int(time.time())
    header = encode(b'{"alg":"RS256","typ":"JWT"}')
    claims = encode(json.dumps({'iss': 'https://pheeeew.com', 'sub': subject, 'iat': now,
                                'exp': now + 15, 'use': 'ACCESS'}, separators=(',', ':')).encode())
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
    token = signing.decode('ascii') + '.' + encode(signature)
    key = base64.b64encode(os.urandom(16)).decode('ascii')
    config = '\n'.join((f'url = "{url}"', f'header = "Authorization: Bearer {token}"',
                        'header = "Connection: Upgrade"', 'header = "Upgrade: websocket"',
                        'header = "Sec-WebSocket-Version: 13"', f'header = "Sec-WebSocket-Key: {key}"')) + '\n'
    # -q must be curl's first argument: never load an operator's curlrc or proxy credentials.
    return probe_output(curl_command + ['-q', '--noproxy', '*', '--http1.1', '--include',
                          '--silent', '--no-buffer', '--max-time', '3', '--max-filesize', '65536',
                          '--config', '-'], config.encode(), key)


def probe_response(output: bytes, key: str) -> str | None:
    """None means incomplete transport data; malformed complete evidence fails closed."""
    headers, separator, frames = output.partition(b'\r\n\r\n')
    if not separator:
        return None
    lines = headers.split(b'\r\n')
    expected = base64.b64encode(hashlib.sha1((key + '258EAFA5-E914-47DA-95CA-C5AB0DC85B11').encode()).digest())
    fields = {name.lower(): value.strip() for line in lines[1:] if b':' in line
              for name, value in [line.split(b':', 1)]}
    if (not lines[0].startswith(b'HTTP/1.1 101 ')
            or fields.get(b'sec-websocket-accept') != expected
            or fields.get(b'upgrade', b'').lower() != b'websocket'
            or b'upgrade' not in fields.get(b'connection', b'').lower().split(b', ')):
        raise ValueError('Invalid generation probe upgrade')
    if len(frames) < 2:
        return None
    if frames[0] != 0x81 or frames[1] & 0x80:
        raise ValueError('Invalid generation probe frame')
    length, offset = frames[1], 2
    if length == 126:
        if len(frames) < 4:
            return None
        length, offset = int.from_bytes(frames[2:4], 'big'), 4
    elif length == 127:
        raise ValueError('Unsupported generation probe frame')
    if len(headers) + 4 + offset + length > 65536:
        raise ValueError('Runtime generation probe exceeded response limit')
    if len(frames) < offset + length:
        return None
    response = json.loads(frames[offset:offset + length].decode('utf-8'))
    if (not isinstance(response, dict) or response.get('v') != 1 or response.get('type') != 'protocol.error'
            or response.get('code') not in ('DEVICE_NOT_FOUND', 'SERVER_DRAINING')):
        raise ValueError('Unexpected generation probe response')
    if not isinstance(response.get('serverGeneration'), str):
        raise ValueError('Invalid generation probe UUID')
    return str(UUID(response['serverGeneration']))


def runtime_generation(inspected: dict) -> str:
    return probe_generation(environment(inspected), ['docker', 'exec', '-i', inspected['Id'], 'curl'])


def check_instance(name: str, snapshot: dict, generation: str | None, legacy: bool = False) -> dict:
    inspected = container(name)
    if identity(inspected, legacy) != snapshot:
        raise ValueError('Prepared container changed; keep both servers for manual recovery')
    if generation is not None and runtime_generation(inspected) != generation:
        raise ValueError('Runtime generation does not match the prepared owner')
    if identity(container(name), legacy) != snapshot:
        raise ValueError('Container restarted during generation verification')
    return inspected


def save_state(state: dict) -> None:
    descriptor = os.open(str(STATE_FILE) + '.new', os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(descriptor, 'w') as output:
        json.dump(state, output)
        output.flush()
        os.fsync(output.fileno())
    os.replace(str(STATE_FILE) + '.new', STATE_FILE)
    directory = os.open(str(STATE_FILE.parent), os.O_RDONLY)
    try:
        os.fsync(directory)
    finally:
        os.close(directory)


def operations():
    sys.dont_write_bytecode = True
    spec = importlib.util.spec_from_file_location('press_operations', Path(__file__).with_name('press-operations.py'))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module.Operations(SimpleNamespace(**globals()))


def record_target(target_name: str) -> None:
    state = json.loads(STATE_FILE.read_text())
    expected_name = state['active_container'].rsplit('-', 1)[0] + '-' + state['target_slot'].removeprefix('app-')
    if target_name != expected_name:
        raise ValueError('Unexpected candidate container name')
    inspected = container(target_name, require_running=False)
    env = environment(inspected)
    if (env.get(GENERATION_KEY) != state['target_generation']
            or (inspected['Config'].get('Labels') or {}).get(CONTRACT_LABEL) not in ('1', '2')
            or list(db_target(env)) != state['database']):
        raise ValueError('Cannot identify the prepared candidate; preserve it for manual inspection')
    snapshot = identity(inspected)
    if 'target_identity' in state and snapshot != state['target_identity']:
        raise ValueError('Prepared candidate was replaced; preserve both containers')
    state.update(target_identity=snapshot, target_process_identity=identity(inspected, True), target_container=target_name)
    # Even a rejected candidate keeps its immutable identity for explicit safe cleanup.
    save_state(state)
    operations().record_target(state, inspected)
    save_state(state)


def check_target(state: dict, target_name: str) -> dict:
    if target_name != state['target_container']:
        raise ValueError('Unexpected candidate container')
    inspected = container(target_name, require_running=False)
    if identity(inspected) != state['target_identity']:
        raise ValueError('Prepared candidate was replaced; preserve both containers')
    return inspected


def cleanup(action: str, container_name: str | None = None) -> None:
    state = json.loads(STATE_FILE.read_text())
    expected_name = state['target_container'] if action == 'cleanup-target' else state['active_container']
    if container_name is not None and container_name != expected_name:
        raise ValueError('Cleanup target does not match the recorded container')
    if state.get('operations'):
        return operations().cleanup(action, state)
    retiring_target = action == 'cleanup-target'
    if retiring_target:
        retired = check_target(state, state['target_container'])
        owner = check_instance(state['active_container'], state['active_identity'], state['generation'], state['legacy'])
        expected = state['generation']
    else:
        retired = container(state['active_container'], require_running=False)
        if identity(retired, state['legacy']) != state['active_identity']:
            raise ValueError('Original container changed; preserve both containers')
        owner = check_instance(state['target_container'], state['target_identity'], state['target_generation'])
        expected = state['target_generation']
    control = read_state(environment(owner))
    if control != dict(generation=expected, enabled=False, activation_date=state['activation_date']):
        raise ValueError('Ownership changed before cleanup; preserve both containers')
    # Never stop/remove by service name: another process might replace that name after inspection.
    subprocess.run(['docker', 'stop', retired['Id']], check=True, capture_output=True, timeout=40)
    subprocess.run(['docker', 'rm', retired['Id']], check=True, capture_output=True, timeout=10)


def db_target(env: dict) -> tuple:
    url = urlsplit(env['DB_URL'].removeprefix('jdbc:'))
    if url.scheme != 'postgresql' or not url.hostname or not url.path.strip('/'):
        raise ValueError('Expected a PostgreSQL JDBC URL')
    return url.hostname, url.port or 5432, url.path.strip('/'), env['DB_USERNAME']


def query(env: dict, sql: str) -> dict:
    host, port, database, user = db_target(env)
    url = urlsplit(env['DB_URL'].removeprefix('jdbc:'))
    process_env = dict(os.environ, PGHOST=host, PGPORT=str(port), PGDATABASE=database,
                       PGUSER=user, PGPASSWORD=env['DB_PASSWORD'], PGCONNECT_TIMEOUT='5',
                       PGSSLMODE=parse_qs(url.query).get('sslmode', ['require'])[0],
                       PGOPTIONS='-c lock_timeout=5000 -c statement_timeout=8000')
    result = subprocess.run(['psql', '-X', '-qAt', '--no-password', '-v', 'ON_ERROR_STOP=1'],
                            input=sql, text=True, capture_output=True, env=process_env, timeout=15)
    if result.returncode:
        # Diagnostics may contain credentials. Re-read after any failure, including a lost COMMIT response.
        raise ValueError('Database operation failed; its outcome must be rechecked')
    state = json.loads(result.stdout.strip())
    if not isinstance(state.get('enabled'), bool):
        raise ValueError('Invalid server control row')
    if state['generation'] is not None:
        UUID(state['generation'])
    return state


def read_state(env: dict) -> dict:
    return query(env, 'BEGIN READ ONLY;\n' + READ_STATE + '\nCOMMIT;')


def change(env: dict, expected: str | None, replacement: str | None, activation: str | None) -> None:
    before = 'NULL' if expected is None else "'" + str(UUID(expected)) + "'::uuid"
    after = 'NULL' if replacement is None else "'" + str(UUID(replacement)) + "'::uuid"
    day = 'NULL' if activation is None else "'" + date.fromisoformat(activation).isoformat() + "'::date"
    query(env, f"""BEGIN;
        DO $fence$ BEGIN
            UPDATE press_race_control SET active_server_generation = {after}
            WHERE id = TRUE AND NOT enabled
              AND activation_date IS NOT DISTINCT FROM {day}
              AND active_server_generation IS NOT DISTINCT FROM {before};
            IF NOT FOUND THEN RAISE EXCEPTION 'Deployment state changed'; END IF;
        END $fence$;
        {READ_STATE}
        COMMIT;""")


def prepare(active_name: str, target_slot: str, allow_legacy: bool = False, probe_port: int = 443) -> None:
    if target_slot not in ('app-blue', 'app-green'):
        raise ValueError('Invalid deployment slot')
    if (not active_name.endswith(('-app-blue', '-app-green'))
            or active_name.endswith('-' + target_slot)):
        raise ValueError('The candidate must use the opposite slot of the named active container')
    active = container(active_name)
    env = environment(active)
    state = read_state(env)
    version = (active['Config'].get('Labels') or {}).get(CONTRACT_LABEL)
    if state['enabled'] and version != '2':
        raise ValueError('Competition is ON; this deployment path only supports OFF')
    active_generation = env.get(GENERATION_KEY) or None
    labeled = version in ('1', '2')
    legacy = state['generation'] is not None and not active_generation and not labeled
    if active_generation:
        UUID(active_generation)
    if state['generation'] is not None:
        if legacy and allow_legacy:
            active_generation = state['generation']
        elif active_generation != state['generation'] or not labeled:
            raise ValueError('Active container and DB generation do not match the supported contract')
    snapshot = identity(active, legacy)
    check_instance(active_name, snapshot, state['generation'], legacy)
    if read_state(env) != state:
        raise ValueError('Control changed while preparing deployment')
    state.update(target_generation=str(uuid4()), database=list(db_target(env)),
                 active_container=active_name, target_slot=target_slot,
                 legacy=legacy, active_identity=snapshot, probe_port=probe_port,
                 active_contract=version, active_process_identity=identity(active, True),
                 phase='PREPARED', operations=version == '2')
    operations().prepare(state, active)
    descriptor = os.open(STATE_FILE, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(descriptor, 'w') as output:
        # UUIDs and DB identity only; never credentials. A stale journal requires manual inspection.
        json.dump(state, output)
        output.flush()
        os.fsync(output.fileno())
    directory = os.open(str(STATE_FILE.parent), os.O_RDONLY)
    try:
        os.fsync(directory)
    finally:
        os.close(directory)
    values = {'PRESS_BLUE_GENERATION': '', 'PRESS_GREEN_GENERATION': ''}
    target_key = 'PRESS_BLUE_GENERATION' if target_slot == 'app-blue' else 'PRESS_GREEN_GENERATION'
    active_key = 'PRESS_GREEN_GENERATION' if target_slot == 'app-blue' else 'PRESS_BLUE_GENERATION'
    values[target_key] = state['target_generation']
    # A legacy process has no stable configuration. Never suggest it can be recreated with its old UUID.
    values[active_key] = '' if legacy else (active_generation or '')
    descriptor = os.open(ENV_FILE, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(descriptor, 'w') as output:
        output.write(''.join(f'{key}={value}\n' for key, value in values.items()))


def transition(action: str, target_name: str) -> None:
    state = json.loads(STATE_FILE.read_text())
    if state.get('operations'):
        return operations().transition(action, target_name, state)
    legacy = state['legacy']
    old, new = state['generation'], state['target_generation']
    if action in ('handoff', 'restore'):
        check_instance(state['active_container'], state['active_identity'], old, legacy)
    check_target(state, target_name)
    # Recovery must remain possible if the newly promoted container crashed.
    inspected = container(state['active_container'] if action == 'restore' else target_name)
    env = environment(inspected)
    if list(db_target(env)) != state['database']:
        raise ValueError('Old and new database targets differ')
    expected = state['generation'] if action == 'restore' else state['target_generation']
    legacy_restore = action == 'restore' and legacy
    if expected is not None and not legacy_restore and env.get(GENERATION_KEY) != expected:
        raise ValueError('Target generation does not match the prepared instance')
    if not legacy_restore and (action != 'restore' or expected is not None) and (inspected['Config'].get('Labels') or {}).get(CONTRACT_LABEL) not in ('1', '2'):
        raise ValueError('Target image does not support generation fencing')
    if action != 'restore':
        snapshot = identity(inspected)
        check_instance(target_name, snapshot, new)
        if action == 'handoff':
            if snapshot != state['target_identity']:
                raise ValueError('Prepared candidate was replaced unexpectedly')
        elif snapshot != state['target_identity']:
            raise ValueError('Promoted container was replaced unexpectedly')
    current = read_state(env)
    if current['enabled'] or current['activation_date'] != state['activation_date']:
        raise ValueError('Competition state changed; manual recovery is required')
    if action == 'handoff':
        change(env, old, new, state['activation_date'])
        check_instance(state['active_container'], state['active_identity'], old, legacy)
    elif action == 'restore':
        if current['generation'] == new:
            change(env, new, old, state['activation_date'])
        elif current['generation'] != old:
            raise ValueError('Unexpected generation; refusing to overwrite another owner')
        if read_state(env) != {key: state[key] for key in ('generation', 'enabled', 'activation_date')}:
            raise ValueError('Restoration could not be verified')
        check_instance(state['active_container'], state['active_identity'], old, legacy)
        if old is None:
            print('Restored initial NULL generation; old HTTP input remains unavailable.', file=sys.stderr)
        elif legacy:
            print('Restored the same legacy process; restart resilience still requires migration.', file=sys.stderr)
    elif action == 'verify':
        if current['generation'] != new:
            raise ValueError('New owner could not be verified')


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=('prepare', 'record-target', 'handoff', 'restore', 'verify',
        'cleanup-target', 'cleanup-active', 'close-active', 'open-target', 'open-active',
        'emergency-stop', 'status', 'activate', 'stop', 'resume-off', 'reconcile-control', 'finalize'))
    parser.add_argument('container')
    parser.add_argument('target_slot', nargs='?')
    parser.add_argument('--allow-legacy', action='store_true', help='Explicit first migration of the verified running legacy owner')
    parser.add_argument('--probe-port', type=int, default=443, help='Local HTTPS port, normally 443')
    parser.add_argument('--expected-generation')
    parser.add_argument('--expected-activation-date', help='Exact current date or null')
    parser.add_argument('--activation-date', help=argparse.SUPPRESS)
    args = parser.parse_args()
    lock = None
    try:
        if args.action != 'status':
            lock = open('.press-operations.lock', 'a')
            try:
                fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
            except BlockingIOError:
                raise ValueError('Another press operation is running; inspect before retrying')
        if not 1 <= args.probe_port <= 65535:
            raise ValueError('Invalid local HTTPS port')
        if args.action == 'prepare':
            prepare(args.container, args.target_slot, args.allow_legacy, args.probe_port)
        elif args.action == 'record-target':
            record_target(args.container)
        elif args.action.startswith('cleanup-'):
            cleanup(args.action, args.container)
        elif args.action in ('status', 'activate', 'stop', 'resume-off', 'reconcile-control'):
            operations().control(args)
        elif args.action in ('close-active', 'open-target', 'open-active', 'emergency-stop', 'finalize'):
            operations().action(args.action, args.container)
        else:
            transition(args.action, args.container)
        return 0
    except ValueError as error:
        print(str(error), file=sys.stderr)
        return 1
    except (KeyError, OSError, subprocess.SubprocessError):
        print('Generation operation failed. Keep both containers; check DB and proxy state before retrying.', file=sys.stderr)
        return 1
    finally:
        if lock is not None:
            lock.close()


if __name__ == '__main__':
    sys.exit(main())
