#!/usr/bin/env python3
"""운영 nginx가 인증 WebSocket의 업그레이드와 프레임을 전달하는지 확인한다."""

import importlib.util
from pathlib import Path
import subprocess
import sys
import tempfile
import time
from uuid import uuid4

HERE = Path(__file__).resolve().parent
sys.dont_write_bytecode = True
spec = importlib.util.spec_from_file_location('alb_health', HERE / 'test-alb-health.py')
fixture = importlib.util.module_from_spec(spec)
spec.loader.exec_module(fixture)
CLIENT = r'''
import http.client, json, socket, ssl, sys, time
from pathlib import Path
ctx = ssl.create_default_context(cafile='/fixture/fullchain.pem')
for attempt in range(30):
    try:
        with ctx.wrap_socket(socket.create_connection(('proxy', 443), timeout=2),
                             server_hostname='api.pheeeew.com') as ready:
            ready.sendall(b'GET /ready HTTP/1.1\r\nHost: api.pheeeew.com\r\nConnection: close\r\n\r\n')
            if ready.recv(1024).startswith(b'HTTP/1.1 200 '):
                break
    except OSError:
        pass
    time.sleep(.2)
else:
    raise RuntimeError('proxy and backend did not become ready')

def health(source, target, path='/actuator/health', method='GET', headers=None):
    conn = http.client.HTTPConnection(target, 80, timeout=3, source_address=(source, 0))
    try:
        conn.request(method, path, headers={'Host': target, **(headers or {})})
        response = conn.getresponse()
        response.read()
        return response.status
    except http.client.RemoteDisconnected:
        return 444
    finally:
        conn.close()

assert health('10.0.10.3', '10.0.10.2') == 200
assert health('10.0.11.3', '10.0.11.2') == 200
assert health('10.0.10.3', '10.0.10.2', headers={'X-Forwarded-For': '198.51.100.77'}) == 200
assert health('10.0.10.3', '10.0.10.2', method='POST') == 405
assert health('10.0.10.3', '10.0.10.2', path='/actuator/health;other') == 444
assert health('10.0.10.3', '10.0.10.2', path='/actuator/prometheus') == 444
assert health('10.0.12.3', '10.0.12.2') == 403
assert health('10.0.12.3', '10.0.12.2', headers={'X-Forwarded-For': '10.0.10.3'}) == 403
Path('/fixture/status-app').write_text('503')
assert health('10.0.10.3', '10.0.10.2') == 503
assert health('10.0.11.3', '10.0.11.2') == 503
Path('/fixture/status-app').write_text('200')
def public_health(path='/actuator/health', method='GET'):
    public_ctx = ssl.create_default_context(cafile='/fixture/fullchain.pem')
    public_ctx.check_hostname = False  # 연결 이름은 Docker 별칭이고 HTTP Host는 운영 도메인이다.
    conn = http.client.HTTPSConnection('proxy', 443, context=public_ctx, timeout=5)
    conn.request(method, path, headers={'Host': 'api.pheeeew.com'})
    response = conn.getresponse()
    result = (response.status, response.read())
    conn.close()
    return result

if sys.argv[1] == 'bluegreen':
    code, body = public_health()
    assert code == 200 and json.loads(body)['target'] == 'app', (code, body)
    Path('/fixture/status-app').write_text('503')
    assert public_health()[0] == 503
    Path('/fixture/status-app').write_text('200')
    for method in ('POST', 'HEAD', 'OPTIONS'):
        assert public_health(method=method)[0] == 405, method
    for path in ('/actuator', '/actuator/prometheus', '/actuator/health/readiness',
                 '/actuator/health/', '/actuator/health;other', '/actuator/health%3bother',
                 '/actuator/%68ealth', '/actuator%2fhealth', '/actuator//health',
                 '/actuator/./health', '/api/../actuator/health',
                 '/%61ctuator/prometheus', '/actuator/health?probe=1'):
        assert public_health(path=path)[0] == 404, path
    print('PASS: public exact health propagates backend status and blocks other Actuator paths')
else:
    assert public_health()[0] == 404
print('PASS: existing ALB health path propagates backend health')

def request(source, target, forwarded, path='/identity', method='GET'):
    conn = socket.create_connection((target, 443), timeout=5, source_address=(source, 0))
    with ctx.wrap_socket(conn, server_hostname='api.pheeeew.com') as tls:
        tls.sendall((method + ' ' + path + ' HTTP/1.1\r\nHost: api.pheeeew.com\r\nConnection: close\r\n'
                     'X-Forwarded-For: ' + forwarded + '\r\nForwarded: for=127.0.0.1\r\n'
                     'CF-Connecting-IP: 127.0.0.1\r\nX-Forwarded-Host: evil.example\r\n'
                     'X-Forwarded-Port: 9999\r\nContent-Length: 0\r\n\r\n').encode())
        response = http.client.HTTPResponse(tls)
        response.begin()
        body = response.read()
        return response.status, body

def observed_ip(source, target, forwarded):
    code, body = request(source, target, forwarded)
    assert code == 200, code
    headers = json.loads(body)['headers']
    assert 'Forwarded' not in headers and 'CF-Connecting-IP' not in headers, headers
    assert headers['X-Forwarded-Host'] == 'api.pheeeew.com' and headers['X-Forwarded-Port'] == '443', headers
    return headers['X-Real-IP']

assert observed_ip('10.0.10.3', '10.0.10.2', '203.0.113.7, 198.51.100.77') == '198.51.100.77'
assert observed_ip('10.0.11.3', '10.0.11.2', '203.0.113.7, 198.51.100.78') == '198.51.100.78'
assert observed_ip('10.0.12.3', '10.0.12.2', '127.0.0.1') == '10.0.12.3'
print('PASS: ALB appended address wins over a forged earlier address; untrusted peers cannot spoof')

codes = [request('10.0.10.3', '10.0.10.2', f'203.0.113.{i}, 198.51.100.90',
                 '/api/v2/devices/challenge', 'POST')[0] for i in range(1, 15)]
assert 429 in codes and 200 in codes, codes
assert request('10.0.10.3', '10.0.10.2', '203.0.113.1, 198.51.100.91',
               '/api/v2/devices/challenge', 'POST')[0] == 200
print('PASS: rate limit follows the actual client address, not the forged prefix')

for auth in (True, False):
    sock = ctx.wrap_socket(socket.create_connection(('proxy', 443), timeout=5),
                           server_hostname='api.pheeeew.com')
    token = 'Authorization: Bearer fixture-token\r\n' if auth else ''
    sock.sendall(('GET /api/v1/press-race HTTP/1.1\r\nHost: api.pheeeew.com\r\n'
        'Connection: Upgrade\r\nUpgrade: websocket\r\nSec-WebSocket-Version: 13\r\n'
        'Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n' + token + '\r\n').encode())
    data = b''
    while b'\r\n\r\n' not in data:
        part = sock.recv(4096)
        assert part, data
        data += part
    headers, frame = data.split(b'\r\n\r\n', 1)
    if auth:
        assert headers.startswith(b'HTTP/1.1 101 '), headers
        assert b's3pPLMBiTxaQ9kYGzzhZRbK+xOo=' in headers, headers
        while len(frame) < 4:
            part = sock.recv(4-len(frame))
            assert part
            frame += part
        assert frame[:4] == b'\x81\x02ok', frame
    else:
        assert headers.startswith(b'HTTP/1.1 401 '), headers
    sock.close()
print('PASS: production nginx authenticated WebSocket and frame')
'''


