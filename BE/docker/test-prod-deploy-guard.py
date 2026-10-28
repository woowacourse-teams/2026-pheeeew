#!/usr/bin/env python3
"""운영 최초 이행 기록이 있는 동안 일반 배포를 거절한다."""

from pathlib import Path
import json
import os
import subprocess
import tempfile
import unittest
import yaml

SCRIPT = Path(__file__).with_name('press-prod-deploy-guard.sh').resolve()
REPO = SCRIPT.parents[2]


class DeployGuardTest(unittest.TestCase):
    def test_only_finalized_journal_allows_deployment(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            def guard():
                return subprocess.run(['sh', str(SCRIPT), str(root)], capture_output=True, text=True)
            self.assertEqual(guard().returncode, 42)
            for name in ('.press-prod-bootstrap.json', '.press-prod-bootstrap.json.new'):
                path = root / name
                path.write_text('{}')
                self.assertEqual(guard().returncode, 42, name)
                path.unlink()
                path.symlink_to(root / 'missing')
                self.assertEqual(guard().returncode, 42, name)
                path.unlink()
            journal = root / '.press-prod-bootstrap.json'
            state = {'phase': 'FINALIZED', 'target_generation': '11111111-1111-4111-8111-111111111111',
                     'release_revision': 'a' * 40, 'release_manifest_sha256': 'b' * 64,
                     'expected_proxy_config_sha256': 'c' * 64,
                     'press_probe': {'device': '22222222-2222-4222-8222-222222222222',
                                     'date': '2026-10-29', 'state': 'FRUSTRATED', 'device_id': 1, 'before': 0}}
            journal.write_text(json.dumps(state))
            self.assertEqual(guard().returncode, 0)
            (root / '.press-prod-bootstrap.json.new').write_text('{}')
            self.assertEqual(guard().returncode, 42)
            (root / '.press-prod-bootstrap.json.new').unlink()
            state['phase'] = 'PUBLIC_CONFIRMED'
            journal.write_text(json.dumps(state))
            self.assertEqual(guard().returncode, 42)

    def test_journal_rejection_releases_only_on_confirmed_guard_result(self):
        workflow = yaml.safe_load((REPO / '.github/workflows/be-deploy.yml').read_text())
        step = next(item for item in workflow['jobs']['deploy']['steps']
                    if item.get('name') == '운영 최초 이행 기록 확인')
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            ssh = root / 'ssh'
            ssh.write_text('''#!/bin/sh
case "$*" in
  *"bash -s"*) exit "$FIXTURE_GUARD_RC" ;;
  *"python3 - release"*) printf 'release\\n' >> "$FIXTURE_LOG"; exit "$FIXTURE_RELEASE_RC" ;;
  *) exit 99 ;;
esac
''')
            ssh.chmod(0o755)
            log = root / 'log'
            def attempt(guard, release=0):
                log.unlink(missing_ok=True)
                env = dict(os.environ, PATH=str(root) + os.pathsep + os.environ['PATH'], LOCK_TOKEN='run-1',
                           FIXTURE_GUARD_RC=str(guard), FIXTURE_RELEASE_RC=str(release), FIXTURE_LOG=str(log))
                result = subprocess.run(['bash', '-c', step['run']], cwd=REPO, env=env,
                                        text=True, capture_output=True)
                return result.returncode, log.read_text() if log.exists() else ''
            self.assertEqual(attempt(42), (1, 'release\n'))
            self.assertEqual(attempt(42, release=255), (255, 'release\n'))
            self.assertEqual(attempt(255), (255, ''))
            self.assertEqual(attempt(1), (1, ''))
            self.assertEqual(attempt(0), (0, ''))

    def test_expected_contract_rejection_releases_lock_only_after_confirmed_inspection(self):
        workflow = yaml.safe_load((REPO / '.github/workflows/be-deploy.yml').read_text())
        step = next(item for item in workflow['jobs']['deploy']['steps']
                    if item.get('name') == '운영 프레스 계약 확인')
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            ssh = root / 'ssh'
            ssh.write_text('''#!/bin/sh
case "$*" in
  *"bash -s"*) printf '%s\\n' "$FIXTURE_CONTRACT"; exit "$FIXTURE_INSPECTION_RC" ;;
  *"python3 - release"*) printf 'release\\n' >> "$FIXTURE_LOG"; exit "$FIXTURE_RELEASE_RC" ;;
  *) exit 99 ;;
esac
''')
            ssh.chmod(0o755)
            log = root / 'log'
            def attempt(contract, inspection=0, release=0):
                log.unlink(missing_ok=True)
                env = dict(os.environ, PATH=str(root) + os.pathsep + os.environ['PATH'], IMAGE='fixture',
                           LOCK_TOKEN='run-1', FIXTURE_CONTRACT=contract,
                           FIXTURE_INSPECTION_RC=str(inspection), FIXTURE_RELEASE_RC=str(release),
                           FIXTURE_LOG=str(log))
                result = subprocess.run(['bash', '-c', step['run']], cwd=REPO, env=env,
                                        text=True, capture_output=True)
                return result.returncode, log.read_text() if log.exists() else ''
            self.assertEqual(attempt('1'), (1, 'release\n'))
            self.assertEqual(attempt('1', inspection=255), (255, ''))
            self.assertEqual(attempt('1', release=255), (255, 'release\n'))
            self.assertEqual(attempt('unexpected'), (1, 'release\n'))
            self.assertEqual(attempt('2'), (0, ''))
if __name__ == '__main__':
    unittest.main()
