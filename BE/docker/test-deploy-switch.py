#!/usr/bin/env python3
import os
import shutil
import subprocess
import sys
import tempfile
import time
import uuid
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
    step = next(s for s in workflow['jobs']['deploy']['steps'] if s.get('name') == '배포')
    return step['run'].split("<<'REMOTE'\n", 1)[1].rsplit('\nREMOTE', 1)[0]


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
            text=True, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)

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
            if not self.exists(color):
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
        env = dict(os.environ)
        env['PATH'] = f'{self.shim}:{env["PATH"]}'
        env['REAL_DOCKER'] = shutil.which('docker')
        env['SHIM_DIR'] = str(self.state)
        env['SHIM_POINTER'] = str(self.pointer)
        env['APP_IMAGE'] = 'python:3.13-alpine'
        proc = subprocess.Popen(['bash', '-s'], stdin=subprocess.PIPE,
                                stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                                text=True, env=env, cwd=self.dir)
        proc.stdin.write(script)
        proc.stdin.close()
        proc.stdin = None
        return proc

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
        sh('docker', 'network', 'rm', f'{self.project}-net')


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
    inflight = fx.slow_request(seconds=35)
    fx.wait_inflight('app-blue')
    deploy = fx.deploy_async()
    fx.wait_edge_serves('app-green')
    expect((fx.appstate / 'inflight-app-blue').exists(),
           'green 으로 전환된 시점에 blue 요청이 이미 끝나 겹침을 확인하지 못했다')
    fx.assert_alive_until_inflight_done('app-blue')
    result = fx.wait_deploy(deploy)
    code = inflight.communicate(timeout=120)[0].strip()
    expect(code == '200', f'진행 중이던 요청이 {code} 로 끝났다')
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
    deploy = fx.deploy_async()
    fx.wait_edge_serves('app-green')
    inflight = fx.slow_request(seconds=40)
    fx.wait_inflight('app-green')
    fx.wait_edge_serves('app-blue')
    expect((fx.appstate / 'inflight-app-green').exists(),
           'blue 로 복구된 시점에 green 요청이 이미 끝나 겹침을 확인하지 못했다')
    fx.assert_alive_until_inflight_done('app-green')
    result = fx.wait_deploy(deploy)
    code = inflight.communicate(timeout=150)[0].strip()
    expect(code == '200', f'green 으로 가던 요청이 {code} 로 끝났다')
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


def scenario_compose_services_match(fx):
    compose = yaml.safe_load((REPO / 'BE/docker/compose.dev.yml').read_text())
    services = set(compose['services'])
    expect(services == {'app-blue', 'app-green'},
           f'배포 스크립트가 왕복하는 두 색과 compose.dev.yml 의 {sorted(services)} 가 다르다')
    for color in ('blue', 'green'):
        expect(compose['services'][f'app-{color}'].get('profiles') == [color],
               f'app-{color} 의 profiles 가 [{color}] 가 아니다')


SCENARIOS = (
    ('정상 전환', scenario_normal),
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
)


def reset(fx):
    fx.compose_apps('--profile', 'blue', '--profile', 'green', 'rm', '-sf')
    fx.clear_appstate()
    fx.reset_edge()
    fx.fix_nginx_conf()
    fx.restore_app('app-green')
    fx.point_at('set $upstream_app app-blue;\n')
    fx.compose_nginx('exec', '-T', 'nginx', 'nginx', '-s', 'reload')


def main():
    only = sys.argv[1:]
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
