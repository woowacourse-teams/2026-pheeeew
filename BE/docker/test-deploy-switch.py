#!/usr/bin/env python3
import os
import base64
import json
import signal
import shutil
import subprocess
import sys
import tempfile
import time
import uuid
from contextlib import contextmanager
from pathlib import Path

import yaml

REPO = Path(__file__).resolve().parents[2]
WORKFLOW = REPO / '.github/workflows/be-dev-deploy.yml'
HTTP_PORT = 18080
HTTPS_PORT = 18443
PROBE_PATH = '/api/v3/devices/nicknames/availability?nickname=probe'
SLOW_PATH = '/api/v3/devices/slow'
COLORS = ('app-blue', 'app-green')


def remote_script():
    workflow = yaml.safe_load(WORKFLOW.read_text())
    steps = workflow['jobs']['deploy']['steps']
    app = next(s for s in steps if s.get('name') == '배포')
    finish = next(s for s in steps if s.get('name') == '배포 마무리')
    expect(steps.index(finish) > steps.index(next(s for s in steps if s.get('name') == 'Alloy 임시 환경 파일 삭제')),
           '마지막 확인이 모니터링 배포/환경 정리보다 먼저 실행된다')
    expect(finish.get('if', 'success()') == 'success()', '앞 단계 실패 후에도 마지막 확인이 실행된다')
    remote = lambda step: step['run'].split("<<'REMOTE'\n", 1)[1].rsplit('\nREMOTE', 1)[0]
    # The monitoring boundary is a controlled external failure; the two real remote scripts run unchanged.
    return remote(app) + '\n[ ! -f "$SHIM_DIR/fail-monitoring" ] || exit 1\n' + remote(finish)


APP = '''
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse, parse_qs
import json
import os
import sys
import time
import uuid
import threading

name = sys.argv[1]
marker = '/appstate/inflight-' + name
badapi = '/appstate/badapi-' + name
gate = os.environ.get('PHEEEEW_PRESS_RACE_ADMISSION_FILE')
if gate and not os.path.exists(gate):
    os.makedirs(os.path.dirname(gate), exist_ok=True)
    with open(gate, 'x') as output:
        output.write('CLOSED ' + str(uuid.uuid4()) + ' ' + os.environ['PHEEEEW_PRESS_RACE_SERVER_GENERATION'] + '\\n')

def acknowledge_closed():
    while gate:
        try:
            value = open(gate).read()
            if value.startswith('CLOSED '):
                with open(gate + '.closed', 'w') as output:
                    output.write(value)
        except OSError:
            pass
        time.sleep(0.02)

threading.Thread(target=acknowledge_closed, daemon=True).start()

class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        parsed = urlparse(self.path)
        if parsed.path.startswith('/api/') and os.path.exists(badapi):
            if open(badapi).read() == 'stall':
                time.sleep(90)
            self.send_response(503)
            self.end_headers()
            return
        slow = parsed.path.endswith('/slow')
        if slow:
            seconds = int(parse_qs(parsed.query).get('seconds', ['20'])[0])
            open(marker, 'w').close()
            time.sleep(seconds)
        self.send_response(200)
        self.send_header('Content-Type', 'application/json')
        self.end_headers()
        self.wfile.write(json.dumps({'target': name, 'available': True}).encode())
        if slow:
            try:
                os.remove(marker)
            except OSError:
                pass

    def log_message(self, *args):
        pass

ThreadingHTTPServer(('0.0.0.0', 8080), Handler).serve_forever()
'''

DOCKER_SHIM = '''#!/bin/sh
for arg in "$@"; do
  [ "$arg" = prune ] && exit 0
done
case "$*" in
  'rm '*)
    if [ -f "$SHIM_DIR/lose-active-rm-response" ] && [ "$2" = "$("$REAL_DOCKER" inspect -f '{{.Id}}' "$GENERATION_ACTIVE")" ]; then
      rm -f "$SHIM_DIR/lose-active-rm-response"
      "$REAL_DOCKER" "$@" || exit $?
      exit 1
    fi ;;
  'container ls '*)
    [ -f "$SHIM_DIR/fail-retirement-query" ] && exit 1 ;;
  'stop '*)
    [ -f "$SHIM_DIR/fail-active-cleanup" ] && [ "$2" = "$("$REAL_DOCKER" inspect -f '{{.Id}}' "$GENERATION_ACTIVE")" ] && exit 1 ;;
  *' cat /app/press-admission'*)
    [ -f "$SHIM_DIR/fail-gate-read" ] && exit 1 ;;
esac
case "$*" in
  *' curl -q '*)
    # Only the probe transport is stubbed. Identity comes from real Docker inspect;
    # actual RSA signing/upgrade/frame handling is also checked against Spring in Java tests.
    "$SHIM_DIR/../probe-transport" "$3"
    exit $? ;;
  'image inspect '*)
    [ -f "$SHIM_DIR/fail-image-inspect" ] && exit 1
    if [ -f "$SHIM_DIR/old-image" ]; then echo missing
    elif [ -f "$SHIM_DIR/v2-image" ]; then echo 2
    else echo 1; fi
    exit 0 ;;
  *' pull '*)
    [ -f "$SHIM_DIR/fail-image-pull" ] && exit 1 ;;
  *COUNTED*)
    if [ -f "$SHIM_DIR/replace-active-before-cleanup" ]; then
      rm -f "$SHIM_DIR/replace-active-before-cleanup"
      "$REAL_DOCKER" rm -f "$GENERATION_ACTIVE" >/dev/null
      "$REAL_DOCKER" compose -f compose.yml up -d app-blue >/dev/null
    fi
    [ -f "$SHIM_DIR/fail-drain" ] && exit 1 ;;
  *"nginx -s reload"*)
    if [ -f "$SHIM_DIR/delay-restore-reload" ] && grep -q 'app-blue;' "$SHIM_POINTER"; then
      rm -f "$SHIM_DIR/delay-restore-reload"
      "$REAL_DOCKER" exec "$GENERATION_NGINX" sh -c 'kill -STOP "$(cat /var/run/nginx.pid)"'
      ( sleep 2; "$REAL_DOCKER" exec "$GENERATION_NGINX" sh -c 'kill -CONT "$(cat /var/run/nginx.pid)"' ) >/dev/null 2>&1 </dev/null &
    fi
    if [ -f "$SHIM_DIR/replace-target-on-reload" ]; then
      rm -f "$SHIM_DIR/replace-target-on-reload"
      "$REAL_DOCKER" rm -f "$GENERATION_TARGET" >/dev/null
      "$REAL_DOCKER" compose --env-file .env --env-file press-generations.env -f compose.yml up -d app-green >/dev/null
      exit 1
    fi
    if [ -f "$SHIM_DIR/restart-active-on-reload" ]; then
      rm -f "$SHIM_DIR/restart-active-on-reload"
      "$REAL_DOCKER" restart "$GENERATION_ACTIVE" >/dev/null
      exit 1
    fi
    if [ -f "$SHIM_DIR/replace-active-on-reload" ]; then
      rm -f "$SHIM_DIR/replace-active-on-reload"
      "$REAL_DOCKER" rm -f "$GENERATION_ACTIVE" >/dev/null
      "$REAL_DOCKER" compose -f compose.yml up -d app-blue >/dev/null
      exit 1
    fi
    if [ -f "$SHIM_DIR/kill-target-on-reload" ]; then
      rm -f "$SHIM_DIR/kill-target-on-reload"
      "$REAL_DOCKER" kill "$GENERATION_TARGET" >/dev/null
      exit 1
    fi
    if [ -f "$SHIM_DIR/fail-reload" ]; then
      remaining=$(cat "$SHIM_DIR/fail-reload")
      if [ "$remaining" -gt 0 ]; then
        echo $((remaining - 1)) > "$SHIM_DIR/fail-reload"
        echo "reload failed (injected)" >&2
        exit 1
      fi
    fi ;;
esac
last=
for arg in "$@"; do last=$arg; done
case "$last" in
  'OPEN '*)
    if [ -f "$SHIM_DIR/fail-readback-after-open" ]; then
      rm -f "$SHIM_DIR/fail-readback-after-open"
      "$REAL_DOCKER" "$@" || exit $?
      touch "$SHIM_DIR/fail-gate-read"
      exit 1
    fi
    if [ -f "$SHIM_DIR/fail-route-after-open" ] && [ "$2" = "$("$REAL_DOCKER" inspect -f '{{.Id}}' "$GENERATION_TARGET")" ]; then
      rm -f "$SHIM_DIR/fail-route-after-open"
      "$REAL_DOCKER" "$@" || exit $?
      echo 503 > "$SHIM_DIR/code-app-green"
      exit 0
    fi
    if [ -f "$SHIM_DIR/lose-open-response" ]; then
      rm -f "$SHIM_DIR/lose-open-response"
      "$REAL_DOCKER" "$@" || exit $?
      exit 1
    fi ;;
esac
exec "$REAL_DOCKER" "$@"
'''

PROBE_TRANSPORT = '''#!/usr/bin/env python3
import base64, hashlib, json, os, subprocess, sys
from pathlib import Path
config = sys.stdin.read()
state = Path(os.environ['SHIM_DIR'])
if (state / 'fail-runtime-probe').exists():
    sys.exit(1)
if (state / 'flood-runtime-probe').exists():
    sys.stdout.buffer.write(b'x' * 70000)
    sys.exit(0)
inspected = json.loads(subprocess.check_output([os.environ['REAL_DOCKER'], 'inspect', sys.argv[1]]))[0]
env = dict(item.split('=', 1) for item in inspected['Config']['Env'] if '=' in item)
generation = env.get('PHEEEEW_PRESS_RACE_SERVER_GENERATION') or '11111111-1111-1111-1111-111111111111'
if (state / 'mismatch-runtime-probe').exists():
    generation = '99999999-9999-9999-9999-999999999999'
key = next(line.split(': ', 1)[1].rstrip('"') for line in config.splitlines() if 'Sec-WebSocket-Key:' in line)
accept = base64.b64encode(hashlib.sha1((key + '258EAFA5-E914-47DA-95CA-C5AB0DC85B11').encode()).digest()).decode()
payload = json.dumps({'v': 1, 'type': 'protocol.error', 'code': 'DEVICE_NOT_FOUND', 'serverGeneration': generation}).encode()
header = ('HTTP/1.1 101 Switching Protocols\\r\\nUpgrade: websocket\\r\\nConnection: Upgrade\\r\\nSec-WebSocket-Accept: ' + accept + '\\r\\n\\r\\n').encode()
frame = bytes([0x81, 126]) + len(payload).to_bytes(2, 'big') + payload
if (state / 'invalid-runtime-frame').exists():
    frame = bytes([0x81, 127]) + len(payload).to_bytes(8, 'big') + payload
sys.stdout.buffer.write(header + frame)
'''

