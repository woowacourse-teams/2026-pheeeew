#!/usr/bin/env python3
import os
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
TARGETS = {
    'dev': {
        'workflow': '.github/workflows/be-dev-deploy.yml',
        'nginx_compose': 'BE/docker/compose.nginx.dev.yml',
        'nginx_conf': 'BE/docker/nginx/api-dev.conf',
        'app_compose': 'BE/docker/compose.dev.yml',
        'host': 'api-dev.pheeeew.com',
        'migrates_from_app': False,
    },
    'prod': {
        'workflow': '.github/workflows/be-deploy.yml',
        'nginx_compose': 'BE/docker/compose.nginx.yml',
        'nginx_conf': 'BE/docker/nginx/api.conf',
        'app_compose': 'BE/docker/compose.prod.yml',
        'host': 'api.pheeeew.com',
        'migrates_from_app': True,
    },
}
TARGET = TARGETS[os.environ.get('DEPLOY_TARGET', 'dev')]
WORKFLOW = REPO / TARGET['workflow']
HOST = TARGET['host']
MIGRATES_FROM_APP = TARGET['migrates_from_app']
HTTP_PORT = 18080
HTTPS_PORT = 18443
PROBE_PATH = '/api/v3/devices/nicknames/availability?nickname=probe'
SLOW_PATH = '/api/v3/devices/slow'
COLORS = ('app-blue', 'app-green')


PREFIX = 'pheeeew-prod' if TARGET['migrates_from_app'] else 'pheeeew-dev'


def step_script(name):
    workflow = yaml.safe_load(WORKFLOW.read_text())
    step = next(s for s in workflow['jobs']['deploy']['steps'] if s.get('name') == name)
    return step['run'].split("<<'REMOTE'\n", 1)[1].rsplit('\nREMOTE', 1)[0]


def remote_script():
    return step_script('배포')


