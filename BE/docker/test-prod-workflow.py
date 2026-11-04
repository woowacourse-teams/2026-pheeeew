#!/usr/bin/env python3
"""Production stop/start order, failure boundaries and manual opt-in."""

import copy
import importlib.util
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import types
import unittest
from unittest import mock
import yaml

sys.dont_write_bytecode = True
REPO = Path(__file__).resolve().parents[2]
WORKFLOW = yaml.safe_load((REPO / '.github/workflows/be-deploy.yml').read_text())
STEPS = WORKFLOW['jobs']['deploy']['steps']
REMOTE = next(s for s in STEPS if s.get('name') == '운영 단일 앱 교체')['run'].split("<<'PYTHON'\n", 1)[1].rsplit('\nPYTHON', 1)[0]
GENERATION = '11111111-1111-4111-8111-111111111111'
NAME = 'pheeeew-prod-app-blue'


class ProductionWorkflowTest(unittest.TestCase):
    def test_only_main_manual_opt_in_can_run_deployment(self):
        triggers = WORKFLOW.get('on', WORKFLOW.get(True))
        self.assertTrue(triggers['workflow_dispatch']['inputs']['build_only']['default'])
        self.assertEqual(WORKFLOW['jobs']['deploy']['if'],
                         "${{ github.event_name == 'workflow_dispatch' && github.ref == 'refs/heads/main' && !inputs.build_only }}")
        self.assertFalse(WORKFLOW['concurrency']['cancel-in-progress'])
        commands = '\n'.join(step.get('run', '') for step in STEPS)
        for removed in ('press-prod-deploy.sh', 'press-prod-lock.py', 'press-prod-recreate.py', 'nginx -s reload'):
            self.assertNotIn(removed, commands)

    def test_staging_preserves_secrets_as_json_data(self):
        step = next(s for s in STEPS if s.get('id') == 'stage')
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for name, body in (('ssh', 'printf "/opt/pheeeew/.recreate.fixture\\n"'), ('scp', 'exit 0')):
                shim = root / name
                shim.write_text('#!/bin/sh\n' + body + '\n')
                shim.chmod(0o755)
            values = {key: 'fixture' for key in step['env']}
            values['DB_PASSWORD'] = 'dollar$literal\nJWT_PRIVATE_KEY_BASE64=still-a-password'
            env = dict(os.environ, **values, PATH=str(root) + os.pathsep + os.environ['PATH'],
                       GITHUB_OUTPUT=str(root / 'output'))
            result = subprocess.run(['bash', '-c', step['run']], cwd=root, env=env, capture_output=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertEqual(json.loads((root / 'env.tmp').read_text())['DB_PASSWORD'], values['DB_PASSWORD'])
            self.assertEqual((root / 'env.tmp').stat().st_mode & 0o777, 0o600)

    def test_actual_compose_keeps_ws_admission_uuid_and_current_slot_configuration(self):
        with tempfile.TemporaryDirectory() as directory:
            envfile = Path(directory) / '.env'
            values = {key: 'fixture' for key in ('DB_USERNAME', 'DB_PASSWORD', 'JWT_PRIVATE_KEY_BASE64',
                      'JWT_PUBLIC_KEY_BASE64', 'S3_BUCKET', 'S3_KEY_PREFIX', 'S3_REGION')}
            values.update(APP_IMAGE='fixture:current', DB_URL='jdbc:postgresql://fixture/db',
                          PRESS_BLUE_GENERATION=GENERATION, PRESS_GREEN_GENERATION=GENERATION)
            envfile.write_text(''.join(f'{key}={value}\n' for key, value in values.items()))
            for color in ('blue', 'green'):
                result = subprocess.run(['docker', 'compose', '--env-file', str(envfile), '-f',
                    str(REPO / 'BE/docker/compose.prod.yml'), '--profile', color,
                    'config', '--format', 'json'], check=True, capture_output=True, text=True)
                model = json.loads(result.stdout)
                service = model['services']['app-' + color]
                self.assertEqual(service['container_name'], 'pheeeew-prod-app-' + color)
                self.assertEqual(service['environment']['PHEEEEW_PRESS_RACE_SERVER_GENERATION'], GENERATION)
                self.assertEqual(service['environment']['PHEEEEW_PRESS_RACE_ADMISSION_FILE'], '')
                self.assertEqual(service['environment']['PHEEEEW_PRESS_RACE_ADMISSION_REQUIRED'], 'false')
                self.assertEqual(model['networks']['default']['name'], 'pheeeew-prod_default')
                self.assertEqual(service['volumes'][0]['source'], 'app-logs-' + color)


class StopStartTest(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.root = Path(directory.name)
        (self.root / 'nginx/upstream').mkdir(parents=True)
        (self.root / 'nginx/upstream/active.conf').write_text('set $upstream_app app-blue;\n')
        self.stage = self.root / 'stage'
        self.stage.mkdir()
        shutil.copy(REPO / 'BE/docker/compose.prod.yml', self.stage)
        self.env = {'DB_URL': 'jdbc:postgresql://fixture/db', 'DB_USERNAME': 'fixture', 'DB_PASSWORD': 'password',
                    'PHEEEEW_PRESS_RACE_SERVER_GENERATION': GENERATION, 'S3_BUCKET': 'fixture',
                    'JWT_PRIVATE_KEY_BASE64': 'fixture'}
        (self.stage / 'incoming.json').write_text(json.dumps(self.env))
        self.containers = {NAME: self.container('a' * 64, 'sha256:old')}
        self.events = []
        self.failure = None
        self.core = types.ModuleType('generation')
        self.core.GENERATION_KEY = 'PHEEEEW_PRESS_RACE_SERVER_GENERATION'
        self.core.CONTRACT_LABEL = 'com.pheeeew.press-generation-contract'
        self.core.environment = lambda inspected: dict(self.env)
        self.core.identity = lambda inspected: {key: copy.deepcopy(inspected[key]) for key in ('Id', 'Image', 'State')}
        self.core.container = self.inspect

    def container(self, identifier, image):
        return {'Id': identifier, 'Image': image, 'Config': {'Labels': {'com.pheeeew.press-generation-contract': '2'}},
                'State': {'Running': True, 'Health': {'Status': 'healthy'}}}

    def inspect(self, name, require_running=True):
        value = next((v for k, v in self.containers.items() if k == name or v['Id'] == name), None)
        if value is None or require_running and not value['State']['Running']:
            raise ValueError('not running')
        return copy.deepcopy(value)

    def command(self, args, **kwargs):
        result = ''
        if args[:2] == ['docker', 'ps']:
            result = '\n'.join(k for k, v in self.containers.items() if '--all' in args or v['State']['Running'])
        elif args[:2] == ['docker', 'stop']:
            self.events.append('stop:' + args[-1])
            if self.failure == 'stop':
                raise subprocess.TimeoutExpired(args, 45)
            self.containers[NAME]['State']['Running'] = self.failure == 'still-running'
        elif args[:2] == ['docker', 'rm']:
            self.events.append('remove:' + args[-1])
            if self.failure != 'still-present':
                del self.containers[NAME]
        elif args[:2] == ['docker', 'pull']:
            self.events.append('pull')
            if self.failure == 'pull':
                raise subprocess.CalledProcessError(1, args)
        elif args[:3] == ['docker', 'image', 'inspect']:
            result = json.dumps([{'Id': 'sha256:new', 'Config': {'Labels': {self.core.CONTRACT_LABEL: '2'}}}])
        elif args[:2] == ['docker', 'compose']:
            self.assertEqual(kwargs['env']['COMPOSE_PROFILES'], '')
            for key in ('APP_IMAGE', 'PRESS_BLUE_GENERATION', 'S3_BUCKET', 'JWT_PRIVATE_KEY_BASE64'):
                self.assertNotIn(key, kwargs['env'])
            if 'config' in args:
                self.events.append('config')
            else:
                self.assertFalse(any(v['State']['Running'] for v in self.containers.values()))
                self.events.append('start')
                if self.failure == 'up-timeout':
                    raise subprocess.TimeoutExpired(args, 240)
                self.containers[NAME] = self.container('b' * 64, 'sha256:new')
        else:
            self.fail('unexpected command: ' + repr(args))
        return types.SimpleNamespace(stdout=result)

    def attempt(self, failure=None):
        self.failure = failure
        previous = Path.cwd()
        loader = types.SimpleNamespace(exec_module=lambda module: None)
        overrides = {key: 'must-not-win' for key in ('APP_IMAGE', 'PRESS_BLUE_GENERATION', 'S3_BUCKET', 'JWT_PRIVATE_KEY_BASE64')}
        try:
            with mock.patch.object(importlib.util, 'spec_from_file_location', return_value=types.SimpleNamespace(loader=loader)), \
                 mock.patch.object(importlib.util, 'module_from_spec', return_value=self.core), \
                 mock.patch.object(subprocess, 'run', side_effect=self.command), \
                 mock.patch.dict(os.environ, overrides), mock.patch.object(sys, 'argv', ['-', str(self.stage), 'fixture:new']):
                script = REMOTE.replace("os.chdir('/opt/pheeeew')", 'os.chdir(' + repr(str(self.root)) + ')')
                try:
                    exec(compile(script, 'be-deploy.yml', 'exec'), {})
                    return 0
                except SystemExit as error:
                    return error.code
        finally:
            os.chdir(previous)

    def test_stop_start_success_keeps_slot_and_removes_interruption_record(self):
        self.assertEqual(self.attempt(), 0)
        self.assertEqual(self.events, ['config', 'pull', 'stop:' + 'a' * 64, 'remove:' + 'a' * 64, 'start'])
        self.assertFalse((self.root / '.press-deploy.json').exists())
        self.assertIn(GENERATION, (self.root / '.env').read_text())
        self.assertEqual((self.root / '.env').stat().st_mode & 0o777, 0o600)

    def test_pull_failure_leaves_original_app_running(self):
        self.assertEqual(self.attempt('pull'), 1)
        self.assertTrue(self.containers[NAME]['State']['Running'])
        self.assertNotIn('start', self.events)
        self.assertFalse((self.root / '.press-deploy.json').exists())

    def test_stop_or_remove_uncertainty_never_starts_new_app(self):
        for failure in ('stop', 'still-running', 'still-present'):
            with self.subTest(failure=failure):
                self.containers = {NAME: self.container('a' * 64, 'sha256:old')}
                (self.root / '.press-deploy.json').unlink(missing_ok=True)
                self.events.clear()
                self.assertEqual(self.attempt(failure), 1)
                self.assertNotIn('start', self.events)
                self.assertTrue((self.root / '.press-deploy.json').exists())

    def test_new_app_failure_and_uncertain_start_leave_record_without_automatic_rollback(self):
        for failure in ('up-timeout',):
            with self.subTest(failure=failure):
                self.containers = {NAME: self.container('a' * 64, 'sha256:old')}
                (self.root / '.press-deploy.json').unlink(missing_ok=True)
                self.events.clear()
                self.assertEqual(self.attempt(failure), 1)
                self.assertEqual(self.events.count('start'), 1)
                self.assertTrue((self.root / '.press-deploy.json').exists())

    def test_database_url_or_user_change_is_rejected_before_stop(self):
        for change in ({'DB_URL': self.env['DB_URL'] + '?currentSchema=other'}, {'DB_USERNAME': 'other'}):
            (self.stage / 'incoming.json').write_text(json.dumps(dict(self.env, **change)))
            self.assertEqual(self.attempt(), 1)
            self.assertEqual(self.events, [])

    def test_existing_operation_records_are_preserved(self):
        for name in ('.press-operations.lockdir', '.press-deploy.json', '.press-deploy.json.new',
                     '.press-prod-bootstrap.json.new', '.press-prod-bootstrap.json', 'nginx/upstream/active.conf.bak'):
            path = self.root / name
            path.symlink_to('missing')
            self.assertEqual(self.attempt(), 1)
            self.assertTrue(path.is_symlink())
            self.assertEqual(self.events, [])
            path.unlink()

    def test_restart_after_record_write_is_not_stopped_as_the_original_process(self):
        def restarted(_):
            if (self.root / '.press-deploy.json').exists():
                self.containers[NAME]['State']['StartedAt'] = 'new-incarnation'
        with mock.patch.object(os, 'fsync', side_effect=restarted):
            self.assertEqual(self.attempt(), 1)
        self.assertNotIn('stop:' + 'a' * 64, self.events)
        self.assertNotIn('start', self.events)
        self.assertTrue((self.root / '.press-deploy.json').exists())

    def test_other_running_slot_is_rejected(self):
        self.containers['pheeeew-prod-app-green'] = self.container('c' * 64, 'sha256:old')
        self.assertEqual(self.attempt(), 1)
        self.assertEqual(self.events, [])

    def test_os_lock_is_automatically_released_after_success_and_failure(self):
        import fcntl
        self.assertEqual(self.attempt('pull'), 1)
        with open(self.root / '.press-operations.lock', 'a') as lock:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        self.assertEqual(self.attempt(), 0)
        with open(self.root / '.press-operations.lock', 'a') as lock:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)


if __name__ == '__main__':
    unittest.main()
