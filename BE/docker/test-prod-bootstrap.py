#!/usr/bin/env python3
"""운영 최초 이행 명령의 실패 닫힘과 영속 기록을 검증한다."""

import json
import base64
import hashlib
import importlib.util
import os
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path
import subprocess
import tempfile
import threading
import unittest
from unittest import mock

SCRIPT = Path(__file__).with_name('press-prod-bootstrap.py').resolve()
LEGACY_ID = 'a' * 64
OLD_IMAGE = 'sha256:' + 'b' * 64
NEW_IMAGE = 'sha256:' + 'c' * 64


class BootstrapCliTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.bin = self.root / 'bin'
        self.bin.mkdir()
        legacy = {'Id': LEGACY_ID, 'Image': OLD_IMAGE, 'RestartCount': 0,
                  'State': {'Running': True, 'Restarting': False, 'StartedAt': '2026-10-29T00:00:00Z'},
                  'HostConfig': {'RestartPolicy': {'Name': 'unless-stopped'}},
                  'Config': {'Labels': {}, 'Env': ['SPRING_PROFILES_ACTIVE=prod',
                    'DB_URL=jdbc:postgresql://fixture:5432/test?sslmode=disable',
                    'DB_USERNAME=fixture', 'DB_PASSWORD=fixture-secret']}}
        self.fixture = self.root / 'docker.json'
        self.fixture.write_text(json.dumps({'legacy': legacy, 'image': {
            'Id': NEW_IMAGE, 'Config': {'Labels': {'com.pheeeew.press-generation-contract': '2'},
                                       'Env': ['APP_RELEASE=' + 'a' * 40]}},
            'nginx': {'Id': 'e' * 64, 'Name': '/pheeeew-prod-nginx', 'Image': 'sha256:' + 'f' * 64, 'RestartCount': 0,
                      'HostConfig': {'RestartPolicy': {'Name': 'unless-stopped'}},
                      'State': {'Running': True, 'Restarting': False, 'StartedAt': '2026-10-28T00:00:00Z'}}}))
        docker = self.bin / 'docker'
        docker.write_text('''#!/usr/bin/env python3
import base64,hashlib,json,os,sys
d=json.load(open(os.environ['BOOTSTRAP_FIXTURE']))
if sys.argv[1:3] == ['image','inspect']:
    print(json.dumps([d['image']]))
elif sys.argv[1] == 'compose':
    print(json.dumps(d['rendered']))
elif sys.argv[1:3] == ['container','ls']:
    if os.environ.get('CANDIDATE_ALREADY_EXISTS'): print('pheeeew-prod-app-blue')
elif sys.argv[1] == 'inspect':
    name=sys.argv[2]
    print(json.dumps([d['candidate'] if name in ('pheeeew-prod-app-blue',d.get('candidate',{}).get('Id')) else d['nginx'] if name in ('pheeeew-prod-nginx',d['nginx']['Id']) else d['legacy']]))
elif sys.argv[1] == 'rename':
    if os.environ.get('FAIL_RENAME'): sys.exit(1)
    if sys.argv[2] != d['nginx']['Id']: sys.exit(1)
    d['nginx']['Name'] = '/' + sys.argv[3]
    with open(os.environ['BOOTSTRAP_FIXTURE'],'w') as f: json.dump(d,f)
    if os.environ.get('LOSE_RENAME_RESPONSE'): sys.exit(1)
elif sys.argv[1] == 'exec' and 'cat' in sys.argv:
    if sys.argv[2] == d.get('nginx',{}).get('Id'):
        sys.stdout.write(d['nginx_config'])
    elif sys.argv[-1].endswith('.closed') and os.environ.get('ACK_MISSING'):
        sys.exit(1)
    else:
        print(d['gate'])
elif sys.argv[1] == 'exec' and sys.argv[-2:] == ['nginx','-t']:
    sys.exit(0)
elif sys.argv[1] == 'exec' and 'curl' in sys.argv:
    config=sys.stdin.read()
    mutation=d.get('mutate_on_probe')
    if mutation == 'candidate_restart': d['candidate']['RestartCount'] += 1
    if mutation == 'gate': d['gate'] = d['gate'].replace('22222222-', '33333333-', 1)
    if mutation == 'legacy_restart': d['legacy']['State']['Running'] = True
    if mutation == 'nginx_restart' and d.get('nginx',{}).get('Id') in sys.argv:
        d['nginx']['RestartCount'] += 1
    if mutation:
        with open(os.environ['BOOTSTRAP_FIXTURE'],'w') as f: json.dump(d,f)
    key=next(s.split(': ',1)[1].rstrip('"') for s in config.splitlines() if 'Sec-WebSocket-Key:' in s)
    accept=base64.b64encode(hashlib.sha1((key+'258EAFA5-E914-47DA-95CA-C5AB0DC85B11').encode()).digest())
    code = 'DEVICE_NOT_FOUND' if d['gate'].startswith('OPEN ') else 'SERVER_DRAINING'
    payload=json.dumps({'v':1,'type':'protocol.error','code':d.get('probe_code',code),
                        'serverGeneration':d['runtime_generation']},separators=(',',':')).encode()
    sys.stdout.buffer.write(b'HTTP/1.1 101 Switching Protocols\\r\\nUpgrade: websocket\\r\\n'
        +b'Connection: Upgrade\\r\\nSec-WebSocket-Accept: '+accept+b'\\r\\n\\r\\n'
        +bytes([0x81,len(payload)])+payload)
elif sys.argv[1] == 'exec' and '-c' in sys.argv and sys.argv[-4] == 'sh':
    expected, replacement = sys.argv[-2:]
    journal = json.load(open('.press-prod-bootstrap.json'))
    command = next((c for c in journal['gate_commands'] if c['replacement'].strip() == replacement), {})
    if command.get('replacement','').strip() != replacement:
        sys.exit(1)
    if journal['phase'] == 'ABORT_ATTEMPTED' and os.environ.get('RACE_OPEN_BEFORE_CLOSE'):
        d['gate'] = next(c['replacement'].strip() for c in journal['gate_commands'] if c['purpose'] == 'bootstrap-open')
        with open(os.environ['BOOTSTRAP_FIXTURE'],'w') as f: json.dump(d,f)
    if d['gate'] != expected or os.environ.get('LOSE_GATE_BEFORE'):
        sys.exit(1)
    d['gate'] = replacement
    with open(os.environ['BOOTSTRAP_FIXTURE'],'w') as f: json.dump(d,f)
    if os.environ.get('LOSE_GATE_RESPONSE'): sys.exit(1)
else:
    sys.exit(1)
''')
        docker.chmod(0o755)
        psql = self.bin / 'psql'
        psql.write_text('#!/usr/bin/env python3\nimport json,os,sys\nsql=sys.stdin.read()\n'
            "if os.environ.get('PGHOSTADDR') or os.environ.get('PGSERVICE'): sys.exit(1)\n"
            "if 'UPDATE press_race_control' in sql:\n"
            "  open(os.environ['BOOTSTRAP_FIXTURE']+'.updates','a').write('update\\n')\n"
            "  if os.environ.get('LOSE_DB_RESPONSE'): sys.exit(1)\n"
            "result={'generation':os.environ.get('CONTROL_GENERATION') or None,"
            "'enabled':os.environ.get('CONTROL_ENABLED')=='true',"
            "'activation_date':os.environ.get('CONTROL_DATE') or None}\n"
            "if 'control_absent' in sql: result['control_absent']=os.environ.get('CONTROL_ABSENT','true')=='true'\n"
            "if 'remaining_sessions' in sql: result['remaining_sessions']=int(os.environ.get('SESSIONS','0'))\n"
            "if 'prepared_transactions' in sql: result['prepared_transactions']=int(os.environ.get('PREPARED','0'))\n"
            "if 'press_rows' in sql: result['press_rows']=int(os.environ.get('PRESS_ROWS','3'))\n"
            "if 'press_total' in sql: result['press_total']=int(os.environ.get('PRESS_TOTAL','12'))\n"
            "if 'press_fingerprint' in sql: result['press_fingerprint']=os.environ.get('PRESS_FINGERPRINT','a'*64)\n"
            "if 'probe_absent' in sql: result['probe_absent']=True\n"
            "print(json.dumps(result))\n")
        psql.chmod(0o755)
        self.env = dict(os.environ, PATH=str(self.bin) + os.pathsep + os.environ['PATH'],
                        BOOTSTRAP_FIXTURE=str(self.fixture))

    def cli(self, *args, env=None, simulate_isolation=False, fail_handoff_journal=False):
        # Exercise the CLI in a disposable directory while simulating its fixed production cwd.
        runner = ("import pathlib,runpy,sys; "
                  "real=pathlib.Path.cwd(); "
                  "pathlib.Path.cwd=classmethod(lambda cls: cls('/opt/pheeeew')); "
                  "sys.argv=sys.argv[1:]; module=runpy.run_path(sys.argv[0],run_name='fixture'); "
                  "module['operations_lock'].ROOT=real; "
                  "mode=sys.argv.pop(); "
                  "module['assign_owner'].__globals__['ensure_writer_isolation']=(lambda state:None) "
                  "if mode in ('simulate-isolation','fail-handoff-journal') else module['ensure_writer_isolation']; "
                  "module['core'].os.replace=(lambda *args: (_ for _ in ()).throw(OSError('fixture replace failure'))) "
                  "if mode=='fail-handoff-journal' else module['core'].os.replace; "
                  "sys.exit(module['main']())")
        marker = ('fail-handoff-journal' if fail_handoff_journal else
                  'simulate-isolation' if simulate_isolation else 'normal-isolation')
        return subprocess.run(['python3', '-c', runner, str(SCRIPT), *args, marker], cwd=self.root, text=True,
                              capture_output=True, env=env or self.env, timeout=10)

    def test_cli_rejects_other_working_directories(self):
        result = subprocess.run(['python3', str(SCRIPT), 'prepare', NEW_IMAGE], cwd=self.root,
                                text=True, capture_output=True, env=self.env, timeout=10)
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse((self.root / '.press-prod-bootstrap.json').exists())

    def test_prepare_records_new_identity_without_credentials_and_refuses_overwrite(self):
        result = self.cli('prepare', NEW_IMAGE)
        self.assertEqual(result.returncode, 0, result.stderr)
        journal = self.root / '.press-prod-bootstrap.json'
        state = json.loads(journal.read_text())
        self.assertEqual(state['legacy_identity']['id'], LEGACY_ID)
        self.assertEqual(state['candidate_image'], NEW_IMAGE)
        self.assertIsNotNone(state['target_generation'])
        self.assertNotIn('fixture-secret', journal.read_text())
        before = journal.read_bytes()
        self.assertNotEqual(self.cli('prepare', NEW_IMAGE).returncode, 0)
        self.assertEqual(journal.read_bytes(), before)

    def test_existing_control_refuses_initialization(self):
        env = dict(self.env, CONTROL_ABSENT='false')
        result = self.cli('prepare', NEW_IMAGE, env=env)
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse((self.root / '.press-prod-bootstrap.json').exists())
        self.assertFalse((self.root / '.press-operations.lockdir').exists())
        self.assertEqual(self.cli('prepare', NEW_IMAGE).returncode, 0)

    def test_prepare_preserves_lock_when_journal_write_was_attempted(self):
        result = self.cli('prepare', NEW_IMAGE, fail_handoff_journal=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertTrue((self.root / '.press-prod-bootstrap.json.new').exists())
        self.assertTrue((self.root / '.press-operations.lockdir').is_dir())

    def test_prepare_preserves_dangling_journal_symlink(self):
        journal = self.root / '.press-prod-bootstrap.json'
        journal.symlink_to('missing-journal')
        self.assertNotEqual(self.cli('prepare', NEW_IMAGE).returncode, 0)
        self.assertTrue(journal.is_symlink())

    def test_bootstrap_refuses_if_shared_lock_is_owned_by_another_operation(self):
        self.assertEqual(self.cli('prepare', NEW_IMAGE).returncode, 0)
        journal = self.root / '.press-prod-bootstrap.json'
        before = journal.read_bytes()
        (self.root / '.press-operations.lockdir' / 'owner').write_text('other-operation\n')
        self.assertNotEqual(self.cli('verify-drained').returncode, 0)
        self.assertEqual(journal.read_bytes(), before)

    def test_unlabelled_candidate_is_rejected(self):
        data = json.loads(self.fixture.read_text())
        data['image']['Config']['Labels'] = {}
        self.fixture.write_text(json.dumps(data))
        self.assertNotEqual(self.cli('prepare', NEW_IMAGE).returncode, 0)
        self.assertFalse((self.root / '.press-prod-bootstrap.json').exists())

    def test_prepare_ignores_inherited_connection_overrides(self):
        env = dict(self.env, PGHOSTADDR='192.0.2.1', PGSERVICE='wrong-database')
        result = self.cli('prepare', NEW_IMAGE, env=env)
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_drain_requires_stopped_app_restart_disabled_and_no_database_sessions(self):
        self.assertEqual(self.cli('prepare', NEW_IMAGE).returncode, 0)
        journal = self.root / '.press-prod-bootstrap.json'
        self.assertNotEqual(self.cli('verify-drained').returncode, 0)
        data = json.loads(self.fixture.read_text())
        data['legacy']['State']['Running'] = False
        self.fixture.write_text(json.dumps(data))
        self.assertNotEqual(self.cli('verify-drained').returncode, 0)
        data['legacy']['HostConfig']['RestartPolicy']['Name'] = 'no'
        data['legacy']['State']['Restarting'] = True
        self.fixture.write_text(json.dumps(data))
        self.assertNotEqual(self.cli('verify-drained').returncode, 0)
        data['legacy']['State']['Restarting'] = False
        data['legacy']['RestartCount'] = 1
        self.fixture.write_text(json.dumps(data))
        self.assertNotEqual(self.cli('verify-drained').returncode, 0)
        data['legacy']['RestartCount'] = 0
        self.fixture.write_text(json.dumps(data))
        self.assertNotEqual(self.cli('verify-drained').returncode, 0)  # Nginx is still running.
        data['nginx']['State']['Running'] = False
        data['nginx']['HostConfig']['RestartPolicy']['Name'] = 'no'
        self.fixture.write_text(json.dumps(data))
        self.assertNotEqual(self.cli('verify-drained', env=dict(self.env, SESSIONS='1')).returncode, 0)
        self.assertNotEqual(self.cli('verify-drained', env=dict(self.env, PREPARED='1')).returncode, 0)
        self.assertEqual(json.loads(journal.read_text())['phase'], 'PREPARED')
        result = self.cli('verify-drained')
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(json.loads(journal.read_text())['phase'], 'LEGACY_STOPPED')

    def test_candidate_must_match_recorded_image_database_generation_and_closed_gate(self):
        self.assertEqual(self.cli('prepare', NEW_IMAGE).returncode, 0)
        data = json.loads(self.fixture.read_text())
        data['legacy']['State']['Running'] = False
        data['legacy']['HostConfig']['RestartPolicy']['Name'] = 'no'
        data['nginx']['State']['Running'] = False
        data['nginx']['HostConfig']['RestartPolicy']['Name'] = 'no'
        self.fixture.write_text(json.dumps(data))
        self.assertEqual(self.cli('verify-drained').returncode, 0)
        self.assertNotEqual(self.cli('attest-quiescence', 'wrong').returncode, 0)
        journal = self.root / '.press-prod-bootstrap.json'
        generation = json.loads(journal.read_text())['target_generation']
        key = subprocess.run(['openssl', 'genpkey', '-algorithm', 'RSA', '-pkeyopt',
                              'rsa_keygen_bits:2048', '-outform', 'DER'], capture_output=True, check=True).stdout
        env = list(data['legacy']['Config']['Env']) + [
            'APP_RELEASE=' + 'a' * 40,
            'PHEEEEW_PRESS_RACE_SERVER_GENERATION=' + generation,
            'PHEEEEW_PRESS_RACE_ADMISSION_FILE=/app/press-admission',
            'PHEEEEW_PRESS_RACE_ADMISSION_REQUIRED=true',
            'JWT_PRIVATE_KEY_BASE64=' + base64.b64encode(key).decode()]
        data['candidate'] = {'Id': 'd' * 64, 'Image': NEW_IMAGE, 'RestartCount': 0,
            'State': {'Running': True, 'Restarting': False, 'StartedAt': '2026-10-29T00:01:00Z'},
            'Config': {'Labels': {'com.pheeeew.press-generation-contract':'2',
                                  'com.docker.compose.project':'pheeeew-prod',
                                  'com.docker.compose.service':'app-blue'}, 'Env':env},
            'Mounts': [], 'NetworkSettings': {'Ports': {'8080/tcp': None},
                                           'Networks': {'pheeeew-prod_default': {'Aliases': ['app-blue']}}},
            'HostConfig': {'Tmpfs':{}, 'NetworkMode': 'pheeeew-prod_default',
                           'PublishAllPorts': False, 'PortBindings': {},
                           'RestartPolicy': {'Name': 'no'}}}
        data['gate'] = 'OPEN 22222222-2222-4222-8222-222222222222 ' + generation
        data['runtime_generation'] = generation
        self.fixture.write_text(json.dumps(data))
        self.assertNotEqual(self.cli('record-candidate').returncode, 0)
        self.assertEqual(json.loads(journal.read_text())['phase'], 'LEGACY_STOPPED')
        data['gate'] = 'CLOSED 22222222-2222-4222-8222-222222222222 ' + generation
        self.fixture.write_text(json.dumps(data))
        self.assertNotEqual(self.cli('record-candidate').returncode, 0)
        self.assertEqual(json.loads(journal.read_text())['phase'], 'LEGACY_STOPPED')
        self.assertEqual(self.cli('attest-quiescence', 'CD_STOPPED_AND_NO_OTHER_WRITERS').returncode, 0)
        self.assertNotEqual(self.cli('attest-image-release', 'b' * 40).returncode, 0)
        self.assertEqual(self.cli('attest-image-release', 'a' * 40).returncode, 0)
        (self.root / 'nginx').mkdir(exist_ok=True)
        config = self.root / 'nginx/api-bluegreen.conf'
        config.write_text('reviewed config')
        config_hash = hashlib.sha256(config.read_bytes()).hexdigest()
        self.assertNotEqual(self.cli('attest-proxy-config', '0' * 64).returncode, 0)
        self.assertEqual(self.cli('attest-proxy-config', config_hash).returncode, 0)
        self.assertNotEqual(self.cli('record-candidate').returncode, 0)
        self.assertEqual(self.cli('write-blue-environment').returncode, 0)
        generation_env = self.root / 'press-generations.env'
        self.assertEqual(generation_env.read_text(), f'PRESS_BLUE_GENERATION={generation}\nPRESS_GREEN_GENERATION=\n')
        self.assertEqual(self.cli('write-blue-environment').returncode, 0)
        generation_env.write_text('PRESS_BLUE_GENERATION=wrong\n')
        self.assertNotEqual(self.cli('record-candidate').returncode, 0)
        self.assertNotEqual(self.cli('write-blue-environment').returncode, 0)
        generation_env.write_text(f'PRESS_BLUE_GENERATION={generation}\nPRESS_GREEN_GENERATION=\n')
        data['rendered'] = {'services': {'app-blue': {'image': 'fixture:release',
            'container_name': 'pheeeew-prod-app-blue', 'restart': 'no',
            'environment': dict(item.split('=', 1) for item in env)}}}
        changed = json.loads(json.dumps(data))
        changed['rendered']['services']['app-blue']['environment']['DB_URL'] = 'jdbc:postgresql://other:5432/test'
        self.fixture.write_text(json.dumps(changed))
        self.assertNotEqual(self.cli('check-blue-configuration', env=dict(self.env, APP_IMAGE='fixture:release', APP_RESTART_POLICY='no')).returncode, 0)
        self.fixture.write_text(json.dumps(data))
        self.assertNotEqual(self.cli('check-blue-configuration', env=dict(
            self.env, APP_IMAGE='fixture:release', APP_RESTART_POLICY='no', CANDIDATE_ALREADY_EXISTS='true')).returncode, 0)
        self.assertNotEqual(self.cli('check-blue-configuration', env=dict(self.env, APP_IMAGE='fixture:release')).returncode, 0)
        checked = self.cli('check-blue-configuration', env=dict(self.env, APP_IMAGE='fixture:release', APP_RESTART_POLICY='no'))
        self.assertEqual(checked.returncode, 0, checked.stderr)
        for variant in ('image', 'database', 'generation', 'runtime', 'admission', 'legacy_running',
                        'host_network', 'published', 'publish_all', 'automatic_restart', 'wrong_slot', 'wrong_network'):
            changed = json.loads(json.dumps(data))
            if variant == 'image':
                changed['candidate']['Image'] = OLD_IMAGE
            elif variant == 'database':
                changed['candidate']['Config']['Env'] = [x.replace('fixture:5432', 'other:5432') for x in env]
            elif variant == 'generation':
                changed['candidate']['Config']['Env'] = [x.replace(generation, '11111111-1111-4111-8111-111111111111') for x in env]
            elif variant == 'runtime':
                changed['runtime_generation'] = '11111111-1111-4111-8111-111111111111'
            elif variant == 'admission':
                changed['probe_code'] = 'DEVICE_NOT_FOUND'
            elif variant == 'host_network':
                changed['candidate']['HostConfig']['NetworkMode'] = 'host'
            elif variant == 'published':
                changed['candidate']['NetworkSettings']['Ports']['8080/tcp'] = [{'HostPort': '8080'}]
            elif variant == 'publish_all':
                changed['candidate']['HostConfig']['PublishAllPorts'] = True
            elif variant == 'automatic_restart':
                changed['candidate']['HostConfig']['RestartPolicy']['Name'] = 'always'
            elif variant == 'wrong_slot':
                changed['candidate']['Config']['Labels']['com.docker.compose.service'] = 'app-green'
            elif variant == 'wrong_network':
                changed['candidate']['NetworkSettings']['Networks']['pheeeew-prod_default']['Aliases'] = ['app-green']
            else:
                changed['legacy']['State']['Running'] = True
            self.fixture.write_text(json.dumps(changed))
            self.assertNotEqual(self.cli('record-candidate').returncode, 0, variant)
            self.assertEqual(json.loads(journal.read_text())['phase'], 'LEGACY_STOPPED')
        for mutation in ('candidate_restart', 'gate', 'legacy_restart'):
            changed = json.loads(json.dumps(data))
            changed['mutate_on_probe'] = mutation
            self.fixture.write_text(json.dumps(changed))
            self.assertNotEqual(self.cli('record-candidate').returncode, 0, mutation)
            self.assertEqual(json.loads(journal.read_text())['phase'], 'LEGACY_STOPPED')
        self.fixture.write_text(json.dumps(data))
        self.assertNotEqual(self.cli('record-candidate', env=dict(self.env, PRESS_ROWS='2')).returncode, 0)
        self.assertNotEqual(self.cli('record-candidate', env=dict(self.env, PRESS_TOTAL='11')).returncode, 0)
        self.assertNotEqual(self.cli('record-candidate', env=dict(self.env, PRESS_FINGERPRINT='b'*64)).returncode, 0)
        for variable, value in (('CONTROL_ENABLED', 'true'), ('CONTROL_DATE', '2026-10-29'),
                                ('CONTROL_GENERATION', '11111111-1111-4111-8111-111111111111')):
            self.assertNotEqual(self.cli('record-candidate', env=dict(self.env, **{variable: value})).returncode, 0)
            self.assertEqual(json.loads(journal.read_text())['phase'], 'LEGACY_STOPPED')
        result = self.cli('record-candidate')
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(json.loads(journal.read_text())['phase'], 'CANDIDATE_CLOSED')
        original_proxy = data['nginx']
        # An active old nginx invalidates the stop-mode isolation check.
        original_proxy['State']['Running'] = True
        self.fixture.write_text(json.dumps(data))
        self.assertNotEqual(self.cli('assign-owner').returncode, 0)
        self.assertEqual(json.loads(journal.read_text())['phase'], 'CANDIDATE_CLOSED')
        original_proxy['State']['Running'] = False
        self.fixture.write_text(json.dumps(data))
        # A failed durable intent record must prevent the database update entirely.
        self.assertNotEqual(self.cli('assign-owner', fail_handoff_journal=True).returncode, 0)
        self.assertEqual(json.loads(journal.read_text())['phase'], 'CANDIDATE_CLOSED')
        pending = self.root / '.press-prod-bootstrap.json.new'
        self.assertEqual(json.loads(pending.read_text())['phase'], 'HANDOFF_ATTEMPTED')
        self.assertFalse(Path(str(self.fixture) + '.updates').exists())
        guard = Path(__file__).with_name('press-prod-deploy-guard.sh')
        self.assertEqual(subprocess.run(['sh', str(guard), str(self.root)],
                                        capture_output=True).returncode, 42)
        pending.unlink()  # Disposable fixture recovery only; production must preserve evidence.
        result = self.cli('assign-owner', env=dict(self.env, LOSE_DB_RESPONSE='true'))
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(json.loads(journal.read_text())['phase'], 'HANDOFF_ATTEMPTED')
        self.assertNotEqual(self.cli('assign-owner').returncode, 0)
        self.assertNotEqual(self.cli('confirm-owner').returncode, 0)
        confirmed = self.cli('confirm-owner', env=dict(self.env, CONTROL_GENERATION=generation))
        self.assertEqual(confirmed.returncode, 0, confirmed.stderr)
        self.assertEqual(json.loads(journal.read_text())['phase'], 'OWNER_CONFIRMED')
        self.assertEqual(json.loads(self.fixture.read_text())['gate'], data['gate'])
        self.assertEqual(Path(str(self.fixture) + '.updates').read_text(), 'update\n')
        config.write_text('changed config')
        self.assertNotEqual(self.cli('open-candidate', env=dict(self.env, CONTROL_GENERATION=generation)).returncode, 0)
        self.assertEqual(json.loads(journal.read_text())['phase'], 'OWNER_CONFIRMED')
        config.write_text('reviewed config')
        open_env = dict(self.env, CONTROL_GENERATION=generation, LOSE_GATE_BEFORE='true')
        result = self.cli('open-candidate', env=open_env)
        self.assertNotEqual(result.returncode, 0)
        attempted = json.loads(journal.read_text())
        self.assertEqual(attempted['phase'], 'OPEN_ATTEMPTED')
        self.assertEqual(attempted['gate_commands'][0]['expected'].strip(), data['gate'])
        self.assertNotEqual(self.cli('open-candidate', env=open_env, simulate_isolation=True).returncode, 0)
        self.assertNotEqual(self.cli('confirm-open', env=open_env).returncode, 0)
        data['gate'] = attempted['gate_commands'][0]['replacement'].strip()
        self.fixture.write_text(json.dumps(data))
        confirmed = self.cli('confirm-open', env=dict(self.env, CONTROL_GENERATION=generation))
        self.assertEqual(confirmed.returncode, 0, confirmed.stderr)
        self.assertEqual(json.loads(journal.read_text())['phase'], 'OPEN_CONFIRMED')
        original = json.loads(journal.read_text())
        retired = self.cli('retire-proxy', env=dict(self.env, CONTROL_GENERATION=generation,
                                                    LOSE_RENAME_RESPONSE='true'))
        self.assertEqual(retired.returncode, 0, retired.stderr)
        self.assertEqual(json.loads(journal.read_text())['phase'], 'PROXY_RETIRED')
        self.assertEqual(json.loads(self.fixture.read_text())['nginx']['Name'], '/pheeeew-prod-nginx-before-press')
        data = json.loads(self.fixture.read_text())
        data['nginx']['Name'] = '/pheeeew-prod-nginx'
        self.fixture.write_text(json.dumps(data))
        journal.write_text(json.dumps(original))
        failed = self.cli('retire-proxy', env=dict(self.env, CONTROL_GENERATION=generation, FAIL_RENAME='true'))
        self.assertNotEqual(failed.returncode, 0)
        self.assertEqual(json.loads(journal.read_text())['phase'], 'PROXY_RETIRE_ATTEMPTED')
        self.assertNotEqual(self.cli('retire-proxy', env=dict(self.env, CONTROL_GENERATION=generation)).returncode, 0)
        data = json.loads(self.fixture.read_text())
        data['nginx']['Name'] = '/pheeeew-prod-nginx-before-press'
        self.fixture.write_text(json.dumps(data))
        self.assertEqual(self.cli('confirm-proxy-retired', env=dict(self.env, CONTROL_GENERATION=generation)).returncode, 0)
        data['nginx']['Name'] = '/pheeeew-prod-nginx'
        self.fixture.write_text(json.dumps(data))
        journal.write_text(json.dumps(original))
        # Separately simulate an OPEN CAS that commits before its response is lost.
        fresh = json.loads(journal.read_text())
        fresh['phase'] = 'OWNER_CONFIRMED'
        fresh.pop('gate_commands')
        journal.write_text(json.dumps(fresh))
        data['gate'] = fresh['candidate_gate'].strip()
        self.fixture.write_text(json.dumps(data))
        result = self.cli('open-candidate', env=dict(self.env, CONTROL_GENERATION=generation,
                                                    LOSE_GATE_RESPONSE='true'))
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(json.loads(result.stdout)['phase'], 'OPEN_CONFIRMED')
        self.assertEqual(json.loads(journal.read_text())['phase'], 'OPEN_CONFIRMED')
        # A new CLOSED revision makes a delayed OPEN based on the old revision stale.
        stale = json.loads(journal.read_text())
        stale['phase'] = 'OPEN_ATTEMPTED'
        journal.write_text(json.dumps(stale))
        data = json.loads(self.fixture.read_text())
        data['gate'] = stale['candidate_gate'].strip()
        self.fixture.write_text(json.dumps(data))
        raced = self.cli('abort-open', env=dict(self.env, CONTROL_GENERATION=generation,
                                                RACE_OPEN_BEFORE_CLOSE='true'))
        self.assertNotEqual(raced.returncode, 0)
        self.assertEqual(json.loads(journal.read_text())['phase'], 'ABORT_ATTEMPTED')
        self.assertTrue(json.loads(self.fixture.read_text())['gate'].startswith('OPEN '))
        # Even if the legacy app unexpectedly starts, close the candidate first.
        data = json.loads(self.fixture.read_text())
        data['legacy']['State']['Running'] = True
        self.fixture.write_text(json.dumps(data))
        self.assertNotEqual(self.cli('abort-open', env=dict(self.env, CONTROL_GENERATION=generation)).returncode, 0)
        data = json.loads(self.fixture.read_text())
        self.assertTrue(data['gate'].startswith('CLOSED '))
        self.assertEqual(json.loads(journal.read_text())['phase'], 'ABORT_ATTEMPTED')
        data['legacy']['State']['Running'] = False
        self.fixture.write_text(json.dumps(data))
        aborted = self.cli('abort-open', env=dict(self.env, CONTROL_GENERATION=generation))
        self.assertEqual(aborted.returncode, 0, aborted.stderr)
        after = json.loads(self.fixture.read_text())['gate']
        self.assertTrue(after.startswith('CLOSED '))
        self.assertNotEqual(after, stale['candidate_gate'].strip())
        self.assertEqual(json.loads(journal.read_text())['phase'], 'ABORT_CLOSED')
        pending_abort = json.loads(journal.read_text())
        pending_abort['phase'] = 'ABORT_ATTEMPTED'
        journal.write_text(json.dumps(pending_abort))
        count = len(pending_abort['gate_commands'])
        delayed = self.cli('abort-open', env=dict(self.env, CONTROL_GENERATION=generation, ACK_MISSING='true'))
        self.assertNotEqual(delayed.returncode, 0)
        self.assertEqual(len(json.loads(journal.read_text())['gate_commands']), count)
        data['gate'] = 'CLOSED 44444444-4444-4444-8444-444444444444 ' + generation
        self.fixture.write_text(json.dumps(data))
        self.assertNotEqual(self.cli('abort-open', env=dict(self.env, CONTROL_GENERATION=generation)).returncode, 0)
        self.assertEqual(len(json.loads(journal.read_text())['gate_commands']), count)
        data['gate'] = after
        self.fixture.write_text(json.dumps(data))
        recovered = self.cli('abort-open', env=dict(self.env, CONTROL_GENERATION=generation))
        self.assertEqual(recovered.returncode, 0, recovered.stderr)
        self.assertEqual(json.loads(journal.read_text())['phase'], 'ABORT_CLOSED')
        self.assertEqual(len(json.loads(journal.read_text())['gate_commands']), count)


class BootstrapOpenRaceTest(unittest.TestCase):
    def test_monitoring_requires_healthy_alloy_and_both_log_volumes(self):
        spec = importlib.util.spec_from_file_location('prod_bootstrap_monitoring', SCRIPT)
        bootstrap = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(bootstrap)
        alloy = {'Id': 'alloy', 'Image': 'sha256:fixture', 'RestartCount': 0,
                 'State': {'Running': True, 'StartedAt': '2026-10-29T00:00:00Z',
                           'Health': {'Status': 'healthy'}},
                 'Config': {'Image': 'grafana/alloy:v1.19.2', 'Labels': {
                     'com.docker.compose.project': 'pheeeew-monitoring',
                     'com.docker.compose.service': 'alloy'}},
                 'HostConfig': {'RestartPolicy': {'Name': 'unless-stopped'}},
                 'NetworkSettings': {'Networks': {'pheeeew-prod_default': {}}},
                 'Mounts': [{'Destination': '/etc/alloy/config.alloy', 'Type': 'bind', 'RW': False,
                             'Source': '/opt/pheeeew/alloy/config.prod-bluegreen.alloy'},
                            *[{'Destination': '/var/log/pheeeew-' + color, 'Type': 'volume', 'RW': False,
                               'Name': 'pheeeew-prod_app-logs-' + color} for color in ('blue', 'green')]]}
        with mock.patch.object(bootstrap.core, 'container', return_value=alloy), \
             mock.patch.object(bootstrap.subprocess, 'run', return_value=mock.Mock(stdout=b'alloy config')) as run:
            digest = hashlib.sha256(b'alloy config').hexdigest()
            bootstrap.verify_monitoring(digest)
            self.assertIn('/dev/tcp/app-blue/8080', run.call_args.args[0][-1])
            alloy['Mounts'].pop()
            with self.assertRaises(ValueError):
                bootstrap.verify_monitoring(digest)

    def test_single_press_uses_exact_http_body_and_no_retry(self):
        spec = importlib.util.spec_from_file_location('prod_bootstrap_http', SCRIPT)
        bootstrap = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(bootstrap)
        received = []
        class Handler(BaseHTTPRequestHandler):
            def do_POST(self):
                received.append((self.path, self.headers.get('Authorization'),
                                 self.rfile.read(int(self.headers['Content-Length']))))
                self.send_response(200)
                self.end_headers()
            def log_message(self, *args):
                pass
        server = HTTPServer(('127.0.0.1', 0), Handler)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        self.addCleanup(server.server_close)
        self.addCleanup(server.shutdown)
        generation = '11111111-1111-4111-8111-111111111111'
        state = {'phase': 'PRESS_PROBE_PREPARED', 'target_generation': generation,
                 'press_probe': {'device': '22222222-2222-4222-8222-222222222222',
                                 'device_id': 5, 'date': '2026-10-29', 'before': 4}}
        before = {'generation': generation, 'enabled': False, 'activation_date': None,
                  'probe_date': '2026-10-29', 'current_date': '2026-10-29', 'probe_device_id': 5,
                  'press_count': 4, 'raw_total': 8, 'projected_total': 8}
        after = dict(before, press_count=5, raw_total=9, projected_total=9)
        real_run = subprocess.run
        def local_curl(command, **kwargs):
            kwargs['input'] = kwargs['input'].replace(b'https://api.pheeeew.com',
                                                     f'http://127.0.0.1:{server.server_port}'.encode())
            return real_run(command, **kwargs)
        with mock.patch.object(bootstrap, 'verify_probe_route', return_value={}), \
             mock.patch.object(bootstrap.core, 'environment', return_value={}), \
             mock.patch.object(bootstrap.core, 'save_state'), \
             mock.patch.object(bootstrap, 'press_probe_token', return_value='test-token'), \
             mock.patch.object(bootstrap, 'press_probe_snapshot', side_effect=[before, after]), \
             mock.patch.object(bootstrap.subprocess, 'run', side_effect=local_curl):
            bootstrap.send_press_probe(state)
        self.assertEqual(state['phase'], 'PRESS_PROBE_CONFIRMED')
        self.assertEqual(received, [('/api/v2/emotions/presses', 'Bearer test-token',
                                     b'{"counts":{"FRUSTRATED":1}}')])

    def test_release_manifest_and_finalization_recheck_all_runtime_artifacts(self):
        spec = importlib.util.spec_from_file_location('prod_bootstrap_release', SCRIPT)
        bootstrap = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(bootstrap)
        generation = '11111111-1111-4111-8111-111111111111'
        revision = 'a' * 40
        contents = {name: ('fixture ' + name).encode() for name in bootstrap.release_manifest.FILES}
        raw = json.dumps({'source_revision': revision, 'files': {
            name: hashlib.sha256(content).hexdigest() for name, content in contents.items()}}).encode()
        digest = hashlib.sha256(raw).hexdigest()
        state = {'phase': 'RESTART_POLICY_CONFIRMED', 'target_generation': generation,
                 'reviewed_revision': revision,
                 'expected_proxy_config_sha256': hashlib.sha256(contents['nginx/api-bluegreen.conf']).hexdigest(),
                 'press_probe': {'device': '22222222-2222-4222-8222-222222222222',
                                 'device_id': 5, 'date': '2026-10-29', 'before': 4}}
        candidate = {'Config': {'Env': ['APP_RELEASE=' + revision]}}
        def read(path, limit):
            return raw if path == '.press-release-manifest.json' else contents[path]
        observed = {'generation': generation, 'enabled': False, 'activation_date': None,
                    'probe_date': '2026-10-29', 'probe_device_id': 5, 'press_count': 5,
                    'raw_total': 9, 'projected_total': 9}
        with mock.patch.object(bootstrap, 'read_regular', side_effect=read), \
             mock.patch.object(bootstrap, 'verify_probe_route', return_value=candidate), \
             mock.patch.object(bootstrap, 'verify_monitoring'), \
             mock.patch.object(bootstrap, 'press_probe_snapshot', return_value=observed), \
             mock.patch.object(bootstrap.core, 'probe_generation', return_value=generation), \
             mock.patch.object(bootstrap.core, 'save_state'), \
             mock.patch.object(bootstrap.os.path, 'lexists', return_value=False):
            with self.assertRaises(ValueError):
                bootstrap.attest_release_files(state, 'b' * 64)
            bootstrap.attest_release_files(state, digest)
            self.assertEqual(state['phase'], 'RELEASE_FILES_CONFIRMED')
            contents['compose.yml'] = b'changed after attestation'
            with self.assertRaises(ValueError):
                bootstrap.finalize_bootstrap(state)
            self.assertEqual(state['phase'], 'RELEASE_FILES_CONFIRMED')
            contents['compose.yml'] = b'fixture compose.yml'
            observed['projected_total'] = 8
            with self.assertRaises(ValueError):
                bootstrap.finalize_bootstrap(state)
            observed['projected_total'] = 9
            bootstrap.finalize_bootstrap(state)
            self.assertEqual(state['phase'], 'FINALIZED')

    def test_restart_response_loss_only_confirms_the_recorded_process(self):
        spec = importlib.util.spec_from_file_location('prod_bootstrap_restart', SCRIPT)
        bootstrap = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(bootstrap)
        candidate = {'Id': 'candidate'}
        state = {'phase': 'PRESS_PROBE_CONFIRMED', 'proxy_identity': {'id': 'proxy'}}
        with mock.patch.object(bootstrap, 'verify_probe_route', return_value=candidate) as check_route, \
             mock.patch.object(bootstrap.core, 'container', return_value=candidate), \
             mock.patch.object(bootstrap.core, 'identity', return_value={'id': 'proxy'}), \
             mock.patch.object(bootstrap.core, 'save_state'), \
             mock.patch.object(bootstrap.subprocess, 'run', side_effect=subprocess.TimeoutExpired('docker', 10)):
            bootstrap.enable_restart_policy(state)
            self.assertEqual(state['phase'], 'RESTART_POLICY_CONFIRMED')
            self.assertEqual(check_route.call_count, 2)
            check_route.assert_called_with(state, restart_policy='unless-stopped')

    def test_single_press_probe_is_recorded_before_input_and_never_replayed(self):
        spec = importlib.util.spec_from_file_location('prod_bootstrap_press', SCRIPT)
        bootstrap = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(bootstrap)
        generation = '11111111-1111-4111-8111-111111111111'
        device = '22222222-2222-4222-8222-222222222222'
        state = {'phase': 'PUBLIC_CONFIRMED', 'target_generation': generation}
        before = {'generation': generation, 'enabled': False, 'activation_date': None,
                  'probe_date': '2026-10-29', 'current_date': '2026-10-29', 'probe_device_id': 5, 'press_count': 4,
                  'raw_total': 8, 'projected_total': 8}
        candidate = {'fixture': True}
        with mock.patch.object(bootstrap, 'verify_probe_route', return_value=candidate), \
             mock.patch.object(bootstrap.core, 'environment', return_value={}), \
             mock.patch.object(bootstrap.core, 'save_state') as saved, \
             mock.patch.object(bootstrap, 'press_probe_token', return_value='test-token'), \
             mock.patch.object(bootstrap.subprocess, 'run', side_effect=subprocess.TimeoutExpired('curl', 12)) as sent, \
             mock.patch.object(bootstrap, 'press_probe_snapshot', return_value=before) as snapshot:
            bootstrap.begin_press_probe(state, device)
            self.assertEqual(state['phase'], 'PRESS_PROBE_PREPARED')
            saved.assert_called_once()
            with self.assertRaises(ValueError):
                bootstrap.confirm_press_probe(state)
            with self.assertRaises(ValueError):
                bootstrap.send_press_probe(state)
            self.assertEqual(state['phase'], 'PRESS_PROBE_ATTEMPTED')
            self.assertEqual(sent.call_count, 1)
            self.assertIn(b'data = "{\\"counts\\":{\\"FRUSTRATED\\":1}}"', sent.call_args.kwargs['input'])
            with self.assertRaises(ValueError):
                bootstrap.send_press_probe(state)
            self.assertEqual(sent.call_count, 1)
            snapshot.return_value = dict(before, press_count=6)
            with self.assertRaises(ValueError):
                bootstrap.confirm_press_probe(state)
            snapshot.return_value = dict(before, press_count=4, current_date='2026-10-30')
            with self.assertRaises(ValueError):
                bootstrap.confirm_press_probe(state)
            snapshot.return_value = dict(before, press_count=5, raw_total=9, projected_total=8)
            with self.assertRaises(ValueError):
                bootstrap.confirm_press_probe(state)
            snapshot.return_value = dict(before, press_count=5, raw_total=9, projected_total=9,
                                         current_date='2026-10-30')
            bootstrap.confirm_press_probe(state)
            self.assertEqual(state['phase'], 'PRESS_PROBE_CONFIRMED')

    def test_public_proxy_requires_expected_mounts_route_and_external_generation(self):
        spec = importlib.util.spec_from_file_location('prod_bootstrap_public', SCRIPT)
        bootstrap = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(bootstrap)
        generation = '11111111-1111-4111-8111-111111111111'
        state = {'phase': 'PROXY_RETIRED', 'target_generation': generation,
                 'expected_proxy_config_sha256': hashlib.sha256(b'fixture nginx config').hexdigest()}
        proxy = {'Id': 'e' * 64, 'Image': NEW_IMAGE, 'RestartCount': 0,
                 'State': {'Running': True, 'StartedAt': '2026-10-29T00:00:00Z'},
                 'Config': {'Labels': {'com.docker.compose.project': 'pheeeew-nginx-prod',
                                       'com.docker.compose.service': 'nginx'}},
                 'NetworkSettings': {'Networks': {'pheeeew-prod_default': {}}},
                 'Mounts': [
                     {'Destination': '/etc/nginx/conf.d/default.conf', 'Type': 'bind',
                      'Source': '/opt/pheeeew/nginx/api-bluegreen.conf', 'RW': False},
                     {'Destination': '/etc/nginx/upstream', 'Type': 'bind',
                      'Source': '/opt/pheeeew/nginx/upstream', 'RW': False}]}
        candidate = {'Config': {'Env': ['FIXTURE=yes']}}
        ops = mock.Mock()
        with mock.patch.object(bootstrap, 'verify_open_blue', return_value=candidate), \
             mock.patch.object(bootstrap, 'verify_retired_proxy'), \
             mock.patch.object(bootstrap.core, 'container', return_value=proxy), \
             mock.patch.object(bootstrap.core, 'operations', return_value=ops), \
             mock.patch.object(bootstrap.core, 'probe_generation', return_value=generation) as probe, \
             mock.patch.object(bootstrap.core, 'save_state'), \
             mock.patch.object(bootstrap, 'read_regular', return_value=b'fixture nginx config'), \
             mock.patch.object(bootstrap.subprocess, 'run', return_value=mock.Mock(stdout=b'fixture nginx config')):
            bootstrap.confirm_proxy_live(state)
            self.assertEqual(state['phase'], 'PUBLIC_CONFIRMED')
            ops.route.assert_called_once_with('app-blue', 443, 'api.pheeeew.com')
            self.assertEqual(probe.call_args.args[2], 'https://api.pheeeew.com/api/v1/press-race')
            state['phase'] = 'PROXY_RETIRED'
            proxy['Mounts'][0]['RW'] = True
            with self.assertRaises(ValueError):
                bootstrap.confirm_proxy_live(state)
            self.assertEqual(state['phase'], 'PROXY_RETIRED')

    def test_candidate_replaced_after_proxy_observation_is_not_opened(self):
        spec = importlib.util.spec_from_file_location('prod_bootstrap_review', SCRIPT)
        bootstrap = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(bootstrap)
        generation = '11111111-1111-4111-8111-111111111111'
        state = {'phase': 'OWNER_CONFIRMED', 'target_generation': generation,
                 'expected_proxy_config_sha256': 'a' * 64,
                 'candidate_identity': {'id': 'original'}, 'candidate_image': 'image',
                 'candidate_gate': 'CLOSED 22222222-2222-4222-8222-222222222222 ' + generation + '\n',
                 'database': ['fixture']}
        candidate = {'Id': 'replacement', 'Image': 'image'}
        env = {bootstrap.core.GENERATION_KEY: generation}
        with (mock.patch.object(bootstrap, 'ensure_writer_isolation'),
              mock.patch.object(bootstrap, 'proxy_config_digest', return_value='a' * 64),
              mock.patch.object(bootstrap, 'checked_candidate', return_value=env),
              mock.patch.object(bootstrap, 'require_isolated_candidate'),
              mock.patch.object(bootstrap.core, 'read_state', return_value={
                  'generation': generation, 'enabled': False, 'activation_date': None}),
              mock.patch.object(bootstrap.core, 'container', return_value=candidate),
              mock.patch.object(bootstrap.core, 'environment', return_value=env),
              mock.patch.object(bootstrap.core, 'identity', return_value={'id': 'replacement'}),
              mock.patch.object(bootstrap.core, 'save_state') as save):
            with self.assertRaisesRegex(ValueError, 'changed before OPEN intent'):
                bootstrap.open_candidate(state)
            save.assert_not_called()
if __name__ == '__main__':
    unittest.main()