APP = '''
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse, parse_qs
import json
import os
import sys
import time

name = sys.argv[1]
marker = '/appstate/inflight-' + name
badapi = '/appstate/badapi-' + name

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
  *COUNTED*)
    [ -f "$SHIM_DIR/fail-drain" ] && exit 1 ;;
  *"nginx -s reload"*)
    if [ -f "$SHIM_DIR/fail-reload" ]; then
      remaining=$(cat "$SHIM_DIR/fail-reload")
      if [ "$remaining" -gt 0 ]; then
        echo $((remaining - 1)) > "$SHIM_DIR/fail-reload"
        echo "reload failed (injected)" >&2
        exit 1
      fi
    fi ;;
esac
exec "$REAL_DOCKER" "$@"
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
        shutil.copy(REPO / TARGET['nginx_conf'], self.confd / 'default.conf')
        self.reset_edge()

    def _certs(self):
        sh('openssl', 'req', '-x509', '-newkey', 'rsa:2048', '-nodes', '-days', '1',
           '-subj', f'/CN={HOST}',
           '-keyout', str(self.dir / 'origin.key'), '-out', str(self.dir / 'origin.pem'))
        for name in ('origin-pull-ca.pem', 'fullchain.pem', 'chain.pem'):
            shutil.copy(self.dir / 'origin.pem', self.dir / name)
        shutil.copy(self.dir / 'origin.key', self.dir / 'privkey.pem')

    def _shims(self):
        for name, text in (('docker', DOCKER_SHIM), ('curl', CURL_SHIM)):
            path = self.shim / name
            path.write_text(text)
            path.chmod(0o755)

    def _compose(self):
        base = {
            'image': 'python:3.13-alpine',
            'volumes': [f'{self.appstate}:/appstate'],
            'healthcheck': {
                'test': ['CMD', 'wget', '-q', '-O', '-', 'http://127.0.0.1:8080/actuator/health'],
                'interval': '2s', 'timeout': '2s', 'retries': 10, 'start_period': '2s',
            },
            'stop_grace_period': '5s',
            'init': True,
        }
        services = {}
        for color in ('blue', 'green'):
            service = dict(base)
            service['command'] = ['python', '-c', APP, f'app-{color}']
            service['container_name'] = f'{self.project}-app-{color}'
            service['profiles'] = [color]
            services[f'app-{color}'] = service
        (self.dir / 'compose.yml').write_text(yaml.safe_dump({
            'name': self.project,
            'services': services,
            'networks': {'default': {'name': self.project, 'external': True}},
        }))

        model = yaml.safe_load((REPO / TARGET['nginx_compose']).read_text())
        model['name'] = self.project + '-nginx'
        model['networks'] = {'default': {'name': self.project, 'external': True}}
        nginx = model['services']['nginx']
        nginx['container_name'] = f'{self.project}-nginx'
        nginx['restart'] = 'no'
        nginx['ports'] = [f'127.0.0.1:{HTTP_PORT}:80', f'127.0.0.1:{HTTPS_PORT}:443']
        nginx['volumes'] = [
            f'{self.confd}:/etc/nginx/conf.d:ro',
            f'{self.upstream}:/etc/nginx/upstream:ro',
            f'{self.dir}:/etc/ssl/cloudflare:ro',
            f'{self.dir}:/etc/letsencrypt/live/{HOST}:ro',
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
             '--resolve', f'{HOST}:{HTTPS_PORT}:127.0.0.1',
             f'https://{HOST}:{HTTPS_PORT}{SLOW_PATH}?seconds={seconds}'],
            text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, start_new_session=True)

    def served_upstream(self):
        result = sh('curl', '-sk', '-o', '/dev/null', '-D', '-', '-w', '%{http_code}', '-m', '5',
                    '--resolve', f'{HOST}:{HTTPS_PORT}:127.0.0.1',
                    f'https://{HOST}:{HTTPS_PORT}{PROBE_PATH}')
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
        sh('docker', 'network', 'create', self.project)
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

    def shell_env(self):
        env = dict(os.environ)
        env['PATH'] = f'{self.shim}:{env["PATH"]}'
        env['REAL_DOCKER'] = shutil.which('docker')
        env['SHIM_DIR'] = str(self.state)
        env['SHIM_POINTER'] = str(self.pointer)
        env['APP_IMAGE'] = 'python:3.13-alpine'
        return env

    def run_step(self, name):
        script = step_script(name).replace('/opt/pheeeew', str(self.dir)).replace(PREFIX, self.project)
        return subprocess.run(['bash', '-s'], input=script, text=True,
                              capture_output=True, env=self.shell_env(), cwd=self.dir)

    def start_legacy_stack(self):
        network = f'{self.project}_default'
        sh('docker', 'network', 'create', network)
        sh('docker', 'run', '-d', '--name', f'{self.project}-app',
           '--network', network, '--network-alias', 'app',
           '--label', f'com.docker.compose.project={self.project}',
           '-v', f'{self.appstate}:/appstate',
           'python:3.13-alpine', 'python', '-c', APP, 'app')
        deadline = time.monotonic() + 60
        while time.monotonic() < deadline:
            if sh('docker', 'run', '--rm', '--network', network, 'nginx:1.27-alpine',
                  'wget', '-q', '-O', '-', '-T', '3',
                  'http://app:8080/actuator/health').returncode == 0:
                break
            time.sleep(1)
        else:
            raise Failure('이전 앱이 네트워크에서 응답하지 않는다')
        sh('docker', 'run', '-d', '--name', f'{self.project}-nginx',
           '--network', network,
           '--label', f'com.docker.compose.project={self.project}',
           '-p', f'127.0.0.1:{HTTP_PORT}:80', '-p', f'127.0.0.1:{HTTPS_PORT}:443',
           '-v', f'{self.confd}:/etc/nginx/conf.d:ro',
           '-v', f'{self.upstream}:/etc/nginx/upstream:ro',
           '-v', f'{self.dir}:/etc/ssl/cloudflare:ro',
           '-v', f'{self.dir}:/etc/letsencrypt/live/{HOST}:ro',
           'nginx:1.27-alpine')

    def drop_legacy_stack(self):
        sh('docker', 'rm', '-f', f'{self.project}-app', f'{self.project}-nginx')
        sh('docker', 'network', 'rm', f'{self.project}_default')

    def deploy_async(self):
        script = remote_script().replace('/opt/pheeeew', str(self.dir)).replace(PREFIX, self.project)
        proc = subprocess.Popen(['bash', '-s'], stdin=subprocess.PIPE,
                                stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                                text=True, env=self.shell_env(), cwd=self.dir, start_new_session=True)
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
        self.compose_nginx('down', '-v', '--remove-orphans')
        self.compose_apps('--profile', 'blue', '--profile', 'green', 'down', '-v',
                          '--remove-orphans')
        if MIGRATES_FROM_APP:
            self.drop_legacy_stack()
            for color in ('blue', 'green'):
                sh('docker', 'volume', 'rm', f'{self.project}_app-logs-{color}')
        sh('docker', 'network', 'rm', self.project)


class Failure(AssertionError):
    pass


def expect(condition, message):
    if not condition:
        raise Failure(message)


def normal_switch(fx, active, target):
    fx.point_at(f'set $upstream_app {active};\n')
    fx.start_app(active)
    result = fx.deploy()
    expect(result.returncode == 0, f'종료 코드 {result.returncode}\n{result.stdout}{result.stderr}')
    expect(fx.pointer_color() == target, f'전환 파일 {fx.pointer_color()}')
    expect(fx.running(target), f'{target} 이 떠 있지 않다')
    expect(not fx.exists(active), f'{active} 가 남아 있다')
    fx.wait_edge_serves(target)
    expect(not fx.backup_exists(), '백업 파일이 남아 있다')
    expect('이전 요청이 모두 끝났습니다' in result.stdout, '드레인 확인 로그가 없다')


def scenario_normal(fx):
    normal_switch(fx, 'app-blue', 'app-green')


def scenario_reverse(fx):
    normal_switch(fx, 'app-green', 'app-blue')


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
    expect(not fx.backup_exists(), '백업 파일이 남아 있다')
    expect(fx.running('app-blue'), 'app-blue 가 내려갔다')
    expect(not fx.exists('app-green'), 'app-green 이 남아 있다')
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


def scenario_legacy_pointer(fx):
    fx.point_at('set $upstream_app app;\n')
    result = fx.deploy()
    if MIGRATES_FROM_APP:
        expect(result.returncode == 0, f'종료 코드 {result.returncode}\n{result.stdout}')
        expect(fx.pointer_color() == 'app-blue', f'전환 파일 {fx.pointer_color()}')
        expect(fx.running('app-blue'), '이관 경로인데 app-blue 가 뜨지 않았다')
    else:
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
    expect('배포 성공' in result.stdout, '전환이 성공했는데 배포를 실패로 끝냈다')


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
               < result.stdout.index('배포 성공'), '드레인 확인보다 배포 성공이 먼저다')


def scenario_rollback_waits_for_inflight(fx):
    fx.point_at('set $upstream_app app-blue;\n')
    fx.start_app('app-blue')
    fx.edge('app-green', code='503')
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


def prepare_first_migration(fx):
    fx.compose_nginx('down', '--remove-orphans')
    fx.point_at('set $upstream_app app;\n')
    fx.start_legacy_stack()
    fx.wait_edge_serves('app')
    # 실제 서버처럼 준비 단계가 기존 app 존재를 보고 전환 파일을 초기화한다.
    fx.pointer.unlink()
    prep = fx.run_step('배포 전 공유 자원 준비')
    expect(prep.returncode == 0, f'준비 단계 실패\n{prep.stdout}{prep.stderr}')
    expect('공유 네트워크에 연결했습니다' in prep.stdout,
           '이전 앱을 공유 네트워크에 붙이지 않았다')
    expect(fx.pointer_color() == 'app', '기존 앱을 전환 대상으로 초기화하지 않았다')
    nginx = fx.run_step('nginx 배포')
    expect(nginx.returncode == 0, f'nginx 교체 실패\n{nginx.stdout}{nginx.stderr}')
    fx.wait_edge_serves('app')
    # 실패 후 재실행해도 기존 앱 연결과 현재 전환 대상을 유지해야 한다.
    prep = fx.run_step('배포 전 공유 자원 준비')
    expect(prep.returncode == 0, f'준비 단계 재실행 실패\n{prep.stdout}{prep.stderr}')
    expect(fx.pointer_color() == 'app', '준비 재실행이 활성 대상을 바꿨다')
    fx.wait_edge_serves('app')


def scenario_first_migration(fx):
    try:
        prepare_first_migration(fx)
        result = fx.deploy()
        expect(result.returncode == 0, f'종료 코드 {result.returncode}\n{result.stdout}{result.stderr}')
        expect(fx.pointer_color() == 'app-blue', f'전환 파일 {fx.pointer_color()}')
        expect(fx.running('app-blue'), 'app-blue 가 뜨지 않았다')
        expect(not fx.exists('app'), '이전 단일 컨테이너가 남아 있다')
        expect('을 정리했습니다' in result.stdout, '이관 정리 안내가 없다')
        fx.wait_edge_serves('app-blue')
    finally:
        fx.drop_legacy_stack()


def scenario_first_migration_start_fails(fx):
    try:
        prepare_first_migration(fx)
        fx.break_app('app-blue')
        result = fx.deploy()
        expect(result.returncode == 1, f'종료 코드 {result.returncode}\n{result.stdout}{result.stderr}')
        expect(fx.pointer_color() == 'app', '기동 실패인데 전환 파일이 바뀌었다')
        expect(fx.running('app'), '기동 실패인데 이전 앱을 내렸다')
        expect(not fx.exists('app-blue'), '실패한 새 앱이 남아 있다')
        fx.wait_edge_serves('app')
    finally:
        fx.restore_app('app-blue')
        fx.drop_legacy_stack()


def scenario_first_migration_rolls_back(fx):
    try:
        prepare_first_migration(fx)
        fx.edge('app-blue', code='503')
        result = fx.deploy()
        expect(result.returncode == 1, f'종료 코드 {result.returncode}\n{result.stdout}{result.stderr}')
        expect(fx.pointer_color() == 'app', '이전 단일 앱으로 포인터를 복구하지 않았다')
        expect('app 로 복구했고 정상 응답을 확인했습니다' in result.stdout, '앱 복구 확인이 없다')
        expect(fx.running('app'), '복구한 이전 앱을 내렸다')
        expect(not fx.exists('app-blue'), '복구 후 새 앱을 정리하지 않았다')
        fx.wait_edge_serves('app')
    finally:
        fx.drop_legacy_stack()


def scenario_compose_services_match(fx):
    compose = yaml.safe_load((REPO / TARGET['app_compose']).read_text())
    services = set(compose['services'])
    expect(services == {'app-blue', 'app-green'},
           f'배포 스크립트가 왕복하는 두 색과 {TARGET["app_compose"]} 의 {sorted(services)} 가 다르다')
    for color in ('blue', 'green'):
        expect(compose['services'][f'app-{color}'].get('profiles') == [color],
               f'app-{color} 의 profiles 가 [{color}] 가 아니다')


SCENARIOS = (
    ('정상 전환', scenario_normal),
    ('역방향 정상 전환', scenario_reverse),
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
    ('이관 전 전환 파일 처리', scenario_legacy_pointer),
    ('전환 파일 이상값', scenario_invalid_pointer),
    ('전환 파일 빈 값', scenario_empty_pointer),
    ('compose 의 색 이름이 배포 스크립트와 일치', scenario_compose_services_match),
) + ((('최초 이관', scenario_first_migration),
      ('최초 이관 중 새 앱 기동 실패', scenario_first_migration_start_fails),
      ('최초 이관 중 앱 롤백', scenario_first_migration_rolls_back)) if MIGRATES_FROM_APP else ())


def reset(fx):
    fx.compose_apps('--profile', 'blue', '--profile', 'green', 'rm', '-sf')
    fx.clear_appstate()
    fx.reset_edge()
    fx.fix_nginx_conf()
    fx.restore_app('app-green')
    fx.point_at('set $upstream_app app-blue;\n')
    if not fx.running('nginx'):
        fx.start_nginx()
    fx.compose_nginx('exec', '-T', 'nginx', 'nginx', '-s', 'reload')


def main():
    only = sys.argv[1:]
    unknown = set(only) - {name for name, _ in SCENARIOS}
    if unknown:
        print(f'알 수 없는 시나리오: {", ".join(sorted(unknown))}', file=sys.stderr)
        return 2
    with tempfile.TemporaryDirectory() as root:
        fx = Fixture(root)
        failures = []
        try:
            fx.point_at('set $upstream_app app-blue;\n')
            fx.start_nginx()
            expect(fx.running('nginx'), 'nginx 가 뜨지 않았다')
            for name, run_scenario in SCENARIOS:
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
        print('\n전환과 복구 분기 전부 통과')
        return 0


if __name__ == '__main__':
    sys.exit(main())
