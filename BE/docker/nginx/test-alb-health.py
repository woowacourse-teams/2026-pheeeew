#!/usr/bin/env python3
"""로컬 Docker에서 개발 Nginx의 ALB API·상태 검사 접근 경계를 검증한다."""

from pathlib import Path
import shutil
import subprocess
import tempfile
import time
import uuid


def run(*args, **kwargs):
    return subprocess.run(args, check=True, text=True, capture_output=True, **kwargs)


APP = """
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path
import json
import sys

target = sys.argv[1]
status = Path('/fixtures/status-' + target)

class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        code = int(status.read_text())
        self.send_response(code)
        self.end_headers()
        self.wfile.write(json.dumps({'path': self.path, 'status': code, 'target': target,
                                    'headers': dict(self.headers)}).encode())

    do_POST = do_GET

HTTPServer(('0.0.0.0', 8080), Handler).serve_forever()
"""

PROBE_HELPERS = """
import http.client
import json
import ssl

context = ssl.create_default_context(cafile='/fixtures/origin.pem')
count = 0

def probe(source, path='/actuator/health', method='GET', host=None,
          headers=None, expected=200, secure=True, upstream=None, upstream_header=True):
    global count
    target = source.rsplit('.', 1)[0] + '.2'
    cls = http.client.HTTPSConnection if secure else http.client.HTTPConnection
    options = {'context': context} if secure else {}
    connection = cls(target, 443 if secure else 80, timeout=8,
                     source_address=(source, 0), **options)
    request_headers = {'Host': host or target}
    request_headers.update(headers or {})
    try:
        connection.request(method, path, headers=request_headers)
        response = connection.getresponse()
        code, body = response.status, response.read()
        location = response.getheader('Location')
        served = response.getheader('X-Upstream')
    except http.client.RemoteDisconnected:
        code, body, location, served = 444, b'', None, None
    finally:
        connection.close()
    allowed = (expected,) if isinstance(expected, int) else expected
    assert code in allowed, (source, method, host, path, code, expected, body)
    if expected in (200, 503):
        upstream_path = '/actuator/health' if path.split('?')[0] == '/actuator/health' else path
        assert json.loads(body)['path'] == upstream_path, body
    if upstream is not None:
        # 전환이 실제로 반영됐는지는 헤더와 응답한 백엔드가 둘 다 같아야 확인된다.
        if upstream_header:
            assert served == upstream, (source, path, served, upstream)
        else:
            assert served is None, (source, path, served)
        if code in (200, 503):
            assert json.loads(body)['target'] == upstream, body
    count += 1
    return body, location
"""