PSQL_SHIM = '''#!/bin/sh
# Real PostgreSQL SQL; only transport is adapted to the isolated fixture container.
if [ -f "$SHIM_DIR/fail-db-restore" ] && [ -f "$SHIM_DIR/db-handed-off" ]; then
  exit 1
fi
sql=$(cat)
case "$sql" in
  *'UPDATE press_race_control'*)
    printf 'update\\n' >> "$SHIM_DIR/control-updates"
    if [ -f "$SHIM_DIR/fail-before-control-update" ]; then
      rm -f "$SHIM_DIR/fail-before-control-update"
      exit 1
    fi
    if [ -f "$SHIM_DIR/fail-proof-after-update" ]; then
      rm -f "$SHIM_DIR/fail-proof-after-update"
      touch "$SHIM_DIR/fail-runtime-probe"
    fi
    if [ -f "$SHIM_DIR/lose-handoff-response" ] && [ ! -f "$SHIM_DIR/db-handed-off" ]; then
      printf '%s' "$sql" | "$REAL_DOCKER" exec -i "$GENERATION_DB" psql -U postgres -d postgres "$@" >/dev/null || exit 1
      touch "$SHIM_DIR/db-handed-off"
      exit 1
    fi
    touch "$SHIM_DIR/db-handed-off" ;;
esac
printf '%s' "$sql" | "$REAL_DOCKER" exec -i -e PGOPTIONS "$GENERATION_DB" psql -U postgres -d postgres "$@"
'''

CURL_SHIM = '''#!/bin/sh
case "$*" in
  *--resolve*)
    body=; head=
    while [ $# -gt 0 ]; do
      case "$1" in
        -o) body=$2; shift ;;
        -D) head=$2; shift ;;
      esac
      shift
    done
    color=$(sed -n 's/.*set \\$upstream_app \\([a-z-]*\\);.*/\\1/p' "$SHIM_POINTER")
    code=$(cat "$SHIM_DIR/code-$color" 2>/dev/null || echo 200)
    served=$(cat "$SHIM_DIR/upstream-$color" 2>/dev/null || echo "$color")
    cat "$SHIM_DIR/body-$color" 2>/dev/null > "$body" || echo '{"available":true}' > "$body"
    { echo "HTTP/2 $code"; echo "x-upstream: $served"; echo; } > "$head"
    printf '%s' "$code"
    exit 0 ;;
esac
exec /usr/bin/curl "$@"
'''

HOSTNAME_SHIM = '''#!/bin/sh
if [ -f "$SHIM_DIR/fail-first-activation" ]; then
  rm -f "$SHIM_DIR/fail-first-activation"
  touch "$SHIM_DIR/fail-before-control-update"
fi
if [ -f "$SHIM_DIR/hostname" ]; then cat "$SHIM_DIR/hostname"; else exec "$REAL_HOSTNAME" "$@"; fi
'''


def sh(*args, **kwargs):
    return subprocess.run(args, text=True, capture_output=True, **kwargs)


class Fixture:

    def __init__(self, root):
        self.dir = Path(root)
        self.project = 'dsw' + uuid.uuid4().hex[:8]
        self.state = self.dir / 'state'
        self.shim = self.dir / 'shim'
        self.confd = self.dir / 'conf.d'
        self.appstate = self.dir / 'appstate'
        self.upstream = self.dir / 'nginx' / 'upstream'
        for path in (self.state, self.shim, self.confd, self.appstate):
            path.mkdir(parents=True)
        self.upstream.mkdir(parents=True)
        self.pointer = self.upstream / 'active.conf'
        self._certs()
        self._shims()
        self._compose()
        shutil.copy(REPO / 'BE/docker/press-generation.py', self.dir / 'press-generation.py')
        shutil.copy(REPO / 'BE/docker/press-operations.py', self.dir / 'press-operations.py')
        (self.dir / '.env').write_text('')
        self.db = self.project + '-generation-db'
        sh('docker', 'run', '-d', '--name', self.db, '-e', 'POSTGRES_HOST_AUTH_METHOD=trust',
           'postgis/postgis:17-3.5')
        for _ in range(60):
            if sh('docker', 'exec', self.db, 'pg_isready', '-h', '127.0.0.1', '-U', 'postgres').returncode == 0:
                break
            time.sleep(1)
        else:
            sh('docker', 'rm', '-f', self.db)
            raise Failure('fixture PostgreSQL 이 TCP 연결을 받을 준비가 되지 않았다')
        migration = (REPO / 'BE/src/main/resources/db/migration/V20261026_2__create_press_race_storage.sql').read_text()
        self.sql(migration.split('CREATE TABLE press_device_controls')[0])
        self.sql('CREATE TABLE devices (public_id UUID PRIMARY KEY);')
        self.sql('CREATE TABLE press_device_controls (connection_generation UUID, ready BOOLEAN);')
        shutil.copy(REPO / 'BE/docker/nginx/api-dev.conf', self.confd / 'default.conf')
        self.reset_edge()

    def _certs(self):
        sh('openssl', 'req', '-x509', '-newkey', 'rsa:2048', '-nodes', '-days', '1',
           '-subj', '/CN=api-dev.pheeeew.com',
           '-keyout', str(self.dir / 'origin.key'), '-out', str(self.dir / 'origin.pem'))
        for name in ('origin-pull-ca.pem', 'fullchain.pem', 'chain.pem'):
            shutil.copy(self.dir / 'origin.pem', self.dir / name)
        shutil.copy(self.dir / 'origin.key', self.dir / 'privkey.pem')

    def _shims(self):
        for name, text in (('docker', DOCKER_SHIM), ('curl', CURL_SHIM), ('psql', PSQL_SHIM), ('hostname', HOSTNAME_SHIM)):
            path = self.shim / name
            path.write_text(text)
            path.chmod(0o755)
        probe = self.dir / 'probe-transport'
        probe.write_text(PROBE_TRANSPORT)
        probe.chmod(0o755)

    def _compose(self):
        key = subprocess.run(['openssl', 'pkcs8', '-topk8', '-nocrypt', '-in', str(self.dir / 'origin.key'),
                              '-outform', 'DER'], capture_output=True, check=True)
        encoded_key = base64.b64encode(key.stdout).decode()
        base = {
            'image': 'python:3.13-alpine',
            'volumes': [f'{self.appstate}:/appstate'],
            'healthcheck': {
                'test': ['CMD', 'wget', '-q', '-O', '-', 'http://127.0.0.1:8080/actuator/health'],
                'interval': '2s', 'timeout': '2s', 'retries': 10, 'start_period': '2s',
            },
            'stop_grace_period': '5s',
            'init': True,
            'labels': {'com.pheeeew.press-generation-contract': '1'},
        }
        services = {}
        for color in ('blue', 'green'):
            service = dict(base)
            service['command'] = ['python', '-c', APP, f'app-{color}']
            service['container_name'] = f'{self.project}-app-{color}'
            service['profiles'] = [color]
            variable = 'PRESS_BLUE_GENERATION' if color == 'blue' else 'PRESS_GREEN_GENERATION'
            default = '11111111-1111-1111-1111-111111111111' if color == 'blue' else ''
            service['environment'] = {
                'PHEEEEW_PRESS_RACE_SERVER_GENERATION': '${' + variable + ':-' + default + '}',
                'DB_URL': 'jdbc:postgresql://fixture-db:5432/postgres?sslmode=disable',
                'DB_USERNAME': 'postgres', 'DB_PASSWORD': 'fixture',
                'JWT_PRIVATE_KEY_BASE64': encoded_key,
            }
            services[f'app-{color}'] = service
        (self.dir / 'compose.yml').write_text(yaml.safe_dump({
            'name': self.project,
            'services': services,
            'networks': {'default': {'name': f'{self.project}-net', 'external': True}},
        }))

        model = yaml.safe_load((REPO / 'BE/docker/compose.nginx.dev.yml').read_text())
        model['name'] = self.project + '-nginx'
        model['networks'] = {'default': {'name': f'{self.project}-net', 'external': True}}
        nginx = model['services']['nginx']
        nginx['container_name'] = f'{self.project}-nginx'
        nginx['restart'] = 'no'
        nginx['ports'] = [f'127.0.0.1:{HTTP_PORT}:80', f'127.0.0.1:{HTTPS_PORT}:443']
        nginx['volumes'] = [
            f'{self.confd}:/etc/nginx/conf.d:ro',
            f'{self.upstream}:/etc/nginx/upstream:ro',
            f'{self.dir}:/etc/ssl/cloudflare:ro',
            f'{self.dir}:/etc/letsencrypt/live/api-dev.pheeeew.com:ro',
        ]
        (self.dir / 'compose.nginx.yml').write_text(yaml.safe_dump(model))

    def reset_edge(self):
        for stale in self.state.glob('*'):
            stale.unlink()
        for color in COLORS:
            (self.state / f'code-{color}').write_text('200')
            (self.state / f'upstream-{color}').write_text(color)
            (self.state / f'body-{color}').write_text('{"available":true}')

    def edge(self, color, code=None, upstream=None, body=None):
        if code is not None:
            (self.state / f'code-{color}').write_text(code)
        if upstream is not None:
            (self.state / f'upstream-{color}').write_text(upstream)
        if body is not None:
            (self.state / f'body-{color}').write_text(body)

    def point_at(self, text):
        self.pointer.write_text(text)

    def fail_reload(self, times=1):
        (self.state / 'fail-reload').write_text(str(times))

    def fail_drain(self):
        (self.state / 'fail-drain').write_text('1')

    def slow_request(self, seconds=20):
        return subprocess.Popen(
            ['curl', '-sk', '-o', '/dev/null', '-w', '%{http_code}', '-m', '150',
             '--resolve', f'api-dev.pheeeew.com:{HTTPS_PORT}:127.0.0.1',
             f'https://api-dev.pheeeew.com:{HTTPS_PORT}{SLOW_PATH}?seconds={seconds}'],
            text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, start_new_session=True)

    def served_upstream(self):
        result = sh('curl', '-sk', '-o', '/dev/null', '-D', '-', '-w', '%{http_code}', '-m', '5',
                    '--resolve', f'api-dev.pheeeew.com:{HTTPS_PORT}:127.0.0.1',
                    f'https://api-dev.pheeeew.com:{HTTPS_PORT}{PROBE_PATH}')
        upstream = ''
        for line in result.stdout.splitlines():
            if line.lower().startswith('x-upstream:'):
                upstream = line.split(':', 1)[1].strip()
        return result.stdout.strip().splitlines()[-1] if result.stdout else '', upstream

    def wait_edge_serves(self, color, timeout=60):
        deadline = time.monotonic() + timeout
        seen = ('', '')
        while time.monotonic() < deadline:
            seen = self.served_upstream()
            if seen == ('200', color):
                return
            time.sleep(0.2)
        raise Failure(f'엣지가 {color} 를 200 으로 서비스하지 않았다. 마지막 확인 {seen}')

    def wait_inflight(self, color, timeout=30):
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            if (self.appstate / f'inflight-{color}').exists():
                return
            time.sleep(0.2)
        raise Failure(f'{color} 로 간 느린 요청이 시작되지 않았다')

    def assert_alive_until_inflight_done(self, color, timeout=120):
        deadline = time.monotonic() + timeout
        marker = self.appstate / f'inflight-{color}'
        while marker.exists():
            if not self.exists(color) and marker.exists():
                raise Failure(f'{color} 가 진행 중 요청이 끝나기 전에 삭제됐다')
            if time.monotonic() > deadline:
                raise Failure(f'{color} 의 느린 요청이 끝나지 않았다')
            time.sleep(0.3)

    def break_api(self, color, mode='503'):
        (self.appstate / f'badapi-{color}').write_text(mode)

    def clear_appstate(self):
        for stale in self.appstate.glob('*'):
            stale.unlink(missing_ok=True)

    def break_nginx_conf(self):
        (self.confd / 'zz-bad.conf').write_text('this is not nginx syntax\n')

    def fix_nginx_conf(self):
        (self.confd / 'zz-bad.conf').unlink(missing_ok=True)

    def break_app(self, color):
        compose = yaml.safe_load((self.dir / 'compose.yml').read_text())
        compose['services'][color]['command'] = ['sh', '-c', 'exit 1']
        compose['services'][color].pop('healthcheck', None)
        (self.dir / 'compose.yml').write_text(yaml.safe_dump(compose))

    def restore_app(self, color):
        self._compose()

    def compose_nginx(self, *args):
        return sh('docker', 'compose', '-f', str(self.dir / 'compose.nginx.yml'), *args)

    def compose_apps(self, *args):
        return sh('docker', 'compose', '-f', str(self.dir / 'compose.yml'), *args)

    def start_nginx(self):
        sh('docker', 'network', 'create', f'{self.project}-net')
        self.compose_nginx('up', '-d', '--wait', '--wait-timeout', '60')

    def start_app(self, color):
        result = self.compose_apps('up', '-d', '--wait', '--wait-timeout', '60', color)
        if result.returncode != 0:
            print(f'    {color} 를 띄우지 못했습니다: {result.stderr.strip().splitlines()[-1:]}')
            self.diagnose()
            raise Failure(f'{color} 가 healthy 가 되지 않았다')

    def running(self, container):
        return sh('docker', 'inspect', '-f', '{{.State.Running}}',
                  f'{self.project}-{container}').stdout.strip() == 'true'

    def exists(self, container):
        return sh('docker', 'inspect', f'{self.project}-{container}').returncode == 0

    def pointer_color(self):
        for line in self.pointer.read_text().splitlines():
            if 'set $upstream_app' in line:
                return line.strip().removeprefix('set $upstream_app').strip(' ;')
        return ''

    def backup_exists(self):
        return (self.upstream / 'active.conf.bak').exists()

    def deploy_async(self):
        script = remote_script().replace('cd /opt/pheeeew', f'cd {self.dir}')
        script = script.replace('pheeeew-dev-', self.project + '-')
        env = dict(os.environ)
        env['PATH'] = f'{self.shim}:{env["PATH"]}'
        env['REAL_DOCKER'] = shutil.which('docker')
        env['REAL_HOSTNAME'] = shutil.which('hostname')
        env['SHIM_DIR'] = str(self.state)
        env['SHIM_POINTER'] = str(self.pointer)
        env['APP_IMAGE'] = 'python:3.13-alpine'
        env['GENERATION_DB'] = self.db
        env['GENERATION_TARGET'] = self.project + '-app-green'
        env['GENERATION_ACTIVE'] = self.project + '-app-blue'
        env['GENERATION_NGINX'] = self.project + '-nginx'
        env['ALLOW_LEGACY_GENERATION'] = 'true' if (self.state / 'allow-legacy-generation').exists() else 'false'
        proc = subprocess.Popen(['bash', '-s'], stdin=subprocess.PIPE,
                                stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                                text=True, env=env, cwd=self.dir, start_new_session=True)
        proc.stdin.write(script)
        proc.stdin.close()
        proc.stdin = None
        return proc

    @contextmanager
    def capture_processes_on_failure(self):
        processes = {}
        try:
            yield processes
        except Exception:
            for name, proc in processes.items():
                try:
                    out, err = proc.communicate(timeout=300 if name == 'deploy' else 160)
                except subprocess.TimeoutExpired:
                    print(f'    {name}: 결과 수집 시간 초과')
                    os.killpg(proc.pid, signal.SIGKILL)
                    out, err = proc.communicate()
                print(f'    {name}: exit={proc.returncode}')
                print(f'    {name} stdout:\n{out or "(empty)"}')
                print(f'    {name} stderr:\n{err or "(empty)"}')
            raise

    def wait_deploy(self, proc, timeout=300):
        out, err = proc.communicate(timeout=timeout)
        return subprocess.CompletedProcess(proc.args, proc.returncode, out, err)

    def deploy(self):
        return self.wait_deploy(self.deploy_async())

    def tool(self, *args):
        env = dict(os.environ, REAL_DOCKER=shutil.which('docker'), SHIM_DIR=str(self.state),
                   REAL_HOSTNAME=shutil.which('hostname'),
                   SHIM_POINTER=str(self.pointer), GENERATION_DB=self.db,
                   GENERATION_TARGET=self.project + '-app-green', GENERATION_ACTIVE=self.project + '-app-blue',
                   GENERATION_NGINX=self.project + '-nginx')
        env['PATH'] = f'{self.shim}:{env["PATH"]}'
        return sh(sys.executable, 'press-generation.py', *args, cwd=self.dir, env=env)

    def diagnose(self):
        names = sh('docker', 'ps', '-a', '--filter', f'name={self.project}',
                   '--format', '{{.Names}}').stdout.split()
        for name in names:
            state = sh('docker', 'inspect', name, '--format',
                       'status={{.State.Status}} exit={{.State.ExitCode}}'
                       ' health={{if .State.Health}}{{.State.Health.Status}}{{end}}')
            print(f'    {name}: {state.stdout.strip()}')
            logs = sh('docker', 'logs', '--tail', '5', name)
            for line in (logs.stdout + logs.stderr).splitlines()[-5:]:
                print(f'      로그 {line}')
            probes = sh('docker', 'inspect', name, '--format',
                        '{{range .State.Health.Log}}{{.ExitCode}} {{.Output}}{{end}}')
            for line in probes.stdout.splitlines()[-3:]:
                print(f'      헬스체크 {line}')

    def cleanup(self):
        sh('docker', 'rm', '-f', self.db)
        self.compose_nginx('down', '-v', '--remove-orphans')
        self.compose_apps('--profile', 'blue', '--profile', 'green', 'down', '-v',
                          '--remove-orphans')
        sh('docker', 'network', 'rm', f'{self.project}-net')

    def sql(self, sql):
        result = sh('docker', 'exec', '-i', self.db, 'psql', '-U', 'postgres', '-d', 'postgres',
                    '-XAt', '-v', 'ON_ERROR_STOP=1', input=sql)
        if result.returncode:
            raise Failure(result.stderr)
        return result.stdout.strip()