def run(*args, check=True):
    return subprocess.run(args, check=check, text=True, capture_output=True)


def main():
    bluegreen = '--bluegreen' in sys.argv[1:]
    prefix = 'prod-ws-test-' + uuid4().hex[:10]
    net, second, outside, app, green, proxy, client = (prefix + suffix for suffix in
                                               ('-net', '-second', '-outside', '-app', '-green', '-proxy', '-client'))
    try:
        with tempfile.TemporaryDirectory(prefix=prefix) as tmp:
            root = Path(tmp)
            (root / 'app.py').write_text(fixture.APP)
            (root / 'status-app').write_text('200')
            (root / 'status-green').write_text('503')
            (root / 'upstream').mkdir()
            (root / 'upstream/active.conf').write_text('set $upstream_app app-blue;\n')
            (root / 'client.py').write_text(CLIENT)
            run('openssl', 'req', '-x509', '-newkey', 'rsa:2048', '-nodes', '-days', '1',
                '-subj', '/CN=api.pheeeew.com', '-addext', 'subjectAltName=DNS:api.pheeeew.com',
                '-keyout', str(root / 'privkey.pem'), '-out', str(root / 'fullchain.pem'))
            (root / 'origin.pem').write_bytes((root / 'fullchain.pem').read_bytes())
            (root / 'origin.key').write_bytes((root / 'privkey.pem').read_bytes())
            run('docker', 'network', 'create', '--internal', '--subnet', '10.0.10.0/24', net)
            run('docker', 'network', 'create', '--internal', '--subnet', '10.0.11.0/24', second)
            run('docker', 'network', 'create', '--internal', '--subnet', '10.0.12.0/24', outside)
            run('docker', 'run', '-d', '--name', app, '--network', net, '--network-alias', 'app-blue' if bluegreen else 'app', '--ip', '10.0.10.4',
                '-v', f'{root}:/fixtures:ro', 'python:3.13-alpine', 'python', '/fixtures/app.py', 'app')
            if bluegreen:
                run('docker', 'run', '-d', '--name', green, '--network', net, '--network-alias', 'app-green', '--ip', '10.0.10.5',
                    '-v', f'{root}:/fixtures:ro', 'python:3.13-alpine', 'python', '/fixtures/app.py', 'green')
            run('docker', 'create', '--name', proxy, '--network', net, '--network-alias', 'proxy', '--ip', '10.0.10.2',
                '-v', f'{HERE / ("api-prod-bluegreen.conf" if bluegreen else "api.conf")}:/etc/nginx/conf.d/default.conf:ro',
                '-v', f'{root / "upstream"}:/etc/nginx/upstream:ro',
                '-v', f'{root}:/etc/letsencrypt/live/api.pheeeew.com:ro',
                '-v', f'{root}:/etc/ssl/cloudflare:ro', 'nginx:1.27-alpine')
            run('docker', 'create', '--name', client, '--network', net, '--ip', '10.0.10.3',
                '-v', f'{root}:/fixture', 'python:3.13-alpine', 'sleep', 'infinity')
            for container, address in ((proxy, '10.0.12.2'), (client, '10.0.12.3')):
                run('docker', 'network', 'connect', '--ip', address, outside, container)
                run('docker', 'network', 'connect', '--ip', address.replace('10.0.12.', '10.0.11.'), second, container)
                run('docker', 'start', container)
            for _ in range(30):
                state = run('docker', 'exec', proxy, 'nginx', '-t', check=False)
                if state.returncode == 0:
                    break
                time.sleep(.2)
            else:
                raise RuntimeError('nginx did not start')
            result = run('docker', 'exec', client, 'python', '/fixture/client.py',
                         'bluegreen' if bluegreen else 'single', check=False)
            print(result.stdout, end='')
            if result.returncode:
                raise RuntimeError(result.stderr.strip())
            if bluegreen:
                (root / 'upstream/active.conf').write_text('set $upstream_app app-green;\n')
                run('docker', 'exec', proxy, 'nginx', '-t')
                run('docker', 'exec', proxy, 'nginx', '-s', 'reload')
                run('docker', 'exec', client, 'python', '-c',
                    "import http.client,time; "
                    "time.sleep(1); c=http.client.HTTPConnection('10.0.10.2',80,timeout=10,"
                    "source_address=('10.0.10.3',0)); c.request('GET','/actuator/health',"
                    "headers={'Host':'10.0.10.2'}); r=c.getresponse(); assert r.status == 503, r.status")
                run('docker', 'exec', client, 'python', '-c',
                    "import http.client,ssl; ctx=ssl.create_default_context(cafile='/fixture/fullchain.pem'); ctx.check_hostname=False; "
                    "c=http.client.HTTPSConnection('proxy',443,timeout=10,context=ctx); "
                    "c.request('GET','/actuator/health',headers={'Host':'api.pheeeew.com'}); "
                    "r=c.getresponse(); assert r.status == 503, r.status")
                (root / 'status-green').write_text('200')
                run('docker', 'exec', client, 'python', '-c',
                    "import http.client,json,ssl; ctx=ssl.create_default_context(cafile='/fixture/fullchain.pem'); ctx.check_hostname=False; "
                    "ctx.check_hostname=False; "
                    "c=http.client.HTTPSConnection('proxy',443,timeout=10,"
                    "context=ctx); "
                    "c.request('GET','/identity',headers={'Host':'api.pheeeew.com'}); r=c.getresponse(); "
                    "assert r.status == 200 and r.getheader('X-Upstream') == 'app-green'; "
                    "assert json.loads(r.read())['target'] == 'green'")
                run('docker', 'exec', client, 'python', '-c',
                    "import http.client,json,ssl; ctx=ssl.create_default_context(cafile='/fixture/fullchain.pem'); ctx.check_hostname=False; "
                    "c=http.client.HTTPSConnection('proxy',443,timeout=10,context=ctx); "
                    "c.request('GET','/actuator/health',headers={'Host':'api.pheeeew.com'}); "
                    "r=c.getresponse(); body=json.loads(r.read()); assert r.status == 200 and body['target'] == 'green'")
                print('PASS: API and ALB health follow the switched production upstream')
                return
            run('docker', 'stop', '--time', '1', app)
            run('docker', 'exec', client, 'python', '-c',
                "import http.client; c=http.client.HTTPConnection('10.0.10.2',80,timeout=10,"
                "source_address=('10.0.10.3',0)); c.request('GET','/actuator/health',"
                "headers={'Host':'10.0.10.2'}); r=c.getresponse(); assert r.status in (502,504), r.status")
            print('PASS: unavailable backend is not reported healthy')
    finally:
        run('docker', 'rm', '-f', client, proxy, green, app, check=False)
        run('docker', 'network', 'rm', outside, second, net, check=False)


if __name__ == '__main__':
    main()