PROBES = PROBE_HELPERS + """
for source in ['10.0.10.3', '10.0.11.3']:
    for secure in [False, True]:
        probe(source, secure=secure)
        probe(source, host='10.0.20.123', secure=secure)
        probe(source, path='/actuator/health?probe=alb', secure=secure)
        for method in ['HEAD', 'POST', 'OPTIONS']:
            probe(source, method=method, expected=405, secure=secure)
        for path in ['/', '/api/v2/emotions/map', '/actuator/prometheus',
                     '/actuator/health/readiness', '/actuator/health/',
                     '/actuator/health;other', '/actuator/health%3bother']:
            probe(source, path=path, expected=444, secure=secure)
        for path in ['/actuator/health', '/actuator/prometheus', '/swagger-ui/index.html',
                     '/v3/api-docs', '/api/v2/devices;other']:
            probe(source, host='api-dev.pheeeew.com', path=path,
                  headers={'X-Forwarded-Proto': 'https'}, expected=404, secure=secure)
    probe(source, host='docs-dev.pheeeew.com', expected=400)

for secure in [False, True]:
    probe('10.0.12.3', expected=403, secure=secure)
    probe('10.0.12.3', headers={'X-Forwarded-For': '10.0.10.3',
          'X-Real-IP': '10.0.10.3', 'CF-Connecting-IP': '10.0.11.3',
          'User-Agent': 'ELB-HealthChecker/2.0'}, expected=403, secure=secure)

def api(source, client='198.51.100.7', headers=None, secure=False,
        path='/api/v2/emotions/map?groupId=one&groupId=two', method='GET', expected=200):
    forwarded = {'X-Forwarded-For': client, 'X-Forwarded-Proto': 'https'}
    forwarded.update(headers or {})
    return probe(source, host='api-dev.pheeeew.com', path=path, method=method,
                 headers=forwarded, secure=secure, expected=expected)

for source in ['10.0.10.3', '10.0.11.3']:
    for client in ['198.51.100.7', '198.51.100.7:12345', '[2001:db8::7]:12345', '10.0.10.55']:
        body, _ = api(source, client='203.0.113.99, ' + client, headers={
            'X-Real-IP': '203.0.113.88', 'CF-Connecting-IP': '203.0.113.77',
            'Forwarded': 'for=203.0.113.66;proto=http', 'X-Forwarded-Port': '80',
            'X-Forwarded-Host': 'attacker.invalid'})
        forwarded = json.loads(body)['headers']
        address = '2001:db8::7' if client.startswith('[') else client.split(':')[0]
        assert forwarded['X-Forwarded-For'] == address, forwarded
        assert forwarded['X-Real-IP'] == address, forwarded
        assert forwarded['X-Forwarded-Proto'] == 'https', forwarded
        assert forwarded['X-Forwarded-Port'] == '443', forwarded
        assert forwarded['Host'] == 'api-dev.pheeeew.com', forwarded
        assert forwarded['X-Forwarded-Host'] == 'api-dev.pheeeew.com', forwarded
        assert 'CF-Connecting-IP' not in forwarded and 'Forwarded' not in forwarded, forwarded
    for proto in ['', 'http', 'https,http']:
        api(source, headers={'X-Forwarded-Proto': proto}, expected=400)
    _, location = probe(source, host='docs-dev.pheeeew.com', secure=False, expected=301)
    assert location == 'https://docs-dev.pheeeew.com/actuator/health', location

# TCP 출처는 전달 헤더로 위조할 수 없고, 직접 HTTPS는 기존 연결 주소를 유지한다.
_, location = api('10.0.12.3', client='10.0.10.3', expected=301)
assert location == 'https://api-dev.pheeeew.com/api/v2/emotions/map?groupId=one&groupId=two', location
body, _ = api('10.0.12.3', secure=True, headers={
    'X-Forwarded-Proto': 'http', 'CF-Connecting-IP': '203.0.113.77'})
forwarded = json.loads(body)['headers']
assert forwarded['X-Forwarded-For'] == '10.0.12.3', forwarded
assert forwarded['X-Real-IP'] == '10.0.12.3', forwarded
assert forwarded['X-Forwarded-Proto'] == 'https', forwarded

# XFF 앞부분을 매번 바꿔도 같은 클라이언트의 제한은 LB 노드 사이에서 유지된다.
for n in range(11):
    api('10.0.10.3', client=f'203.0.113.{n}, 198.51.100.120',
        path='/api/v2/devices', method='POST')
api('10.0.11.3', client='203.0.113.200, 198.51.100.120',
    path='/api/v2/devices', method='POST', expected=429)
for source, client in [('10.0.10.3', '198.51.100.121'), ('10.0.11.3', '198.51.100.122')]:
    api(source, client=client, path='/api/v2/devices', method='POST')
print(f'PASS: {count} HTTP/HTTPS health, API, forwarding, rate-limit and access-boundary checks')
"""


# 전환 대상이 실제로 바뀌었는지 프록시하는 경로마다 확인한다.
SWITCH_PROBES = PROBE_HELPERS + """
import sys
upstream = sys.argv[1]
for source in ['10.0.10.3', '10.0.11.3']:
    for secure in [False, True]:
        probe(source, host='api-dev.pheeeew.com',
              path='/api/v2/emotions/map?groupId=one&groupId=two',
              headers={'X-Forwarded-For': '198.51.100.7', 'X-Forwarded-Proto': 'https'},
              secure=secure, upstream=upstream)
        probe(source, secure=secure, upstream=upstream, upstream_header=False)
probe('10.0.12.3', secure=True, host='api-dev.pheeeew.com',
      path='/api/v2/emotions/map?groupId=one&groupId=two',
      headers={'X-Forwarded-For': '198.51.100.7', 'X-Forwarded-Proto': 'http'},
      upstream=upstream)
print(count)
"""

# reload 반영을 기다릴 때 쓴다. 단언하지 않고 지금 응답하는 백엔드만 돌려준다.
SERVED_UPSTREAM = PROBE_HELPERS + """
body, _ = probe('10.0.10.3', host='api-dev.pheeeew.com',
                path='/api/v2/emotions/map?groupId=one&groupId=two',
                headers={'X-Forwarded-For': '198.51.100.7', 'X-Forwarded-Proto': 'https'},
                secure=False)
print(json.loads(body)['target'])
"""

