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
import socket, ssl, sys
ctx = ssl.create_default_context(cafile='/fixture/fullchain.pem')
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
    net, app, proxy = (prefix + suffix for suffix in ('-net', '-app', '-proxy'))
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
            run('docker', 'network', 'create', net)
            run('docker', 'run', '-d', '--name', app, '--network', net, '--network-alias', 'app',
                '-v', f'{root}:/fixtures:ro', 'python:3.13-alpine', 'python', '/fixtures/app.py', 'app')
            run('docker', 'run', '-d', '--name', proxy, '--network', net, '--network-alias', 'proxy',
                '-v', f'{HERE / "api.conf"}:/etc/nginx/conf.d/default.conf:ro',
                '-v', f'{root}:/etc/letsencrypt/live/api.pheeeew.com:ro',
                '-v', f'{root}:/etc/ssl/cloudflare:ro', 'nginx:1.27-alpine')
            for _ in range(30):
                state = run('docker', 'exec', proxy, 'nginx', '-t', check=False)
                if state.returncode == 0:
                    break
                time.sleep(.2)
            else:
                raise RuntimeError('nginx did not start')
            result = run('docker', 'run', '--rm', '--network', net, '-v', f'{root}:/fixture:ro',
                         'python:3.13-alpine', 'python', '/fixture/client.py', check=False)
            print(result.stdout, end='')
            if result.returncode:
                raise RuntimeError(result.stderr.strip())
    finally:
        run('docker', 'rm', '-f', proxy, app, check=False)
        run('docker', 'network', 'rm', net, check=False)


if __name__ == '__main__':
    main()
