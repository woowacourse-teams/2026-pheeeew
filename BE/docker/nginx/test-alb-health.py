#!/usr/bin/env python3
"""로컬 Docker에서 개발 Nginx의 ALB API·상태 검사 접근 경계를 검증한다."""

from pathlib import Path
import shutil
import subprocess
import tempfile
import uuid


def run(*args, **kwargs):
    return subprocess.run(args, check=True, text=True, capture_output=True, **kwargs)


APP = """
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path
import json

class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        code = int(Path('/fixtures/status').read_text())
        self.send_response(code)
        self.end_headers()
        self.wfile.write(json.dumps({'path': self.path, 'status': code,
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
          headers=None, expected=200, secure=True):
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
    except http.client.RemoteDisconnected:
        code, body, location = 444, b'', None
    finally:
        connection.close()
    allowed = (expected,) if isinstance(expected, int) else expected
    assert code in allowed, (source, method, host, path, code, expected, body)
    if expected in (200, 503):
        upstream_path = '/actuator/health' if path.split('?')[0] == '/actuator/health' else path
        assert json.loads(body)['path'] == upstream_path, body
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


def main():
    prefix = 'pheeeew-alb-health-' + uuid.uuid4().hex[:8]
    networks = [prefix + suffix for suffix in ('-a', '-b', '-outside')]
    app, nginx, client = (prefix + suffix for suffix in ('-app', '-nginx', '-client'))
    config = Path(__file__).with_name('api-dev.conf').resolve()
    with tempfile.TemporaryDirectory(prefix=prefix) as directory:
        fixtures = Path(directory)
        (fixtures / 'status').write_text('200')
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
            run('docker', 'run', '-d', '--name', app, '--network', networks[0],
                '--network-alias', 'app', '--ip', '10.0.10.4',
                '-v', f'{fixtures}:/fixtures:ro',
                'python:3.13-alpine', 'python', '/fixtures/app.py')
            run('docker', 'create', '--name', nginx, '--network', networks[0], '--ip', '10.0.10.2',
                '-v', f'{config}:/etc/nginx/conf.d/default.conf:ro',
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
            (fixtures / 'status').write_text('503')
            failure_probes = "\nfor source in ['10.0.10.3', '10.0.11.3']:\n"
            failure_probes += "    for secure in [False, True]:\n"
            failure_probes += "        probe(source, expected=503, secure=secure)\n"
            failure_probes += "    probe(source, path='/api/v2/emotions/map', host='api-dev.pheeeew.com',\n"
            failure_probes += "          headers={'X-Forwarded-Proto': 'https', 'X-Forwarded-For': '198.51.100.7'}, expected=503, secure=False)\n"
            run('docker', 'exec', '-i', client, 'python', '-', input=PROBE_HELPERS + failure_probes)
            print('PASS: upstream 503 is preserved for HTTP API and HTTP/HTTPS health on both LB subnets')
            run('docker', 'stop', '--time', '1', app)
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
            for container in (client, nginx, app):
                subprocess.run(['docker', 'rm', '-f', container], capture_output=True)
            for network in reversed(networks):
                subprocess.run(['docker', 'network', 'rm', network], capture_output=True)


if __name__ == '__main__':
    main()
