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
import http.client, socket, ssl, sys, time
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
assert health('10.0.10.3', '10.0.10.2', method='POST') == 405
assert health('10.0.10.3', '10.0.10.2', path='/actuator/health;other') == 444
assert health('10.0.10.3', '10.0.10.2', path='/actuator/prometheus') == 444
assert health('10.0.12.3', '10.0.12.2') == 403
assert health('10.0.12.3', '10.0.12.2', headers={'X-Forwarded-For': '10.0.10.3'}) == 403
Path('/fixture/status-app').write_text('503')
assert health('10.0.10.3', '10.0.10.2') == 503
assert health('10.0.11.3', '10.0.11.2') == 503
Path('/fixture/status-app').write_text('200')
with ctx.wrap_socket(socket.create_connection(('proxy', 443), timeout=3),
                     server_hostname='api.pheeeew.com') as public:
    public.sendall(b'GET /actuator/health HTTP/1.1\r\nHost: api.pheeeew.com\r\nConnection: close\r\n\r\n')
    assert public.recv(1024).startswith(b'HTTP/1.1 404 ')
print('PASS: ALB-only health path propagates backend health')

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
    prefix = 'prod-ws-test-' + uuid4().hex[:10]
    net, second, outside, app, proxy, client = (prefix + suffix for suffix in
                                               ('-net', '-second', '-outside', '-app', '-proxy', '-client'))
    try:
        with tempfile.TemporaryDirectory(prefix=prefix) as tmp:
            root = Path(tmp)
            (root / 'app.py').write_text(fixture.APP)
            (root / 'status-app').write_text('200')
            (root / 'client.py').write_text(CLIENT)
            run('openssl', 'req', '-x509', '-newkey', 'rsa:2048', '-nodes', '-days', '1',
                '-subj', '/CN=api.pheeeew.com', '-addext', 'subjectAltName=DNS:api.pheeeew.com',
                '-keyout', str(root / 'privkey.pem'), '-out', str(root / 'fullchain.pem'))
            (root / 'origin.pem').write_bytes((root / 'fullchain.pem').read_bytes())
            (root / 'origin.key').write_bytes((root / 'privkey.pem').read_bytes())
            run('docker', 'network', 'create', '--internal', '--subnet', '10.0.10.0/24', net)
            run('docker', 'network', 'create', '--internal', '--subnet', '10.0.11.0/24', second)
            run('docker', 'network', 'create', '--internal', '--subnet', '10.0.12.0/24', outside)
            run('docker', 'run', '-d', '--name', app, '--network', net, '--network-alias', 'app', '--ip', '10.0.10.4',
                '-v', f'{root}:/fixtures:ro', 'python:3.13-alpine', 'python', '/fixtures/app.py', 'app')
            run('docker', 'create', '--name', proxy, '--network', net, '--network-alias', 'proxy', '--ip', '10.0.10.2',
                '-v', f'{HERE / "api.conf"}:/etc/nginx/conf.d/default.conf:ro',
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
            result = run('docker', 'exec', client, 'python', '/fixture/client.py', check=False)
            print(result.stdout, end='')
            if result.returncode:
                raise RuntimeError(result.stderr.strip())
            run('docker', 'stop', '--time', '1', app)
            run('docker', 'exec', client, 'python', '-c',
                "import http.client; c=http.client.HTTPConnection('10.0.10.2',80,timeout=10,"
                "source_address=('10.0.10.3',0)); c.request('GET','/actuator/health',"
                "headers={'Host':'10.0.10.2'}); r=c.getresponse(); assert r.status in (502,504), r.status")
            print('PASS: unavailable backend is not reported healthy')
    finally:
        run('docker', 'rm', '-f', client, proxy, app, check=False)
        run('docker', 'network', 'rm', outside, second, net, check=False)


if __name__ == '__main__':
    main()