class Failure(AssertionError):
    pass


def expect(condition, message):
    if not condition:
        raise Failure(message)


def scenario_normal(fx):
    fx.point_at('set $upstream_app app-blue;\n')
    fx.start_app('app-blue')
    result = fx.deploy()
    expect(result.returncode == 0, f'종료 코드 {result.returncode}\n{result.stdout}{result.stderr}')
    expect(fx.pointer_color() == 'app-green', f'전환 파일 {fx.pointer_color()}')
    expect(fx.running('app-green'), 'app-green 이 떠 있지 않다')
    expect(not fx.exists('app-blue'), 'app-blue 가 남아 있다')
    expect(not fx.backup_exists(), '백업 파일이 남아 있다')
    expect('이전 요청이 모두 끝났습니다' in result.stdout, '드레인 확인 로그가 없다')
    actual = sh('docker', 'inspect', fx.project + '-app-green', '--format',
                '{{range .Config.Env}}{{println .}}{{end}}').stdout
    generation = next(line.split('=', 1)[1] for line in actual.splitlines()
                      if line.startswith('PHEEEEW_PRESS_RACE_SERVER_GENERATION='))
    expect(fx.sql('SELECT active_server_generation FROM press_race_control;') == generation,
           '실제 새 컨테이너 UUID와 DB 소유권이 다르다')


def scenario_start_fail(fx):
    fx.point_at('set $upstream_app app-blue;\n')
    fx.start_app('app-blue')
    fx.break_app('app-green')
    result = fx.deploy()
    expect(result.returncode == 1, f'종료 코드 {result.returncode}')
    expect(fx.pointer_color() == 'app-blue', f'전환 파일 {fx.pointer_color()}')
    expect(fx.running('app-blue'), 'app-blue 가 내려갔다')
    expect(not fx.exists('app-green'), 'app-green 이 남아 있다')
    expect('전환하지 않고 끝냅니다' in result.stdout, '미전환 안내가 없다')
    fx.restore_app('app-green')


def scenario_conf_test_fail(fx):
    fx.point_at('set $upstream_app app-blue;\n')
    fx.start_app('app-blue')
    fx.break_nginx_conf()
    result = fx.deploy()
    fx.fix_nginx_conf()
    expect(result.returncode == 1, f'종료 코드 {result.returncode}')
    expect(fx.pointer_color() == 'app-blue', f'전환 파일 {fx.pointer_color()}')
    expect(fx.backup_exists(), '복구 확인 전 백업을 삭제했다')
    expect(fx.running('app-blue'), 'app-blue 가 내려갔다')
    expect(fx.exists('app-green'), 'nginx 복구를 확인하지 못했는데 대상을 삭제했다')
    expect('전환 설정이 올바르지 않습니다' in result.stdout, 'nginx -t 실패 안내가 없다')


def scenario_reload_fail(fx):
    fx.point_at('set $upstream_app app-blue;\n')
    fx.start_app('app-blue')
    fx.fail_reload()
    result = fx.deploy()
    expect(result.returncode == 1, f'종료 코드 {result.returncode}')
    expect(fx.pointer_color() == 'app-blue', f'전환 파일 {fx.pointer_color()}')
    expect(fx.running('app-blue'), 'app-blue 가 내려갔다')
    expect(not fx.exists('app-green'), 'app-green 이 남아 있다')
    expect('app-blue 로 복구했고 정상 응답을 확인했습니다' in result.stdout, '복구 안내가 없다')
    expect(fx.sql('SELECT active_server_generation FROM press_race_control;') ==
           '11111111-1111-1111-1111-111111111111', 'DB 소유권이 blue로 복구되지 않았다')


def scenario_lost_db_response(fx):
    fx.start_app('app-blue')
    (fx.state / 'lose-handoff-response').touch()
    result = fx.deploy()
    expect(result.returncode == 1, '커밋 응답 유실을 성공으로 처리했다')
    expect(fx.pointer_color() == 'app-blue', '응답 유실 뒤 프록시가 전환됐다')
    expect(fx.sql('SELECT active_server_generation FROM press_race_control;') ==
           '11111111-1111-1111-1111-111111111111', '실제 커밋을 다시 읽고 복구하지 않았다')
    expect(not fx.exists('app-green'), '복구 확인 후 대상이 정리되지 않았다')


def scenario_db_restore_fails(fx):
    fx.start_app('app-blue')
    fx.fail_reload()
    (fx.state / 'fail-db-restore').touch()
    result = fx.deploy()
    expect(result.returncode == 1, 'DB 복구 실패를 성공으로 처리했다')
    expect(fx.running('app-blue') and fx.running('app-green'), '불확실한 DB 소유권에서 서버를 삭제했다')
    expect((fx.dir / '.press-deploy.json').exists(), '복구 실패 기록을 삭제했다')


def scenario_target_dies_after_handoff(fx):
    fx.start_app('app-blue')
    (fx.state / 'kill-target-on-reload').touch()
    result = fx.deploy()
    expect(result.returncode == 1, '새 서버 종료를 성공으로 처리했다')
    expect(fx.running('app-blue') and not fx.exists('app-green'), '기존 서버 복구 또는 종료된 대상 정리 실패')
    expect(fx.pointer_color() == 'app-blue', '기존 nginx 대상으로 복귀하지 않았다')
    expect(fx.sql('SELECT active_server_generation FROM press_race_control;') ==
           '11111111-1111-1111-1111-111111111111', '새 서버가 죽은 뒤 DB 자격을 복구하지 않았다')