BACKENDS = (('app', '10.0.10.4'), ('app-blue', '10.0.10.5'), ('app-green', '10.0.10.6'))


def main():
    prefix = 'pheeeew-alb-health-' + uuid.uuid4().hex[:8]
    networks = [prefix + suffix for suffix in ('-a', '-b', '-outside')]
    nginx, client = (prefix + suffix for suffix in ('-nginx', '-client'))
    apps = {name: f'{prefix}-{name}' for name, _ in BACKENDS}
    config = Path(__file__).with_name('api-dev.conf').resolve()
    with tempfile.TemporaryDirectory(prefix=prefix) as directory:
        fixtures = Path(directory)
        for name, _ in BACKENDS:
            (fixtures / f'status-{name}').write_text('200')
        # 저장소가 내보내는 초기 전환 파일을 그대로 복사해 쓰기 가능한 자리에 둔다.
        upstream = fixtures / 'upstream'
        upstream.mkdir()
        active = upstream / 'active.conf'
        shutil.copy(config.parent / 'upstream' / 'active.conf', active)
        assert 'set $upstream_app app;' in active.read_text(), active.read_text()
        (fixtures / 'app.py').write_text(APP)
        (fixtures / 'probes.py').write_text(PROBES)
        (fixtures / 'nginx.conf').write_text('''
events {}
http {
    include /etc/nginx/conf.d/default.conf;
}
''')
        run('openssl', 'req', '-x509', '-newkey', 'rsa:2048', '-nodes', '-days', '1',
            '-subj', '/CN=local-alb-health-test',
            '-addext', 'subjectAltName=IP:10.0.10.2,IP:10.0.11.2,IP:10.0.12.2',
            '-keyout', str(fixtures / 'origin.key'), '-out', str(fixtures / 'origin.pem'))
        shutil.copy(fixtures / 'origin.pem', fixtures / 'origin-pull-ca.pem')
        shutil.copy(fixtures / 'origin.pem', fixtures / 'fullchain.pem')
        shutil.copy(fixtures / 'origin.key', fixtures / 'privkey.pem')
        try:
            for network, subnet in zip(networks, ('10.0.10.0/24', '10.0.11.0/24', '10.0.12.0/24')):
                run('docker', 'network', 'create', '--internal', '--subnet', subnet, network)
            for name, address in BACKENDS:
                run('docker', 'run', '-d', '--name', apps[name], '--network', networks[0],
                    '--network-alias', name, '--ip', address,
                    '-v', f'{fixtures}:/fixtures:ro',
                    'python:3.13-alpine', 'python', '/fixtures/app.py', name)
            run('docker', 'create', '--name', nginx, '--network', networks[0], '--ip', '10.0.10.2',
                '-v', f'{config}:/etc/nginx/conf.d/default.conf:ro',
                '-v', f'{upstream}:/etc/nginx/upstream:ro',
                '-v', f'{fixtures / "nginx.conf"}:/etc/nginx/nginx.conf:ro',
                '-v', f'{fixtures}:/etc/ssl/cloudflare:ro',
                '-v', f'{fixtures}:/etc/letsencrypt/live/api-dev.pheeeew.com:ro',
                'nginx:1.27-alpine')
            run('docker', 'create', '--name', client, '--network', networks[0], '--ip', '10.0.10.3',
                '-v', f'{fixtures}:/fixtures:ro', 'python:3.13-alpine', 'sleep', 'infinity')
            for container, last in ((nginx, '2'), (client, '3')):
                for network, subnet in zip(networks[1:], ('10.0.11', '10.0.12')):
                    run('docker', 'network', 'connect', '--ip', f'{subnet}.{last}', network, container)
                run('docker', 'start', container)
            result = run('docker', 'exec', nginx, 'nginx', '-t')
            print(result.stderr.strip())
            print(run('docker', 'exec', client, 'python', '/fixtures/probes.py').stdout.strip())

            def write_active(text):
                active.write_text(text)
                # 바인드 마운트 반영을 기다린다. 기다리지 않으면 이전 내용으로 검사된다.
                for _ in range(50):
                    seen = run('docker', 'exec', nginx,
                               'cat', '/etc/nginx/upstream/active.conf').stdout
                    if seen == text:
                        return
                    time.sleep(0.1)
                raise AssertionError(f'전환 파일이 컨테이너에 반영되지 않았다: {seen!r}')

            def select(name):
                write_active(f'set $upstream_app {name};\n')

            def reload_nginx(expect):
                run('docker', 'exec', nginx, 'nginx', '-t')
                # reload 는 신호만 보내고 반환한다. 새 워커가 받기 시작할 때까지 기다린다.
                run('docker', 'exec', nginx, 'nginx', '-s', 'reload')
                seen = None
                for _ in range(100):
                    seen = run('docker', 'exec', '-i', client, 'python', '-',
                               input=SERVED_UPSTREAM).stdout.strip()
                    if seen == expect:
                        return
                    time.sleep(0.1)
                raise AssertionError(f'reload 뒤 {expect} 로 바뀌지 않았다: {seen!r}')

            def routed_to(name):
                result = run('docker', 'exec', '-i', client, 'python', '-', name,
                             input=SWITCH_PROBES)
                return int(result.stdout.strip())

            routes = routed_to('app')
            print(f'PASS: shipped active.conf routes {routes} proxy paths to the app backend')

            # 파일만 바꾸고 reload 하지 않으면 살아 있는 설정이 바뀌지 않아야 한다.
            select('app-blue')
            routed_to('app')
            print('PASS: switch file change alone does not move live traffic')

            reload_nginx('app-blue')
            routed_to('app-blue')
            select('app-green')
            reload_nginx('app-green')
            routed_to('app-green')
            print(f'PASS: app -> app-blue -> app-green reaches the matching backend on {routes} paths')

            (fixtures / 'status-app-green').write_text('503')
            run('docker', 'exec', '-i', client, 'python', '-', input=PROBE_HELPERS
                + "\nfor secure in [False, True]:\n"
                + "    probe('10.0.10.3', expected=503, secure=secure,\n"
                + "          upstream='app-green', upstream_header=False)\n")
            (fixtures / 'status-app-green').write_text('200')
            print('PASS: upstream 503 keeps reporting the switched backend')

            select('app-blue')
            reload_nginx('app-blue')
            routed_to('app-blue')
            print('PASS: app-green -> app-blue reverse switch reaches the matching backend')

            # 전환 파일이 깨지면 nginx -t 가 실패해야 한다. 배포는 이 신호로 멈춘다.
            write_active('set $upstream_app;\n')
            broken = subprocess.run(['docker', 'exec', nginx, 'nginx', '-t'],
                                    text=True, capture_output=True)
            assert broken.returncode != 0, broken.stdout + broken.stderr
            routed_to('app-blue')
            print('PASS: invalid switch file fails nginx -t while live traffic keeps the old target')

            write_active('')
            empty = subprocess.run(['docker', 'exec', nginx, 'nginx', '-t'],
                                   text=True, capture_output=True)
            assert empty.returncode != 0, empty.stdout + empty.stderr
            print('PASS: empty switch file fails nginx -t instead of serving an unset upstream')

            select('app')
            reload_nginx('app')
            routed_to('app')
            (fixtures / 'status-app').write_text('503')
            failure_probes = "\nfor source in ['10.0.10.3', '10.0.11.3']:\n"
            failure_probes += "    for secure in [False, True]:\n"
            failure_probes += "        probe(source, expected=503, secure=secure)\n"
            failure_probes += "    probe(source, path='/api/v2/emotions/map', host='api-dev.pheeeew.com',\n"
            failure_probes += "          headers={'X-Forwarded-Proto': 'https', 'X-Forwarded-For': '198.51.100.7'}, expected=503, secure=False)\n"
            run('docker', 'exec', '-i', client, 'python', '-', input=PROBE_HELPERS + failure_probes)
            print('PASS: upstream 503 is preserved for HTTP API and HTTP/HTTPS health on both LB subnets')
            for container in apps.values():
                run('docker', 'stop', '--time', '1', container)
            run('docker', 'exec', '-i', client, 'python', '-',
                input=PROBE_HELPERS + "\nfor secure in [False, True]:\n"
                + "    probe('10.0.10.3', expected=(502, 504), secure=secure)\n")
            print('PASS: unavailable upstream returns 502/504 over HTTP/HTTPS instead of false healthy')
        except Exception as error:
            if isinstance(error, subprocess.CalledProcessError):
                print(error.stdout, error.stderr)
            print(subprocess.run(['docker', 'logs', nginx], text=True, capture_output=True).stderr)
            raise
        finally:
            for container in (client, nginx, *apps.values()):
                subprocess.run(['docker', 'rm', '-f', container], capture_output=True)
            for network in reversed(networks):
                subprocess.run(['docker', 'network', 'rm', network], capture_output=True)


if __name__ == '__main__':
    main()
