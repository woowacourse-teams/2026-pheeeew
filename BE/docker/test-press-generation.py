#!/usr/bin/env python3
"""Generation probe contract tests with real subprocess IO and a controlled peer."""

import base64
import importlib.util
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.dont_write_bytecode = True
spec = importlib.util.spec_from_file_location('press_generation', Path(__file__).with_name('press-generation.py'))
generation = importlib.util.module_from_spec(spec)
spec.loader.exec_module(generation)

GENERATION = '11111111-1111-1111-1111-111111111111'
PEER = r'''
import base64, hashlib, json, os, sys, time
from pathlib import Path

config = sys.stdin.read()
mode = sys.argv[2] if len(sys.argv) > 2 else 'keep-open'
key = next(line.split(': ', 1)[1].rstrip('"') for line in config.splitlines() if 'Sec-WebSocket-Key:' in line)
accept = base64.b64encode(hashlib.sha1((key + '258EAFA5-E914-47DA-95CA-C5AB0DC85B11').encode()).digest())
if mode == 'bad-accept': accept = b'wrong'
headers = b'HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: ' + accept + b'\r\n\r\n'
response = dict(v=1, type='protocol.error', code='DEVICE_NOT_FOUND',
                serverGeneration='11111111-1111-1111-1111-111111111111')
if mode == 'draining': response['code'] = 'SERVER_DRAINING'
if mode == 'wrong-code': response['code'] = 'AUTH_EXPIRED'
if mode == 'wrong-version': response['v'] = 2
if mode == 'wrong-type': response['type'] = 'session.ready'
if mode == 'bad-uuid': response['serverGeneration'] = 'not-a-uuid'
if mode == 'missing-uuid': del response['serverGeneration']
if mode in ('extended', 'split'): response['message'] = '조회' * 50
payload = json.dumps(response, separators=(',', ':'), ensure_ascii=False).encode()
if mode == 'bad-json': payload = b'{'
if mode == 'bad-utf8': payload = b'"\xff"'
if mode == 'non-object': payload = b'[]'
frame = (bytes([0x81, len(payload)]) if len(payload) < 126 else b'\x81\x7e' + len(payload).to_bytes(2, 'big')) + payload
if mode == 'bad-status': headers = headers.replace(b'101 Switching Protocols', b'401 Unauthorized')
if mode == 'bad-upgrade': headers = headers.replace(b'Upgrade: websocket', b'Upgrade: invalid')
if mode == 'bad-connection': headers = headers.replace(b'Connection: Upgrade', b'Connection: close')
if mode == 'fragmented': frame = b'\x01' + frame[1:]
if mode == 'reserved': frame = b'\xc1' + frame[1:]
if mode == 'binary': frame = b'\x82' + frame[1:]
if mode == 'masked': frame = frame[:1] + bytes([frame[1] | 0x80]) + frame[2:]
if mode == 'length64': frame = b'\x81\x7f' + len(payload).to_bytes(8, 'big') + payload
if mode == 'declared-oversize': frame = b'\x81\x7e\xff\xff'
wire = headers + frame
if mode == 'header-eof': wire = headers[:-2]
if mode == 'frame-header-eof': wire = headers + b'\x81'
if mode == 'extended-length-eof': wire = headers + b'\x81\x7e\x01'
if mode in ('payload-eof', 'timeout-exit'): wire = wire[:-1]
if mode == 'flood': wire = b'x' * 70000
if mode == 'transport-failure': sys.exit(7)
if mode == 'slow-partial':
    for _ in range(12):
        sys.stdout.buffer.write(b'H')
        sys.stdout.buffer.flush()
        time.sleep(0.6)
else:
    chunks = [wire[i:i+1] for i in range(len(wire))] if mode == 'split' else [wire]
    for chunk in chunks:
        sys.stdout.buffer.write(chunk)
        sys.stdout.buffer.flush()
        if mode == 'split': time.sleep(0.001)
if mode in ('keep-open', 'split'):
    time.sleep(3)
    Path(sys.argv[1]).touch()
if mode == 'timeout-exit': sys.exit(28)
'''


class GenerationProbeTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.directory = tempfile.TemporaryDirectory()
        cls.addClassCleanup(cls.directory.cleanup)
        cls.root = Path(cls.directory.name)
        cls.peer = cls.root / 'peer.py'
        cls.peer.write_text(PEER)
        psql = cls.root / 'psql'
        psql.write_text('#!/usr/bin/env python3\nimport sys\nsys.stdin.read()\n'
                        'print(\'{"generation":null,"enabled":false,"activation_date":null,"probe_absent":true}\')\n')
        psql.chmod(0o755)
        key = subprocess.run(['openssl', 'genpkey', '-algorithm', 'RSA', '-pkeyopt', 'rsa_keygen_bits:2048',
                              '-outform', 'DER'], capture_output=True, check=True).stdout
        cls.env = dict(DB_URL='jdbc:postgresql://fixture/test?sslmode=disable', DB_USERNAME='fixture',
                       DB_PASSWORD='fixture', JWT_PRIVATE_KEY_BASE64=base64.b64encode(key).decode())

    def probe(self, mode):
        eof = self.root / ('natural-eof-' + mode)
        with patch.dict(os.environ, PATH=str(self.root) + os.pathsep + os.environ['PATH']):
            return generation.probe_generation(self.env, [sys.executable, str(self.peer), str(eof), mode])

    def test_검증된_첫_응답은_EOF를_기다리지_않는다(self):
        actual = self.probe('keep-open')
        self.assertEqual(actual, GENERATION)
        self.assertFalse((self.root / 'natural-eof-keep-open').exists(),
                         'A valid response must complete before the peer closes the stream')

    def test_TCP_분할_수신과_허용_응답을_검증한다(self):
        for mode in ('short', 'extended', 'draining', 'split'):
            with self.subTest(mode=mode):
                self.assertEqual(self.probe(mode), GENERATION)
        self.assertFalse((self.root / 'natural-eof-split').exists())

    def test_잘못된_인증_응답과_프레임은_거절한다(self):
        for mode in ('bad-status', 'bad-accept', 'bad-upgrade', 'bad-connection',
                     'fragmented', 'reserved', 'binary', 'masked', 'length64',
                     'bad-json', 'bad-utf8', 'non-object', 'wrong-code', 'wrong-version',
                     'wrong-type', 'bad-uuid', 'missing-uuid'):
            with self.subTest(mode=mode), self.assertRaises(ValueError):
                self.probe(mode)

    def test_불완전_응답은_정상종료와_시간초과에도_성공이_아니다(self):
        for mode in ('header-eof', 'frame-header-eof', 'extended-length-eof', 'payload-eof', 'timeout-exit'):
            with self.subTest(mode=mode), self.assertRaisesRegex(ValueError, 'Incomplete'):
                self.probe(mode)

    def test_응답_크기와_전체_수신_시간_상한을_유지한다(self):
        for mode in ('flood', 'declared-oversize'):
            with self.subTest(mode=mode), self.assertRaisesRegex(ValueError, 'exceeded response limit'):
                self.probe(mode)
        with self.assertRaisesRegex(ValueError, 'timed out'):
            self.probe('slow-partial')

    def test_비허용_전송_종료는_실패한다(self):
        with self.assertRaisesRegex(ValueError, 'Runtime generation probe failed'):
            self.probe('transport-failure')


if __name__ == '__main__':
    unittest.main()