def scenario_competition_on(fx):
    fx.start_app('app-blue')
    fx.sql('UPDATE press_race_control SET enabled = TRUE;')
    result = fx.deploy()
    expect(result.returncode == 1 and not fx.exists('app-green'), '경쟁 ON 상태로 배포했다')
    expect(fx.pointer_color() == 'app-blue', '경쟁 ON에서 프록시를 변경했다')


def operations_fixture(fx, enabled=True, active_version='2'):
    model = yaml.safe_load((fx.dir / 'compose.yml').read_text())
    for app in model['services'].values():
        app['labels'] = dict(app['labels'], **{'com.pheeeew.press-generation-contract': '2'})
        app['environment']['PHEEEEW_PRESS_RACE_ADMISSION_FILE'] = '/app/press-admission'
        app['environment']['PHEEEEW_PRESS_RACE_ADMISSION_REQUIRED'] = 'true'
        app['environment']['SPRING_PROFILES_ACTIVE'] = 'dev'
    if active_version == '1':
        model['services']['app-blue']['labels']['com.pheeeew.press-generation-contract'] = '1'
        model['services']['app-blue']['environment'].pop('PHEEEEW_PRESS_RACE_ADMISSION_FILE')
        model['services']['app-blue']['environment'].pop('PHEEEEW_PRESS_RACE_ADMISSION_REQUIRED')
    (fx.dir / 'compose.yml').write_text(yaml.safe_dump(model))
    (fx.state / 'v2-image').touch()
    fx.start_app('app-blue')
    if active_version == '2':
        sh('docker', 'exec', fx.project + '-app-blue', 'sh', '-c',
           "printf 'OPEN 22222222-2222-2222-2222-222222222222 11111111-1111-1111-1111-111111111111\\n' > /app/press-admission")
    fx.sql("UPDATE press_race_control SET enabled=" + ('TRUE' if enabled else 'FALSE') +
           ", activation_date=(clock_timestamp() AT TIME ZONE 'Asia/Seoul')::date;")
    fx.sql("INSERT INTO press_device_controls VALUES ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', TRUE);")


def scenario_competition_on_v2(fx):
    operations_fixture(fx)
    result = fx.deploy()
    expect(result.returncode == 0, '지원하는 ON 전환이 실패했다: ' + result.stdout + result.stderr)
    expect(fx.sql('SELECT enabled FROM press_race_control;') == 't', '일반 배포가 경쟁 ON 을 바꿨다')
    expect(fx.pointer_color() == 'app-green', '신규 서버로 라우팅되지 않았다')
    expect(fx.sql('SELECT COUNT(*) FROM press_device_controls WHERE connection_generation IS NOT NULL OR ready;') == '0',
           '기존 연결의 저장 자격이 남았다')


def scenario_v2_lost_db_response(fx):
    operations_fixture(fx)
    (fx.state / 'lose-handoff-response').touch()
    result = fx.deploy()
    expect(result.returncode == 0, 'DB 커밋 응답 유실을 재조회로 정산하지 못했다: ' + result.stdout + result.stderr)
    expect(fx.sql('SELECT enabled FROM press_race_control;') == 't', '정상 전환이 경쟁을 중단했다')


def scenario_v2_lost_open_response(fx):
    operations_fixture(fx)
    (fx.state / 'lose-open-response').touch()
    result = fx.deploy()
    expect(result.returncode == 0, '확인 가능한 OPEN 응답 유실을 정산하지 못했다: ' + result.stdout + result.stderr)


def scenario_v2_reload_rollback(fx):
    operations_fixture(fx)
    fx.fail_reload()
    result = fx.deploy()
    expect(result.returncode == 1, '실패한 전환을 성공으로 보고했다')
    expect(fx.sql('SELECT enabled FROM press_race_control;') == 't', '검증된 원본 복귀에서 경쟁을 껐다')
    expect(fx.sql('SELECT active_server_generation FROM press_race_control;') ==
           '11111111-1111-1111-1111-111111111111', '원본 소유권이 복구되지 않았다')
    expect(fx.pointer_color() == 'app-blue' and fx.running('app-blue') and not fx.exists('app-green'),
           '검증된 원본 복귀가 완료되지 않았다: ' + result.stdout + result.stderr)
    expect(sh('docker', 'exec', fx.project + '-app-blue', 'cat', '/app/press-admission').stdout.startswith('OPEN '),
           '복귀한 원본 입력이 재개되지 않았다')


def failed_open_fixture(fx):
    operations_fixture(fx)
    (fx.state / 'fail-route-after-open').touch()
    result = fx.deploy()
    expect(result.returncode == 1, '개방 후 검증 실패를 배포 성공으로 보고했다')
    expect(fx.exists('app-blue') and fx.exists('app-green'), '불확실한 전환에서 서버를 삭제했다')
    expect(fx.sql('SELECT enabled FROM press_race_control;') == 'f', '개방 후 장애를 OFF로 정리하지 못했다')
    journal = json.loads((fx.dir / '.press-deploy.json').read_text())
    expect(journal['open_attempted'] and journal['phase'] == 'EMERGENCY_OFF_CONFIRMED', '개방 시도/중단 기록을 보존하지 않았다')
    for color in COLORS:
        expect(sh('docker', 'exec', fx.project + '-' + color, 'cat', '/app/press-admission').stdout.startswith('CLOSED '),
               '불확실한 전환의 입력이 열려 있다')
    return journal


def scenario_v2_open_then_failure(fx):
    failed_open_fixture(fx)


def replay_gate_command(fx, command):
    cas = __import__('runpy').run_path(str(fx.dir / 'press-operations.py'))['CAS']
    return sh('docker', 'exec', command['container_id'], 'sh', '-c', cas, 'sh', '/app/press-admission',
              command['expected'].rstrip('\n'), command['replacement'].rstrip('\n'))


def scenario_v2_delayed_open_and_manual_off_resume(fx):
    journal = failed_open_fixture(fx)
    command = next(item for item in journal['gate_commands'] if item['purpose'] == 'open-target')
    expect(replay_gate_command(fx, command).returncode != 0, '늦은 이전 OPEN 이 새 CLOSED를 덮어썼다')
    fx.edge('app-green', code='200')
    result = fx.tool('resume-off', fx.project + '-app-green', '--expected-generation', journal['target_generation'],
                     '--expected-activation-date', journal['activation_date'])
    expect(result.returncode == 0, '명시적 OFF 복구 실패: ' + result.stdout + result.stderr)
    recovered = json.loads((fx.dir / '.press-deploy.json').read_text())
    expect(recovered['phase'] == 'HTTP_RECOVERED' and fx.sql('SELECT enabled FROM press_race_control;') == 'f',
           'HTTP 복구가 경쟁을 다시 켰거나 기록을 잃었다')
    expect(fx.exists('app-blue') and fx.exists('app-green'), '수동 정산 없이 서버를 삭제했다')
    resume = next(item for item in recovered['gate_commands'] if item['purpose'].startswith('resume-off-open'))
    result = fx.tool('emergency-stop', fx.project + '-app-green')
    expect(result.returncode == 0, '복구 후 재중단 실패: ' + result.stdout + result.stderr)
    expect(replay_gate_command(fx, resume).returncode != 0, '이전 복구 OPEN 이 재중단을 무효화했다')


def scenario_v2_restart_blocks_restore(fx):
    operations_fixture(fx)
    (fx.state / 'restart-active-on-reload').touch()
    result = fx.deploy()
    expect(result.returncode == 1 and fx.exists('app-blue') and fx.exists('app-green'), '재시작 원본에 자동 복귀했다')
    expect(fx.sql('SELECT active_server_generation FROM press_race_control;') !=
           '11111111-1111-1111-1111-111111111111', '과거 프로세스 신원으로 자격을 복원했다')
    expect(sh('docker', 'exec', fx.project + '-app-blue', 'cat', '/app/press-admission').stdout.startswith('CLOSED '),
           '같은 UUID 재시작으로 차단이 풀렸다')


def scenario_v2_stop_and_activate(fx):
    operations_fixture(fx)
    current = fx.sql("SELECT (clock_timestamp() AT TIME ZONE 'Asia/Seoul')::date;")
    (fx.state / 'lose-handoff-response').touch()
    result = fx.tool('stop', fx.project + '-app-blue', '--expected-generation',
                     '11111111-1111-1111-1111-111111111111', '--expected-activation-date', current)
    expect(result.returncode == 0, '긴급 중단 후 호환 HTTP 입력을 재개하지 못했다: ' + result.stdout + result.stderr)
    expect(fx.sql('SELECT enabled FROM press_race_control;') == 'f', '중단 후 경쟁이 켜졌다')
    expect(sh('docker', 'exec', fx.project + '-app-blue', 'cat', '/app/press-admission').stdout.startswith('OPEN '),
           '중단이 기존 HTTP까지 영구 차단했다')
    (fx.state / 'db-handed-off').unlink(missing_ok=True)
    result = fx.tool('activate', fx.project + '-app-blue', '--expected-generation',
                     '11111111-1111-1111-1111-111111111111', '--expected-activation-date', current,
                     '--activation-date', current)
    expect(result.returncode == 1 and not (fx.dir / '.press-deploy.json').exists(),
           '잘못된 활성화 날짜를 수락했거나 작업 기록으로 이후 작업을 막았다')
    result = fx.tool('activate', fx.project + '-app-blue', '--expected-generation',
                     '11111111-1111-1111-1111-111111111111', '--expected-activation-date', current)
    expect(result.returncode == 0, '경쟁 ON을 즉시 사용할 수 없다: ' + result.stdout + result.stderr)
    expect(fx.sql("SELECT enabled AND activation_date = (clock_timestamp() AT TIME ZONE 'Asia/Seoul')::date FROM press_race_control;") == 't',
           'ON이 다음 날짜까지 경쟁을 지연했다')


def scenario_first_v2_off_manual_recovery(fx):
    operations_fixture(fx, enabled=False, active_version='1')
    (fx.state / 'fail-route-after-open').touch()
    result = fx.deploy()
    expect(result.returncode == 1, '최초 이행의 개방 후 실패를 성공으로 보고했다')
    journal = json.loads((fx.dir / '.press-deploy.json').read_text())
    expect(journal['phase'] == 'EMERGENCY_OFF_CONFIRMED', '최초 이행 실패의 OFF를 확인하지 못했다')
    fx.edge('app-green', code='200')
    result = fx.tool('resume-off', fx.project + '-app-green', '--expected-generation', journal['target_generation'],
                     '--expected-activation-date', journal['activation_date'])
    expect(result.returncode == 0, '기존 v1 비소유자를 보존하는 최초 v2 OFF 복구 실패: ' + result.stdout + result.stderr)
    expect(fx.sql('SELECT active_server_generation FROM press_race_control;') == journal['target_generation'],
           'OFF 복구가 과거 UUID로 소유권을 되돌렸다')
    expect(fx.sql('SELECT enabled FROM press_race_control;') == 'f', '최초 이행 OFF 복구가 경쟁을 켰다')


