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
COLORS = ('app', 'app-blue', 'app-green')
LEGACY = 'pheeeew-dev-app'


def remote_script():
    workflow = yaml.safe_load(WORKFLOW.read_text())
    step = next(s for s in workflow['jobs']['deploy']['steps'] if s.get('name') == '배포')
    return step['run'].split("<<'REMOTE'\n", 1)[1].rsplit('\nREMOTE', 1)[0]


APP = '''
from http.server import BaseHTTPRequestHandler, HTTPServer
import json
import sys
import time

name = sys.argv[1]

class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        if 'slow' in self.path:
            time.sleep(60)
        self.send_response(200)
        self.send_header('Content-Type', 'application/json')
        self.end_headers()
        self.wfile.write(json.dumps({'target': name, 'available': True}).encode())

    def log_message(self, *args):
        pass

HTTPServer(('0.0.0.0', 8080), Handler).serve_forever()
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
        self.upstream = self.dir / 'nginx' / 'upstream'
        for path in (self.state, self.shim, self.confd):
            path.mkdir(parents=True)
        self.upstream.mkdir(parents=True)
        self.pointer = self.upstream / 'active.conf'
        self.made_legacy = False
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

    def slow_request(self):
        return subprocess.Popen(
            ['curl', '-sk', '-o', '/dev/null', '-w', '%{http_code}', '-m', '120',
             '--resolve', f'api-dev.pheeeew.com:{HTTPS_PORT}:127.0.0.1',
             f'https://api-dev.pheeeew.com:{HTTPS_PORT}/api/v3/devices/slow'],
            text=True, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)

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

    def start_legacy(self):
        sh('docker', 'run', '-d', '--name', LEGACY,
           '--network', f'{self.project}-net', '--network-alias', 'app',
           'python:3.13-alpine', 'python', '-c', APP, 'app')
        self.made_legacy = True

    def drop_legacy(self):
        if self.made_legacy:
            sh('docker', 'rm', '-f', LEGACY)
            self.made_legacy = False

    def running(self, container):
        name = container if container.startswith('pheeeew') else f'{self.project}-{container}'
        return sh('docker', 'inspect', '-f', '{{.State.Running}}', name).stdout.strip() == 'true'

    def exists(self, container):
        name = container if container.startswith('pheeeew') else f'{self.project}-{container}'
        return sh('docker', 'inspect', name).returncode == 0

    def pointer_color(self):
        for line in self.pointer.read_text().splitlines():
            if 'set $upstream_app' in line:
                return line.strip().removeprefix('set $upstream_app').strip(' ;')
        return ''

    def backup_exists(self):
        return (self.upstream / 'active.conf.bak').exists()

    def deploy(self):
        script = remote_script().replace('cd /opt/pheeeew', f'cd {self.dir}')
        env = dict(os.environ)
        env['PATH'] = f'{self.shim}:{env["PATH"]}'
        env['REAL_DOCKER'] = shutil.which('docker')
        env['SHIM_DIR'] = str(self.state)
        env['SHIM_POINTER'] = str(self.pointer)
        env['APP_IMAGE'] = 'python:3.13-alpine'
        return subprocess.run(['bash', '-s'], input=script, text=True,
                              capture_output=True, env=env, cwd=self.dir)

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
        self.drop_legacy()
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


def scenario_legacy_app(fx):
    fx.point_at('set $upstream_app app;\n')
    fx.start_legacy()
    result = fx.deploy()
    expect(result.returncode == 0, f'종료 코드 {result.returncode}\n{result.stdout}{result.stderr}')
    expect(fx.pointer_color() == 'app-blue', f'전환 파일 {fx.pointer_color()}')
    expect(fx.running('app-blue'), 'app-blue 가 떠 있지 않다')
    expect(not fx.exists(LEGACY), '이전 단일 컨테이너가 남아 있다')
    expect(f'{LEGACY} 을 정리했습니다' in result.stdout, '이관 안내가 없다')
    fx.made_legacy = False


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
    expect(result.returncode == 1, f'종료 코드 {result.returncode}')
    expect(fx.pointer_color() == 'app-green', f'전환 파일 {fx.pointer_color()}')
    expect(fx.running('app-blue'), '워커 검사가 실패했는데 app-blue 를 지웠다')
    expect(fx.running('app-green'), 'app-green 이 내려갔다')
    expect('app-blue 를 삭제하지 않습니다' in result.stdout, '보존 안내가 없다')
    expect('한 번도 확인하지 못했습니다' in result.stdout, '미확인 상태를 알리지 않는다')


def scenario_broken_json_body(fx):
    fx.point_at('set $upstream_app app-blue;\n')
    fx.start_app('app-blue')
    fx.edge('app-green', body='{"available":true garbage')
    result = fx.deploy()
    expect(result.returncode == 1, f'종료 코드 {result.returncode}')
    expect(fx.pointer_color() == 'app-blue', f'전환 파일 {fx.pointer_color()}')
    expect(fx.running('app-blue'), '깨진 JSON 인데 app-blue 를 지웠다')
    expect(not fx.exists('app-green'), 'app-green 이 남아 있다')


def scenario_rollback_drains_long_request(fx):
    fx.point_at('set $upstream_app app-blue;\n')
    fx.start_app('app-blue')
    fx.edge('app-green', code='503')
    inflight = fx.slow_request()
    time.sleep(2)
    began = time.monotonic()
    result = fx.deploy()
    elapsed = time.monotonic() - began
    served_code = inflight.communicate(timeout=180)[0].strip()
    expect(result.returncode == 1, f'종료 코드 {result.returncode}')
    expect(fx.pointer_color() == 'app-blue', f'전환 파일 {fx.pointer_color()}')
    expect(served_code == '200', f'진행 중이던 요청이 {served_code} 로 끝났다')
    expect(not fx.exists('app-green'), 'drain 후에도 app-green 이 남아 있다')
    expect(elapsed >= 50, f'drain 을 기다리지 않았다 ({elapsed:.1f}초)')
    order = result.stdout.index('복구했고 정상 응답을 확인했습니다')
    expect(order < result.stdout.index('app-green 을 정리했습니다'),
           '복구 확인보다 새 서버 삭제가 먼저다')


SCENARIOS = (
    ('정상 전환', scenario_normal),
    ('새 버전 기동 실패', scenario_start_fail),
    ('nginx -t 실패', scenario_conf_test_fail),
    ('reload 명령 실패', scenario_reload_fail),
    ('전환 미반영', scenario_not_reflected),
    ('전환 후 503', scenario_switched_but_503),
    ('전환 후 잘못된 본문', scenario_switched_but_bad_body),
    ('복구 실패', scenario_restore_fail),
    ('워커 검사 실패', scenario_drain_check_fails),
    ('전환 후 깨진 JSON', scenario_broken_json_body),
    ('롤백이 긴 요청을 기다린다', scenario_rollback_drains_long_request),
    ('최초 app 이관', scenario_legacy_app),
    ('전환 파일 이상값', scenario_invalid_pointer),
    ('전환 파일 빈 값', scenario_empty_pointer),
)


def reset(fx):
    fx.compose_apps('--profile', 'blue', '--profile', 'green', 'rm', '-sf')
    fx.drop_legacy()
    fx.reset_edge()
    fx.fix_nginx_conf()
    fx.restore_app('app-green')
    fx.compose_nginx('exec', '-T', 'nginx', 'nginx', '-s', 'reload')


def main():
    only = sys.argv[1:]
    if sh('docker', 'inspect', LEGACY).returncode == 0:
        print(f'{LEGACY} 컨테이너가 이미 있습니다. 이 검사는 그 이름을 쓰므로 중단합니다.')
        return 1
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
