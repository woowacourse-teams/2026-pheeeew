#!/usr/bin/env python3
"""로컬 Docker에서 개발 Nginx의 ALB 상태 검사 접근 경계를 검증한다."""

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
        self.wfile.write(json.dumps({'path': self.path, 'status': code}).encode())

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
    except http.client.RemoteDisconnected:
        code, body = 444, b''
    finally:
        connection.close()
    allowed = (expected,) if isinstance(expected, int) else expected
    assert code in allowed, (source, method, host, path, code, expected, body)
    if expected in (200, 503):
        assert json.loads(body)['path'] == '/actuator/health', body
    count += 1
"""

PROBES = PROBE_HELPERS + """
for source in ['10.0.10.3', '10.0.11.3']:
    probe(source)
    probe(source, host='10.0.20.123:443')
    probe(source, path='/actuator/health?probe=alb')
    for method in ['HEAD', 'POST', 'OPTIONS']:
        probe(source, method=method, expected=405)
    for path in ['/', '/api/v2/emotions/map', '/actuator/prometheus',
                 '/actuator/health/readiness', '/actuator/health/',
                 '/actuator/health;other', '/actuator/health%3bother']:
        probe(source, path=path, expected=444)
    probe(source, secure=False, expected=444)
    probe(source, host='api-dev.pheeeew.com', expected=404)
    probe(source, host='api-dev.pheeeew.com', path='/actuator/prometheus', expected=404)
    probe(source, host='docs-dev.pheeeew.com', expected=400)

probe('10.0.12.3', expected=403)
probe('10.0.12.3', headers={'X-Forwarded-For': '10.0.10.3',
      'X-Real-IP': '10.0.10.3', 'CF-Connecting-IP': '10.0.11.3',
      'User-Agent': 'ELB-HealthChecker/2.0'}, expected=403)
print(f'PASS: {count} route, method, subnet, header-spoofing and mTLS checks')
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
            run('docker', 'exec', '-i', client, 'python', '-', input=PROBE_HELPERS
                + "\nprobe('10.0.10.3', expected=503)\nprobe('10.0.11.3', expected=503)\n")
            print('PASS: upstream 503 is preserved for both LB subnets')
            run('docker', 'stop', '--time', '1', app)
            run('docker', 'exec', '-i', client, 'python', '-',
                input=PROBE_HELPERS + "\nprobe('10.0.10.3', expected=(502, 504))\n")
            print('PASS: unavailable upstream returns 502/504 instead of a false healthy response')
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