def scenario_operations_exact_targets(fx):
    operations_fixture(fx)
    active, target = fx.project + '-app-blue', fx.project + '-app-green'
    result = fx.tool('prepare', active, 'app-blue')
    expect(result.returncode == 1 and not (fx.dir / '.press-deploy.json').exists(), '활성 슬롯을 후보로 허용했다')
    expect(fx.tool('prepare', active, 'app-green').returncode == 0, '유효한 후보 준비 실패')
    result = sh('docker', 'compose', '--env-file', str(fx.dir / '.env'), '--env-file',
                str(fx.dir / 'press-generations.env'), '-f', str(fx.dir / 'compose.yml'),
                'up', '-d', '--wait', '--wait-timeout', '60', 'app-green')
    expect(result.returncode == 0 and fx.tool('record-target', target).returncode == 0, '후보 신원 기록 실패')
    before = sh('docker', 'exec', active, 'cat', '/app/press-admission').stdout
    expect(fx.tool('emergency-stop', active).returncode == 1, '다른 컨테이너를 지정한 중단을 실행했다')
    expect(sh('docker', 'exec', active, 'cat', '/app/press-admission').stdout == before
           and fx.sql('SELECT enabled FROM press_race_control;') == 't', '잘못된 대상의 중단이 상태를 바꿨다')
    expect(fx.tool('cleanup-target', active).returncode == 1 and fx.exists('app-green'),
           '다른 대상을 지정했는데 기록된 후보를 삭제했다')
    expect(fx.tool('cleanup-target', target).returncode == 0 and not fx.exists('app-green'),
           '검증된 정확한 후보 정리 실패')


def first_deployment_fixture(fx):
    operations_fixture(fx, enabled=False, active_version='1')
    fx.sql('UPDATE press_race_control SET activation_date = NULL;')
    (fx.state / 'hostname').write_text('pheeeew-dev\n')


def scenario_first_development_deployment(fx):
    first_deployment_fixture(fx)
    result = fx.deploy()
    expect(result.returncode == 0, '최초 개발 배포 마무리 실패: ' + result.stdout + result.stderr)
    expect(fx.sql('SELECT enabled FROM press_race_control;') == 't', '정상 최초 개발 배포 후 경쟁을 사용할 수 없다')
    expect(fx.sql("SELECT activation_date = (clock_timestamp() AT TIME ZONE 'Asia/Seoul')::date FROM press_race_control;") == 't',
           '최초 경쟁을 DB 기준 현재 날짜에 사용할 수 없다')
    expect(not (fx.dir / '.press-deploy.json').exists() and not fx.backup_exists(), '성공한 마무리 기록이 남았다')


def scenario_ordinary_off_stays_off(fx):
    operations_fixture(fx, enabled=False)
    fx.sql('UPDATE press_race_control SET activation_date = NULL;')
    result = fx.deploy()
    expect(result.returncode == 0, '일반 OFF 개발 배포 실패: ' + result.stdout + result.stderr)
    expect(fx.sql('SELECT NOT enabled AND activation_date IS NULL FROM press_race_control;') == 't',
           '일반 OFF 배포가 경쟁을 자동으로 활성화했다')


def scenario_first_then_normal_on(fx):
    scenario_first_development_deployment(fx)
    day = fx.sql('SELECT activation_date FROM press_race_control;')
    generation = fx.sql('SELECT active_server_generation FROM press_race_control;')
    model = yaml.safe_load((fx.dir / 'compose.yml').read_text())
    next_app = model['services']['app-blue']
    next_app['labels']['com.pheeeew.press-generation-contract'] = '2'
    next_app['environment']['PHEEEEW_PRESS_RACE_ADMISSION_FILE'] = '/app/press-admission'
    next_app['environment']['PHEEEEW_PRESS_RACE_ADMISSION_REQUIRED'] = 'true'
    (fx.dir / 'compose.yml').write_text(yaml.safe_dump(model))
    result = fx.deploy()
    expect(result.returncode == 0, '기존 ON의 후속 정상 배포 실패: ' + result.stdout + result.stderr)
    expect(fx.sql('SELECT enabled FROM press_race_control;') == 't'
           and fx.sql('SELECT activation_date FROM press_race_control;') == day,
           '후속 배포가 기존 ON이나 최초 활성화 날짜를 바꿨다')
    expect(fx.sql('SELECT active_server_generation FROM press_race_control;') != generation,
           '후속 배포가 새 서버 세대로 교체되지 않았다')
    expect(len((fx.state / 'control-updates').read_text().splitlines()) == 3,
           '후속 정상 배포가 최초 활성화를 다시 실행했다')


def scenario_first_deployment_requires_development(fx):
    first_deployment_fixture(fx)
    (fx.state / 'hostname').write_text('other-environment\n')
    result = fx.deploy()
    expect(result.returncode == 1 and fx.sql('SELECT NOT enabled AND activation_date IS NULL FROM press_race_control;') == 't',
           '개발 환경을 확인하지 않고 경쟁을 활성화했다')
    expect((fx.dir / '.press-deploy.json').exists() and fx.backup_exists(), '미확인 마무리 기록을 삭제했다')


def scenario_first_monitoring_failure(fx):
    first_deployment_fixture(fx)
    (fx.state / 'fail-monitoring').touch()
    result = fx.deploy()
    expect(result.returncode == 1 and fx.sql('SELECT NOT enabled AND activation_date IS NULL FROM press_race_control;') == 't',
           '앞 단계 실패 뒤 최초 경쟁을 활성화했다')
    expect((fx.dir / '.press-deploy.json').exists() and fx.backup_exists(), '건너뛴 마무리 기록을 삭제했다')
    owner = fx.sql('SELECT active_server_generation FROM press_race_control;')
    expect(fx.deploy().returncode == 1 and fx.sql('SELECT active_server_generation FROM press_race_control;') == owner,
           '미완료 기록이 있는데 다음 배포가 다시 소유권을 바꿨다')


def scenario_on_monitoring_failure(fx):
    operations_fixture(fx)
    day = fx.sql('SELECT activation_date FROM press_race_control;')
    (fx.state / 'fail-monitoring').touch()
    result = fx.deploy()
    expect(result.returncode == 1 and fx.sql('SELECT enabled FROM press_race_control;') == 't'
           and fx.sql('SELECT activation_date FROM press_race_control;') == day,
           '모니터링 실패가 기존 ON이나 날짜를 바꿨다')
    expect((fx.dir / '.press-deploy.json').exists() and fx.backup_exists(), '미완료 ON 배포 기록을 삭제했다')
    owner = fx.sql('SELECT active_server_generation FROM press_race_control;')
    expect(fx.deploy().returncode == 1 and fx.sql('SELECT active_server_generation FROM press_race_control;') == owner,
           '미완료 ON 배포를 확인하지 않고 다음 배포가 진행됐다')
    expect(not fx.exists('app-blue') and fx.running('app-green'), '이전 서버 정리 이후의 경계가 아니다')
    (fx.state / 'db-handed-off').unlink(missing_ok=True)
    (fx.state / 'lose-handoff-response').touch()
    target = fx.project + '-app-green'
    result = fx.tool('emergency-stop', target)
    expect(result.returncode == 0 and fx.sql('SELECT enabled FROM press_race_control;') == 'f',
           '원본 제거 이후 현재 서버 긴급 중단 실패: ' + result.stdout + result.stderr)
    expect(fx.sql('SELECT active_server_generation FROM press_race_control;') == owner
           and fx.sql('SELECT activation_date FROM press_race_control;') == day,
           '긴급 중단이 현재 소유권이나 날짜를 변경했다')
    expect(sh('docker', 'exec', target, 'cat', '/app/press-admission').stdout.startswith('CLOSED '),
           '긴급 중단 뒤 현재 서버 입력이 열려 있다')
    result = fx.tool('resume-off', target, '--expected-generation', owner, '--expected-activation-date', day)
    expect(result.returncode == 0 and fx.sql('SELECT enabled FROM press_race_control;') == 'f',
           '원본 없이 HTTP/결과 복구만 재개하지 못했다: ' + result.stdout + result.stderr)
    expect(sh('docker', 'exec', target, 'cat', '/app/press-admission').stdout.startswith('OPEN ')
           and (fx.dir / '.press-deploy.json').exists(), '확인할 복구 기록을 잃었거나 현재 HTTP 입력이 막혔다')


def scenario_first_activation_uncertain_is_preserved(fx):
    first_deployment_fixture(fx)
    (fx.state / 'fail-first-activation').touch()
    result = fx.deploy()
    expect(result.returncode == 1, '최초 활성화 결과 불확실을 성공으로 처리했다')
    journal_file = fx.dir / '.press-deploy.json'
    raw = journal_file.read_bytes()
    journal = json.loads(raw)
    expect(journal['phase'] == 'FIRST_ACTIVATION_ATTEMPTED' and journal['active_removed'],
           '원본 정리 후 최초 활성화 의도를 보존하지 않았다')
    target = fx.project + '-app-green'
    admission = sh('docker', 'exec', target, 'cat', '/app/press-admission').stdout
    updates = (fx.state / 'control-updates').read_text()
    result = fx.tool('emergency-stop', target)
    expect(result.returncode == 1 and 'Initial activation is unconfirmed' in result.stderr,
           '후발 활성화 SQL 가능성을 무시하고 OFF 완료를 주장했다')
    expect(fx.tool('finalize', target).returncode == 1, '최초 활성화 SQL을 자동 재실행했다')
    expect(journal_file.read_bytes() == raw and (fx.state / 'control-updates').read_text() == updates
           and sh('docker', 'exec', target, 'cat', '/app/press-admission').stdout == admission,
           '미확정 최초 활성화가 기록을 잃었거나 임의로 상태를 변경했다')


def scenario_active_removal_response_lost(fx):
    operations_fixture(fx)
    day = fx.sql('SELECT activation_date FROM press_race_control;')
    (fx.state / 'lose-active-rm-response').touch()
    result = fx.deploy()
    expect(result.returncode == 1 and not fx.exists('app-blue') and fx.running('app-green'),
           '이전 서버 삭제 응답 유실이 재현되지 않았다: ' + result.stdout + result.stderr)
    path = fx.dir / '.press-deploy.json'
    raw = path.read_bytes()
    journal = json.loads(raw)
    expect(not journal.get('active_removed') and journal['removal_intent']['phase'] == 'REMOVE_ATTEMPTED'
           and journal['removal_intent']['container_id'] == journal['active_identity']['id'],
           '삭제 전에 고정 ID와 제거 의도를 보존하지 않았다')
    (fx.state / 'fail-retirement-query').touch()
    target = fx.project + '-app-green'
    expect(fx.tool('emergency-stop', target).returncode == 1 and path.read_bytes() == raw,
           'Docker 조회 실패를 ID 부재로 추측했다')
    expect(fx.sql('SELECT enabled FROM press_race_control;') == 't', '미확인 정산이 경쟁 상태를 바꿨다')
    (fx.state / 'fail-retirement-query').unlink()
    result = fx.tool('emergency-stop', target)
    expect(result.returncode == 0 and fx.sql('SELECT enabled FROM press_race_control;') == 'f',
           '삭제된 고정 ID 정산 뒤 현재 서버 긴급 중단 실패: ' + result.stdout + result.stderr)
    confirmed = json.loads(path.read_text())
    expect(confirmed['active_removed'] and confirmed['phase'] == 'EMERGENCY_OFF_CONFIRMED'
           and confirmed['removal_intent']['phase'] == 'REMOVED_CONFIRMED', '제거와 중단 정산을 보존하지 않았다')
    expect(fx.sql('SELECT active_server_generation FROM press_race_control;') == journal['target_generation']
           and fx.sql('SELECT activation_date FROM press_race_control;') == day,
           '정산이 과거 UUID나 다른 날짜로 제어를 바꿨다')
    expect(sh('docker', 'exec', target, 'cat', '/app/press-admission').stdout.startswith('CLOSED '),
           '중단 완료 뒤 현재 서버 입력이 열려 있다')


def scenario_stop_reconcile_after_open(fx):
    operations_fixture(fx)
    current = fx.sql("SELECT (clock_timestamp() AT TIME ZONE 'Asia/Seoul')::date;")
    active = fx.project + '-app-blue'
    arguments = ('--expected-generation', '11111111-1111-1111-1111-111111111111',
                 '--expected-activation-date', current)
    (fx.state / 'fail-readback-after-open').touch()
    result = fx.tool('stop', active, *arguments)
    expect(result.returncode == 1, 'OPEN 응답/재조회 유실을 완료로 보고했다')
    journal = json.loads((fx.dir / '.press-deploy.json').read_text())
    expect(journal['kind'] == 'stop' and journal['phase'] == 'HTTP_OPEN_ATTEMPTED', '중단 재개 의도를 보존하지 않았다')
    expect(fx.sql('SELECT enabled FROM press_race_control;') == 'f', '실패한 확인을 이유로 경쟁을 재활성화했다')
    (fx.state / 'fail-gate-read').unlink()
    updates = (fx.state / 'control-updates').read_text()
    expect(fx.tool('resume-off', active, *arguments).returncode == 1, '배포 복구 명령이 단독 중단 기록을 잘못 처리했다')
    result = fx.tool('reconcile-control', active, *arguments)
    expect(result.returncode == 0 and not (fx.dir / '.press-deploy.json').exists(),
           '중단의 명시적 정산 실패: ' + result.stdout + result.stderr)
    expect((fx.state / 'control-updates').read_text() == updates, '이미 확정한 중단의 DB 변경을 재실행했다')


def activation_arguments(fx):
    current = fx.sql("SELECT (clock_timestamp() AT TIME ZONE 'Asia/Seoul')::date;")
    return ('--expected-generation', '11111111-1111-1111-1111-111111111111',
            '--expected-activation-date', current)


def scenario_activation_reconcile_committed(fx):
    operations_fixture(fx, enabled=False)
    active, arguments = fx.project + '-app-blue', activation_arguments(fx)
    (fx.state / 'lose-handoff-response').touch()
    (fx.state / 'fail-proof-after-update').touch()
    result = fx.tool('activate', active, *arguments)
    expect(result.returncode == 1 and fx.sql('SELECT enabled FROM press_race_control;') == 't',
           '활성화 커밋 뒤 검증 실패가 재현되지 않았다')
    journal = json.loads((fx.dir / '.press-deploy.json').read_text())
    expect(journal['desired_control']['activation_date'] == fx.sql("SELECT (clock_timestamp() AT TIME ZONE 'Asia/Seoul')::date;"),
           '즉시 ON의 내부 날짜를 보존하지 않았다')
    (fx.state / 'fail-runtime-probe').unlink()
    expect(fx.tool('activate', active, *arguments).returncode == 1, '남은 의도를 무시하고 새 활성화를 시작했다')
    updates = (fx.state / 'control-updates').read_text()
    result = fx.tool('reconcile-control', active, *arguments)
    expect(result.returncode == 0 and not (fx.dir / '.press-deploy.json').exists(),
           '커밋된 활성화 정산 실패: ' + result.stdout + result.stderr)
    expect((fx.state / 'control-updates').read_text() == updates, '활성화 정산이 ON SQL을 재실행했다')


def scenario_activation_reconcile_unconfirmed(fx):
    operations_fixture(fx, enabled=False)
    active, arguments = fx.project + '-app-blue', activation_arguments(fx)
    (fx.state / 'fail-before-control-update').touch()
    result = fx.tool('activate', active, *arguments)
    expect(result.returncode == 1 and fx.sql('SELECT enabled FROM press_race_control;') == 'f',
           '미적용 활성화 실패가 재현되지 않았다')
    journal = (fx.dir / '.press-deploy.json').read_bytes()
    updates = (fx.state / 'control-updates').read_text()
    admission = sh('docker', 'exec', active, 'cat', '/app/press-admission').stdout
    result = fx.tool('reconcile-control', active, *arguments)
    expect(result.returncode == 1 and 'Activation outcome is unconfirmed' in result.stderr,
           '변경 전 DB 조회를 활성화 취소 완료로 처리했다: ' + result.stdout + result.stderr)
    expect((fx.dir / '.press-deploy.json').read_bytes() == journal,
           '미확정 활성화의 원래 의도 기록을 보존하지 않았다')
    expect(fx.tool('activate', active, *arguments).returncode == 1,
           '미확정 의도가 있는데 새 활성화를 시작했다')
    expect(fx.sql('SELECT enabled FROM press_race_control;') == 'f'
           and (fx.state / 'control-updates').read_text() == updates
           and sh('docker', 'exec', active, 'cat', '/app/press-admission').stdout == admission,
           '미확정 활성화 정산이 DB 또는 입력 허용 상태를 변경했다')


def scenario_old_image(fx):
    fx.start_app('app-blue')
    (fx.state / 'old-image').touch()
    result = fx.deploy()
    expect(result.returncode == 1 and not fx.exists('app-green'), '세대 계약 없는 이미지를 기동했다')
    expect(fx.sql('SELECT active_server_generation FROM press_race_control;') ==
           '11111111-1111-1111-1111-111111111111', '이미지 거절 전 DB를 변경했다')


def assert_image_failure_allows_retry(fx, marker):
    fx.start_app('app-blue')
    failure = fx.state / marker
    failure.touch()
    result = fx.deploy()
    expect(result.returncode == 1, '이미지 준비 실패를 성공 처리했다')
    expect(fx.running('app-blue') and not fx.exists('app-green'), '이미지 준비 실패 전에 컨테이너를 교체했다')
    expect(fx.sql('SELECT active_server_generation FROM press_race_control;') ==
           '11111111-1111-1111-1111-111111111111', '이미지 준비 실패 전에 DB 소유권을 변경했다')
    expect(not (fx.dir / '.press-deploy.json').exists(), '이미지 준비 실패가 다음 배포를 막는다')
    failure.unlink()
    retry = fx.deploy()
    expect(retry.returncode == 0, f'이미지 준비 복구 뒤 재배포 실패: {retry.stdout}{retry.stderr}')


def scenario_image_pull_fails(fx):
    assert_image_failure_allows_retry(fx, 'fail-image-pull')


def scenario_image_inspect_fails(fx):
    assert_image_failure_allows_retry(fx, 'fail-image-inspect')


def bootstrap_fixture(fx):
    # Reproduce the current development server: no configured UUID/contract label, DB owner NULL.
    compose = yaml.safe_load((fx.dir / 'compose.yml').read_text())
    blue = compose['services']['app-blue']
    blue['environment']['PHEEEEW_PRESS_RACE_SERVER_GENERATION'] = ''
    blue.pop('labels')
    (fx.dir / 'compose.yml').write_text(yaml.safe_dump(compose))
    fx.sql('UPDATE press_race_control SET active_server_generation = NULL;')
    fx.start_app('app-blue')


def scenario_bootstrap(fx):
    bootstrap_fixture(fx)
    result = fx.deploy()
    expect(result.returncode == 0, f'초기 지정 실패: {result.stdout}{result.stderr}')
    expect(fx.sql('SELECT active_server_generation IS NOT NULL AND NOT enabled FROM press_race_control;') == 't',
           '초기 지정이 없거나 경쟁을 켰다')
    expect(fx.pointer_color() == 'app-green' and not fx.exists('app-blue'), '초기 서버 전환 실패')


def scenario_bootstrap_restore(fx):
    bootstrap_fixture(fx)
    fx.fail_reload()
    result = fx.deploy()
    expect(result.returncode == 1, '초기 지정 실패를 성공으로 처리했다')
    expect(fx.sql('SELECT active_server_generation IS NULL AND NOT enabled FROM press_race_control;') == 't',
           '초기 NULL/OFF 상태로 복귀하지 않았다')
    expect(fx.running('app-blue') and not fx.exists('app-green'), '초기 복귀의 서버 정리 실패')
    expect('old HTTP input remains unavailable' in result.stderr, 'NULL 복귀를 HTTP 정상화처럼 보고했다')


def scenario_legacy_migration(fx):
    bootstrap_fixture(fx)
    fx.sql("UPDATE press_race_control SET active_server_generation = '11111111-1111-1111-1111-111111111111';")
    (fx.state / 'allow-legacy-generation').touch()
    result = fx.deploy()
    expect(result.returncode == 0, f'임시 복구 세대의 명시적 세대 이행 실패: {result.stdout}{result.stderr}')
    expect(fx.pointer_color() == 'app-green' and not fx.exists('app-blue'), '기존 서버의 최초 이행 실패')


def legacy_fixture(fx):
    bootstrap_fixture(fx)
    fx.sql("UPDATE press_race_control SET active_server_generation = '11111111-1111-1111-1111-111111111111';")
    (fx.state / 'allow-legacy-generation').touch()


def scenario_legacy_restore(fx):
    legacy_fixture(fx)
    fx.fail_reload()
    result = fx.deploy()
    expect(result.returncode == 1, '최초 이행 실패를 성공 처리했다')
    expect(fx.sql('SELECT active_server_generation FROM press_race_control;') ==
           '11111111-1111-1111-1111-111111111111', '기존 실행 세대를 복원하지 못했다')
    expect(fx.running('app-blue') and not fx.exists('app-green'), '같은 실행 인스턴스로 복귀하지 못했다')
    expect('restart resilience still requires migration' in result.stderr, '임시 복귀를 영구 해결로 보고했다')


def scenario_legacy_restart_blocks_restore(fx):
    legacy_fixture(fx)
    (fx.state / 'restart-active-on-reload').touch()
    result = fx.deploy()
    expect(result.returncode == 1, '기존 프로세스 재시작을 성공 처리했다')
    expect(fx.running('app-blue') and fx.running('app-green'), '재시작된 구 서버의 UUID를 믿고 새 서버를 제거했다')
    expect((fx.dir / '.press-deploy.json').exists(), '불확실한 복귀 기록을 삭제했다')
    expect(fx.sql('SELECT active_server_generation FROM press_race_control;') !=
           '11111111-1111-1111-1111-111111111111', '재시작된 기존 서버에 과거 세대를 복원했다')


def scenario_legacy_replacement_blocks_restore(fx):
    legacy_fixture(fx)
    (fx.state / 'replace-active-on-reload').touch()
    result = fx.deploy()
    expect(result.returncode == 1, '기존 컨테이너 교체를 성공 처리했다')
    expect(fx.exists('app-blue') and fx.running('app-green'), '교체된 구 서버를 믿고 새 서버를 제거했다')
    expect((fx.dir / '.press-deploy.json').exists(), '원본이 사라졌는데 전환 기록을 삭제했다')
    expect(fx.sql('SELECT active_server_generation FROM press_race_control;') !=
           '11111111-1111-1111-1111-111111111111', '다른 컨테이너에 과거 세대를 복원했다')


def scenario_legacy_requires_option(fx):
    legacy_fixture(fx)
    (fx.state / 'allow-legacy-generation').unlink()
    result = fx.deploy()
    expect(result.returncode == 1 and not fx.exists('app-green'), '기본 배포가 자동으로 최초 이행했다')


def scenario_runtime_mismatch(fx):
    fx.start_app('app-blue')
    (fx.state / 'mismatch-runtime-probe').touch()
    result = fx.deploy()
    expect(result.returncode == 1 and not fx.exists('app-green'), '설정 UUID만 믿고 실제 세대 불일치를 허용했다')
    expect(not (fx.dir / '.press-deploy.json').exists(), '검증하지 못한 소유자로 전환 기록을 만들었다')


def scenario_runtime_probe_fails(fx):
    legacy_fixture(fx)
    (fx.state / 'fail-runtime-probe').touch()
    result = fx.deploy()
    expect(result.returncode == 1 and not fx.exists('app-green'), '실행 UUID를 확인하지 못했는데 이행했다')


def scenario_stable_restart(fx):
    scenario_normal(fx)
    generation = fx.sql('SELECT active_server_generation FROM press_race_control;')
    result = fx.compose_apps('restart', 'app-green')
    expect(result.returncode == 0, '같은 컨테이너를 재시작하지 못했다')
    actual = sh('docker', 'inspect', fx.project + '-app-green', '--format',
                '{{range .Config.Env}}{{println .}}{{end}}').stdout
    expect('PHEEEEW_PRESS_RACE_SERVER_GENERATION=' + generation in actual, '재시작 후 명시적 세대를 잃었다')
    expect(fx.sql('SELECT active_server_generation FROM press_race_control;') == generation, '재시작이 소유권을 바꿨다')


def scenario_next_replacement(fx):
    scenario_normal(fx)
    previous = fx.sql('SELECT active_server_generation FROM press_race_control;')
    result = fx.deploy()
    expect(result.returncode == 0, f'다음 배포 실패: {result.stdout}{result.stderr}')
    expect(fx.pointer_color() == 'app-blue' and not fx.exists('app-green'), '다음 컨테이너 교체 실패')
    expect(fx.sql('SELECT active_server_generation FROM press_race_control;') != previous, '새 컨테이너가 과거 세대를 재사용했다')


def scenario_owner_traffic_mismatch(fx):
    fx.start_app('app-blue')
    fx.edge('app-blue', upstream='app-green')
    result = fx.deploy()
    expect(result.returncode == 1 and not fx.exists('app-green'), '실제 트래픽과 전환 파일 불일치를 무시했다')
    expect(not (fx.dir / '.press-deploy.json').exists(), '트래픽 불일치에서 소유권 전환을 준비했다')


def scenario_replaced_target_is_preserved(fx):
    fx.start_app('app-blue')
    (fx.state / 'replace-target-on-reload').touch()
    result = fx.deploy()
    expect(result.returncode == 1, '승격 후 대상 교체를 성공 처리했다')
    expect(fx.running('app-blue') and fx.exists('app-green'), '다른 ID의 대상 컨테이너를 삭제했다')
    expect((fx.dir / '.press-deploy.json').exists() and fx.backup_exists(), '불확실한 대상 교체 기록을 삭제했다')


def scenario_replaced_active_is_preserved(fx):
    fx.start_app('app-blue')
    (fx.state / 'replace-active-before-cleanup').touch()
    result = fx.deploy()
    expect(result.returncode == 1, '원본 교체 뒤 성공 정리를 진행했다')
    expect(fx.exists('app-blue') and fx.running('app-green'), '다른 ID의 원본 컨테이너를 삭제했다')
    expect((fx.dir / '.press-deploy.json').exists() and fx.backup_exists(), '성공 정리의 불확실한 기록을 삭제했다')


def scenario_probe_response_limit(fx):
    fx.start_app('app-blue')
    (fx.state / 'flood-runtime-probe').touch()
    result = fx.deploy()
    expect(result.returncode == 1 and not fx.exists('app-green'), '세대 조회 응답 상한을 넘겼는데 배포했다')
    expect('exceeded response limit' in result.stderr, '응답 읽기 상한으로 중단하지 않았다')


def scenario_probe_invalid_frame(fx):
    fx.start_app('app-blue')
    (fx.state / 'invalid-runtime-frame').touch()
    result = fx.deploy()
    expect(result.returncode == 1 and not fx.exists('app-green'), '미지원 프레임을 받아 배포했다')


def scenario_not_reflected(fx):
    fx.point_at('set $upstream_app app-blue;\n')
    fx.start_app('app-blue')
    fx.edge('app-green', upstream='app-blue')
    result = fx.deploy()
    expect(result.returncode == 1, f'종료 코드 {result.returncode}')
    expect(fx.pointer_color() == 'app-blue', f'전환 파일 {fx.pointer_color()}')
    expect(fx.running('app-blue'), 'app-blue 가 내려갔다')
    expect(not fx.exists('app-green'), 'app-green 이 남아 있다')
    expect('전환이 반영되지 않았습니다' in result.stdout, '미반영 안내가 없다')


def scenario_switched_but_503(fx):
    fx.point_at('set $upstream_app app-blue;\n')
    fx.start_app('app-blue')
    fx.edge('app-green', code='503')
    result = fx.deploy()
    expect(result.returncode == 1, f'종료 코드 {result.returncode}')
    expect(fx.pointer_color() == 'app-blue', f'전환 파일 {fx.pointer_color()}')
    expect(fx.running('app-blue'), 'app-blue 가 내려갔다')
    expect(not fx.exists('app-green'), 'app-green 이 남아 있다')


def scenario_switched_but_bad_body(fx):
    fx.point_at('set $upstream_app app-blue;\n')
    fx.start_app('app-blue')
    fx.edge('app-green', body='{"nickname":"probe"}')
    result = fx.deploy()
    expect(result.returncode == 1, f'종료 코드 {result.returncode}')
    expect(fx.pointer_color() == 'app-blue', f'전환 파일 {fx.pointer_color()}')
    expect(fx.running('app-blue'), 'app-blue 가 내려갔다')
    expect(not fx.exists('app-green'), 'app-green 이 남아 있다')


def scenario_restore_fail(fx):
    fx.point_at('set $upstream_app app-blue;\n')
    fx.start_app('app-blue')
    fx.fail_reload(times=9)
    result = fx.deploy()
    expect(result.returncode == 1, f'종료 코드 {result.returncode}')
    expect('복구에 실패했습니다' in result.stdout, '복구 실패 보고가 없다')
    expect('전환 파일:' in result.stdout and '엣지 응답:' in result.stdout,
           '디스크와 실제 상태를 함께 보고하지 않는다')
    expect(fx.exists('app-green'), 'app-green 을 지웠다')
    expect(fx.running('app-blue'), 'app-blue 를 지웠다')


def scenario_retired_pointer(fx):
    fx.point_at('set $upstream_app app;\n')
    result = fx.deploy()
    expect(result.returncode == 1, f'종료 코드 {result.returncode}')
    expect('전환 파일의 값이 올바르지 않습니다' in result.stdout, '이관 전 값을 거부하지 않는다')
    expect(not fx.exists('app-blue') and not fx.exists('app-green'),
           '이관 전 값인데 앱을 띄웠다')


def scenario_invalid_pointer(fx):
    fx.point_at('set $upstream_app nonsense_value;\n')
    result = fx.deploy()
    expect(result.returncode == 1, f'종료 코드 {result.returncode}')
    expect('전환 파일의 값이 올바르지 않습니다' in result.stdout, '이상값 안내가 없다')
    expect(not fx.exists('app-blue') and not fx.exists('app-green'),
           '이상값인데 앱을 띄웠다')


def scenario_empty_pointer(fx):
    fx.point_at('\n')
    result = fx.deploy()
    expect(result.returncode == 1, f'종료 코드 {result.returncode}')
    expect('빈 값' in result.stdout, '빈 값 안내가 없다')


def scenario_drain_check_fails(fx):
    fx.point_at('set $upstream_app app-blue;\n')
    fx.start_app('app-blue')
    fx.fail_drain()
    result = fx.deploy()
    expect(result.returncode == 0, f'종료 코드 {result.returncode}\n{result.stdout}')
    expect(fx.pointer_color() == 'app-green', f'전환 파일 {fx.pointer_color()}')
    expect(fx.running('app-green'), 'app-green 이 내려갔다')
    expect(not fx.exists('app-blue'), '워커 검사가 실패했는데 app-blue 를 남겼다')
    expect('graceful 하게 내립니다' in result.stdout, 'graceful 종료 안내가 없다')
    expect('한 번도 확인하지 못했습니다' in result.stdout, '미확인 상태를 알리지 않는다')
    expect('개발 배포를 마쳤습니다' in result.stdout, '전환이 성공했는데 배포를 실패로 끝냈다')


def scenario_broken_json_body(fx):
    fx.point_at('set $upstream_app app-blue;\n')
    fx.start_app('app-blue')
    fx.edge('app-green', body='{"available":true garbage')
    result = fx.deploy()
    expect(result.returncode == 1, f'종료 코드 {result.returncode}')
    expect(fx.pointer_color() == 'app-blue', f'전환 파일 {fx.pointer_color()}')
    expect(fx.running('app-blue'), '깨진 JSON 인데 app-blue 를 지웠다')
    expect(not fx.exists('app-green'), 'app-green 이 남아 있다')


def scenario_normal_waits_for_inflight(fx):
    fx.point_at('set $upstream_app app-blue;\n')
    fx.start_app('app-blue')
    with fx.capture_processes_on_failure() as processes:
        inflight = processes['http'] = fx.slow_request(seconds=35)
        fx.wait_inflight('app-blue')
        deploy = processes['deploy'] = fx.deploy_async()
        fx.wait_edge_serves('app-green')
        expect((fx.appstate / 'inflight-app-blue').exists(),
               'green 으로 전환된 시점에 blue 요청이 이미 끝나 겹침을 확인하지 못했다')
        fx.assert_alive_until_inflight_done('app-blue')
        result = fx.wait_deploy(deploy)
        code = inflight.communicate(timeout=120)[0].strip()
        expect(inflight.returncode == 0 and code == '200',
               f'진행 중이던 요청: curl exit={inflight.returncode}, HTTP={code}')
        expect(result.returncode == 0, f'종료 코드 {result.returncode}\n{result.stdout}')
        expect(fx.pointer_color() == 'app-green', f'전환 파일 {fx.pointer_color()}')
        expect(fx.running('app-green'), 'app-green 이 떠 있지 않다')
        expect(not fx.exists('app-blue'), '요청이 끝났는데 app-blue 가 남아 있다')
        expect(result.stdout.index('이전 요청이 모두 끝났습니다')
               < result.stdout.index('개발 배포를 마쳤습니다'), '드레인 확인보다 배포 성공이 먼저다')


def scenario_rollback_waits_for_inflight(fx):
    fx.point_at('set $upstream_app app-blue;\n')
    fx.start_app('app-blue')
    fx.edge('app-green', code='503')
    # Reload acknowledges the signal before the master handles it; hold that window open.
    (fx.state / 'delay-restore-reload').touch()
    with fx.capture_processes_on_failure() as processes:
        deploy = processes['deploy'] = fx.deploy_async()
        fx.wait_edge_serves('app-green')
        inflight = processes['http'] = fx.slow_request(seconds=40)
        fx.wait_inflight('app-green')
        fx.wait_edge_serves('app-blue')
        expect((fx.appstate / 'inflight-app-green').exists(),
               'blue 로 복구된 시점에 green 요청이 이미 끝나 겹침을 확인하지 못했다')
        fx.assert_alive_until_inflight_done('app-green')
        result = fx.wait_deploy(deploy)
        code = inflight.communicate(timeout=150)[0].strip()
        expect(inflight.returncode == 0 and code == '200',
               f'green 으로 가던 요청: curl exit={inflight.returncode}, HTTP={code}')
        expect(result.returncode == 1, f'종료 코드 {result.returncode}')
        expect(fx.pointer_color() == 'app-blue', f'전환 파일 {fx.pointer_color()}')
        expect(fx.running('app-blue'), 'app-blue 가 내려갔다')
        expect(not fx.exists('app-green'), '요청이 끝났는데 app-green 이 남아 있다')
        expect('app-green 으로 향하던 요청이 모두 끝났습니다.' in result.stdout,
               f'기록한 worker PID 종료를 확인하지 못했다: {result.stdout}')
        expect(result.stdout.index('복구했고 정상 응답을 확인했습니다')
               < result.stdout.index('app-green 을 정리했습니다'),
               '복구 확인보다 새 서버 삭제가 먼저다')


def assert_target_api_rejected(fx, mode):
    fx.point_at('set $upstream_app app-blue;\n')
    fx.start_app('app-blue')
    fx.break_api('app-green', mode)
    started = time.monotonic()
    result = fx.deploy()
    elapsed = time.monotonic() - started
    expect(result.returncode == 1, f'종료 코드 {result.returncode}\n{result.stdout}')
    expect(fx.pointer_color() == 'app-blue', f'전환 파일 {fx.pointer_color()}')
    expect(fx.running('app-blue'), 'app-blue 가 내려갔다')
    expect(not fx.exists('app-green'), 'app-green 이 남아 있다')
    expect(not fx.backup_exists(), '전환 전에 멈췄는데 백업이 생겼다')
    expect('최소 API 를 응답하지 않습니다' in result.stdout, '최소 API 실패 안내가 없다')
    return elapsed


def scenario_target_api_fails(fx):
    assert_target_api_rejected(fx, '503')


def scenario_target_api_times_out(fx):
    elapsed = assert_target_api_rejected(fx, 'stall')
    expect(elapsed < 60, f'응답 없는 API 검사가 제한 시간을 넘겼다: {elapsed:.1f}초')


def scenario_compose_services_match(fx):
    compose = yaml.safe_load((REPO / 'BE/docker/compose.dev.yml').read_text())
    services = set(compose['services'])
    expect(services == {'app-blue', 'app-green'},
           f'배포 스크립트가 왕복하는 두 색과 compose.dev.yml 의 {sorted(services)} 가 다르다')
    for color in ('blue', 'green'):
        expect(compose['services'][f'app-{color}'].get('profiles') == [color],
               f'app-{color} 의 profiles 가 [{color}] 가 아니다')


def scenario_worker_pid_matching(fx):
    script = remote_script()
    functions = script[script.index('read_nginx_processes()'):script.index('wait_drained()')]
    result = sh('bash', '-s', cwd=fx.dir, input='''set -eu
NGINX="docker compose -f compose.nginx.yml"
''' + functions + '''
capture_workers
[ -n "$DRAIN_WORKERS" ]
[ "$(count_previous_workers)" -ge 0 ]
read_nginx_processes() { printf '%s\\n' '112 nginx: worker process' '34 changed-process-title'; }
DRAIN_WORKERS='12 34 '
[ "$(count_previous_workers)" -eq 1 ]
DRAIN_WORKERS='12 '
[ "$(count_previous_workers)" -eq 0 ]
DRAIN_WORKERS=
if count_previous_workers; then exit 1; fi
''')
    expect(result.returncode == 0, f'worker PID 판정 실패: {result.stdout}{result.stderr}')


ON_SCENARIOS = (
    ('경쟁 ON v2 정상 전환', scenario_competition_on_v2),
    ('ON v2 DB 커밋 응답 유실', scenario_v2_lost_db_response),
    ('ON v2 OPEN 응답 유실', scenario_v2_lost_open_response),
    ('ON v2 개방 전 실패 자동 복귀', scenario_v2_reload_rollback),
    ('ON v2 개방 후 실패 안전 중단', scenario_v2_open_then_failure),
    ('ON v2 늦은 OPEN 차단과 명시적 OFF 복구', scenario_v2_delayed_open_and_manual_off_resume),
    ('ON v2 원본 재시작은 자동 복귀 차단', scenario_v2_restart_blocks_restore),
    ('v2 긴급 중단과 즉시 경쟁 사용', scenario_v2_stop_and_activate),
    ('최초 v1에서 v2 OFF 이행 실패의 명시적 복구', scenario_first_v2_off_manual_recovery),
    ('v2 관리 명령의 정확한 대상 검증', scenario_operations_exact_targets),
    ('최초 개발 배포 마무리 후 경쟁 사용', scenario_first_development_deployment),
    ('일반 OFF 개발 배포는 OFF 유지', scenario_ordinary_off_stays_off),
    ('최초 개발 배포 이후 정상 ON 재배포', scenario_first_then_normal_on),
    ('개발 환경 미확인 시 최초 활성화 금지', scenario_first_deployment_requires_development),
    ('최초 개발 배포의 모니터링 실패는 기록 보존', scenario_first_monitoring_failure),
    ('정상 ON 배포의 모니터링 실패는 상태 보존', scenario_on_monitoring_failure),
    ('미확정 최초 활성화는 중단 완료 주장 없이 보존', scenario_first_activation_uncertain_is_preserved),
    ('이전 서버 삭제 응답 유실의 고정 ID 정산', scenario_active_removal_response_lost),
    ('v2 중단 OPEN 응답 유실의 명시적 정산', scenario_stop_reconcile_after_open),
    ('v2 활성화 커밋 뒤 검증 실패 정산', scenario_activation_reconcile_committed),
    ('v2 미확정 활성화 정산은 기록 보존', scenario_activation_reconcile_unconfirmed),
)

LEGACY_SCENARIOS = (
    ('정상 전환', scenario_normal),
    ('DB 커밋 응답 유실 복구', scenario_lost_db_response),
    ('DB 복구 실패 시 서버 보존', scenario_db_restore_fails),
    ('세대 전환 직후 새 서버 종료 복구', scenario_target_dies_after_handoff),
    ('경쟁 ON 배포 거부', scenario_competition_on),
    ('세대 미지원 이미지 거부', scenario_old_image),
    ('이미지 pull 실패 후 재배포', scenario_image_pull_fails),
    ('이미지 inspect 실패 후 재배포', scenario_image_inspect_fails),
    ('NULL 세대 최초 지정', scenario_bootstrap),
    ('최초 지정 실패 시 NULL 복귀', scenario_bootstrap_restore),
    ('임시 복구 세대의 최초 이행', scenario_legacy_migration),
    ('최초 이행 실패의 동일 프로세스 복귀', scenario_legacy_restore),
    ('기존 랜덤 세대 재시작 시 복귀 중단', scenario_legacy_restart_blocks_restore),
    ('기존 랜덤 세대 컨테이너 교체 시 복귀 중단', scenario_legacy_replacement_blocks_restore),
    ('최초 이행 옵션 없으면 거부', scenario_legacy_requires_option),
    ('실행 세대 불일치 거부', scenario_runtime_mismatch),
    ('실행 세대 조회 실패 거부', scenario_runtime_probe_fails),
    ('명시적 세대 컨테이너 재시작', scenario_stable_restart),
    ('다음 새 컨테이너 교체', scenario_next_replacement),
    ('트래픽과 전환 파일 불일치 거부', scenario_owner_traffic_mismatch),
    ('복귀 중 같은 이름의 다른 대상 보존', scenario_replaced_target_is_preserved),
    ('성공 정리 중 같은 이름의 다른 원본 보존', scenario_replaced_active_is_preserved),
    ('세대 조회 응답 상한', scenario_probe_response_limit),
    ('세대 조회 미지원 프레임 거부', scenario_probe_invalid_frame),
    ('새 버전 기동 실패', scenario_start_fail),
    ('새 버전이 최소 API 를 응답하지 않음', scenario_target_api_fails),
    ('새 버전의 최소 API 응답이 멈춤', scenario_target_api_times_out),
    ('nginx -t 실패', scenario_conf_test_fail),
    ('reload 명령 실패', scenario_reload_fail),
    ('전환 미반영', scenario_not_reflected),
    ('전환 후 503', scenario_switched_but_503),
    ('전환 후 잘못된 본문', scenario_switched_but_bad_body),
    ('복구 실패', scenario_restore_fail),
    ('워커 검사 실패', scenario_drain_check_fails),
    ('전환 후 깨진 JSON', scenario_broken_json_body),
    ('정상 전환이 진행 중 요청을 기다린다', scenario_normal_waits_for_inflight),
    ('롤백이 진행 중 요청을 기다린다', scenario_rollback_waits_for_inflight),
    ('이관 전 전환 파일 거부', scenario_retired_pointer),
    ('전환 파일 이상값', scenario_invalid_pointer),
    ('전환 파일 빈 값', scenario_empty_pointer),
    ('compose 의 색 이름이 배포 스크립트와 일치', scenario_compose_services_match),
    ('worker PID 캡처와 정확한 종료 판정', scenario_worker_pid_matching),
)

SCENARIOS = ON_SCENARIOS + LEGACY_SCENARIOS
CI_ON_SCENARIOS = {
    '경쟁 ON v2 정상 전환',
    'ON v2 DB 커밋 응답 유실',
    'ON v2 개방 전 실패 자동 복귀',
    '최초 개발 배포 마무리 후 경쟁 사용',
    '이전 서버 삭제 응답 유실의 고정 ID 정산',
}


def reset(fx):
    fx.compose_apps('--profile', 'blue', '--profile', 'green', 'rm', '-sf')
    fx.clear_appstate()
    for path in (fx.dir / '.press-deploy.json', fx.dir / 'press-generations.env',
                 fx.upstream / 'active.conf.bak'):
        path.unlink(missing_ok=True)
    fx.sql("UPDATE press_race_control SET enabled = FALSE, activation_date = NULL, "
           "active_server_generation = '11111111-1111-1111-1111-111111111111';")
    fx.reset_edge()
    fx.fix_nginx_conf()
    fx.restore_app('app-green')
    fx.point_at('set $upstream_app app-blue;\n')
    fx.compose_nginx('exec', '-T', 'nginx', 'nginx', '-s', 'reload')


def main():
    only = sys.argv[1:]
    scenarios = SCENARIOS
    if only == ['--ci']:
        scenarios = tuple(item for item in ON_SCENARIOS if item[0] in CI_ON_SCENARIOS) + LEGACY_SCENARIOS
        only = []
    with tempfile.TemporaryDirectory() as root:
        fx = Fixture(root)
        failures = []
        try:
            fx.point_at('set $upstream_app app-blue;\n')
            fx.start_nginx()
            expect(fx.running('nginx'), 'nginx 가 뜨지 않았다')
            for name, run_scenario in scenarios:
                if only and name not in only:
                    continue
                reset(fx)
                try:
                    run_scenario(fx)
                    print(f'PASS: {name}')
                except Failure as failure:
                    failures.append(name)
                    print(f'FAIL: {name} — {failure}')
                    fx.diagnose()
        finally:
            fx.cleanup()
        if failures:
            print(f'\n{len(failures)}개 실패: {", ".join(failures)}')
            return 1
        print('\n선택한 전환과 복구 분기 통과')
        return 0


if __name__ == '__main__':
    sys.exit(main())
