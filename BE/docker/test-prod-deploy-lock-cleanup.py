#!/usr/bin/env python3
"""운영 배포 셸의 안전한 실패와 불확실한 실패에서 잠금 처리를 검증한다."""

import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import Mock, patch

sys.dont_write_bytecode = True
HERE = Path(__file__).resolve().parent


def load(name):
    spec = importlib.util.spec_from_file_location(name, HERE / (name + '.py'))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


lock = load('press-prod-lock')
generation = load('press-generation')


class DeploymentFailureLockTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        original_root = lock.ROOT
        lock.ROOT = self.root
        self.addCleanup(setattr, lock, 'ROOT', original_root)
        lock.acquire('run-1')
        (self.root / 'nginx/upstream').mkdir(parents=True)
        (self.root / 'nginx/upstream/active.conf').write_text('set $upstream_app app-blue;\n')
        (self.root / 'press-prod-deploy-guard.sh').write_text('exit 0\n')
        self.bin = self.root / 'bin'
        self.bin.mkdir()
        self.executable('awk', '''#!/bin/sh
if [ "$1" = '/^MemAvailable:/ {print $2}' ]; then
  count=$(cat "$TEST_ROOT/memory-count" 2>/dev/null || echo 0)
  echo $((count+1)) > "$TEST_ROOT/memory-count"
  if [ "$count" -gt 0 ]; then printf '%s\\n' "$SECOND_AVAILABLE_KIB"; else printf '%s\\n' "$AVAILABLE_KIB"; fi
else exec /usr/bin/awk "$@"; fi
''')
        self.executable('docker', '''#!/bin/sh
case "$*" in
  'image inspect'*) printf '%s\\n' "$CONTRACT" ;;
  *' pull '*) exit "$PULL_RC" ;;
  *' up '*) exit "$UP_RC" ;;
  *'logs '*) exit 0 ;;
  *'wget '*) printf '{"available":true}' ;;
  *'nginx -t'*) exit 0 ;;
  *'nginx -s reload'*) exit 0 ;;
  *'ps -o'*)
    count=$(cat "$TEST_ROOT/ps-count" 2>/dev/null || echo 0)
    echo $((count+1)) > "$TEST_ROOT/ps-count"
    printf '1 nginx: master process\\n'
    if [ "$count" = 0 ]; then printf '2 nginx: worker process\\n'; fi
    printf 'COUNTED\\n' ;;
  *) exit 99 ;;
esac
''')
        self.executable('rm', '''#!/bin/sh
if [ "$REMOVE_RC" != 0 ]; then exit "$REMOVE_RC"; fi
exec /bin/rm "$@"
''')
        self.executable('date', '''#!/bin/sh
n=$(cat "$TEST_ROOT/clock" 2>/dev/null || echo 0)
n=$((n+100)); echo "$n" > "$TEST_ROOT/clock"; echo "$n"
''')
        for name in ['psql', 'openssl']:
            self.executable(name, '#!/bin/sh\nexit 0\n')
        self.executable('curl', '''#!/bin/sh
count=$(cat "$TEST_ROOT/curl-count" 2>/dev/null || echo 0)
echo $((count+1)) > "$TEST_ROOT/curl-count"
if [ "$count" -gt 0 ] && [ "$EXIT_ROUTE_RC" != 0 ]; then exit "$EXIT_ROUTE_RC"; fi
while [ "$#" -gt 0 ]; do
  case "$1" in
    -o) shift; printf '{"available":true}' > "$1" ;;
    -D) shift; printf 'X-Upstream: %s\\r\\n' "$(sed -n 's/set $upstream_app \\(.*\\);/\\1/p' "$TEST_ROOT/nginx/upstream/active.conf")" > "$1" ;;
  esac
  shift
done
printf 200
''')
        self.executable('python3', '''#!%s
import importlib.util, os, sys
from pathlib import Path
if sys.argv[1] == 'press-prod-lock.py':
    spec=importlib.util.spec_from_file_location('lock', os.environ['LOCK_SOURCE'])
    lock=importlib.util.module_from_spec(spec); spec.loader.exec_module(lock)
    lock.ROOT=Path(os.environ['TEST_ROOT']); sys.argv=sys.argv[1:]
    if sys.argv[1] == 'release' and os.environ['RELEASE_RC'] != '0':
        raise SystemExit(int(os.environ['RELEASE_RC']))
    raise SystemExit(lock.main())
if sys.argv[1] == 'press-generation.py':
    if sys.argv[2] == 'prepare':
        Path('.press-deploy.json').write_text('{}')
        raise SystemExit(int(os.environ['PREPARE_RC']))
    if sys.argv[2] == 'abort-prepared':
        if os.environ['ABORT_RC'] != '0': raise SystemExit(int(os.environ['ABORT_RC']))
        Path('.press-deploy.json').unlink(); raise SystemExit(0)
    result = {'handoff':'HANDOFF_RC', 'cleanup-target':'CLEANUP_RC', 'restore':'RESTORE_RC', 'verify-active':'VERIFY_RC'}
    raise SystemExit(int(os.environ.get(result.get(sys.argv[2], ''), '0')))
if sys.argv[1] == 'press-prod-preflight.py':
    count=Path('preflight-count'); n=int(count.read_text()) if count.exists() else 0; count.write_text(str(n+1))
    raise SystemExit(int(os.environ['SECOND_PREFLIGHT_RC' if n else 'PREFLIGHT_RC']))
os.execv(sys.executable, [sys.executable]+sys.argv[1:])
''' % sys.executable)

    def executable(self, name, text):
        path = self.bin / name
        path.write_text(text)
        path.chmod(0o755)

    def run_deploy(self, **overrides):
        script = (HERE / 'press-prod-deploy.sh').read_text().replace('/opt/pheeeew', str(self.root))
        script = script.replace('/tmp/probe', str(self.root / 'probe')).replace('/tmp/target', str(self.root / 'target'))
        env = dict(os.environ, PATH=str(self.bin)+os.pathsep+os.environ['PATH'],
                   PYTHONDONTWRITEBYTECODE='1',
                   APP_IMAGE='fixture', LOCK_TOKEN='run-1', TEST_ROOT=str(self.root),
                   LOCK_SOURCE=str(HERE / 'press-prod-lock.py'), AVAILABLE_KIB='786431',
                   PREPARE_RC='0', PREFLIGHT_RC='0', SECOND_PREFLIGHT_RC='0', ABORT_RC='0',
                   SECOND_AVAILABLE_KIB='786432', CONTRACT='2', PULL_RC='0', UP_RC='0',
                   HANDOFF_RC='0', CLEANUP_RC='0', RESTORE_RC='0', VERIFY_RC='0', REMOVE_RC='0', RELEASE_RC='0', EXIT_ROUTE_RC='0')
        env.update(overrides)
        signal = env.pop('SIGNAL', None)
        if signal:
            script = script.split('abort_prepared_deployment() {', 1)[0]
            script += '\nACTIVE=app-blue\nRELEASE_LOCK_ON_FAILURE=yes\nkill -'+signal+' $$\n'
        return subprocess.run(['sh', '-c', script], cwd=self.root, env=env, text=True, capture_output=True)

    def test_메모리_검사_실패는_원래_실패를_유지하며_잠금을_해제한다(self):
        result = self.run_deploy()
        self.assertEqual(result.returncode, 1, result.stderr)
        self.assertFalse((self.root / lock.LOCK).exists(), result.stdout)
        lock.acquire('next-run')

    def test_prepare_부분_실패는_잠금과_기록을_보존한다(self):
        result = self.run_deploy(AVAILABLE_KIB='786432', PREPARE_RC='7')
        self.assertEqual(result.returncode, 7)
        lock.check_owner('run-1')
        self.assertTrue((self.root / '.press-deploy.json').exists())

    def test_preflight_실패는_검증된_준비_중단_후_해제한다(self):
        result = self.run_deploy(AVAILABLE_KIB='786432', PREFLIGHT_RC='8')
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse((self.root / lock.LOCK).exists(), result.stdout)
        self.assertFalse((self.root / '.press-deploy.json').exists())

    def test_준비_중단_검증_실패는_잠금과_기록을_보존한다(self):
        result = self.run_deploy(AVAILABLE_KIB='786432', PREFLIGHT_RC='8', ABORT_RC='9')
        self.assertNotEqual(result.returncode, 0)
        lock.check_owner('run-1')
        self.assertTrue((self.root / '.press-deploy.json').exists())

    def test_기존_임시_기록이나_끊어진_심볼릭_링크가_있으면_보존한다(self):
        for name in ['.press-deploy.json', '.press-deploy.json.new', 'nginx/upstream/active.conf.bak']:
            with self.subTest(name=name):
                path = self.root / name
                path.symlink_to(self.root / 'missing')
                result = self.run_deploy()
                self.assertNotEqual(result.returncode, 0)
                lock.check_owner('run-1')
                self.assertTrue(path.is_symlink())
                path.unlink()

    def test_다른_토큰은_기존_잠금을_해제하지_못한다(self):
        result = self.run_deploy(LOCK_TOKEN='other-run')
        self.assertNotEqual(result.returncode, 0)
        lock.check_owner('run-1')

    def test_두번째_메모리_검사_실패도_해제한다(self):
        result = self.run_deploy(AVAILABLE_KIB='786432', SECOND_AVAILABLE_KIB='1')
        self.assertEqual(result.returncode, 1)
        self.assertFalse((self.root / lock.LOCK).exists(), result.stdout+result.stderr)

    def test_두번째_preflight_실패도_원래_코드로_종료하고_해제한다(self):
        result = self.run_deploy(AVAILABLE_KIB='786432', SECOND_PREFLIGHT_RC='8')
        self.assertEqual(result.returncode, 8)
        self.assertFalse((self.root / lock.LOCK).exists(), result.stdout+result.stderr)

    def test_이미지_pull_실패도_원래_코드로_종료하고_해제한다(self):
        result = self.run_deploy(AVAILABLE_KIB='786432', PULL_RC='6')
        self.assertEqual(result.returncode, 6)
        self.assertFalse((self.root / lock.LOCK).exists(), result.stdout+result.stderr)

    def test_후보_기동_실패_후_정리하면_해제한다(self):
        result = self.run_deploy(AVAILABLE_KIB='786432', UP_RC='1')
        self.assertEqual(result.returncode, 1)
        self.assertFalse((self.root / lock.LOCK).exists(), result.stdout+result.stderr)

    def test_후보_정리에_실패하면_잠금을_보존한다(self):
        result = self.run_deploy(AVAILABLE_KIB='786432', UP_RC='1', CLEANUP_RC='1')
        self.assertEqual(result.returncode, 1)
        lock.check_owner('run-1')

    def test_세대_전환_실패_후_복구와_정리를_마치면_해제한다(self):
        result = self.run_deploy(AVAILABLE_KIB='786432', HANDOFF_RC='1')
        self.assertEqual(result.returncode, 1)
        self.assertFalse((self.root / lock.LOCK).exists(), result.stdout+result.stderr)

    def test_복구_또는_기록_삭제_실패는_잠금을_보존한다(self):
        for failure in ['RESTORE_RC', 'VERIFY_RC', 'REMOVE_RC']:
            with self.subTest(failure=failure):
                result = self.run_deploy(AVAILABLE_KIB='786432', HANDOFF_RC='1', **{failure:'1'})
                self.assertEqual(result.returncode, 1)
                lock.check_owner('run-1')
                (self.root / '.press-deploy.json').unlink(missing_ok=True)
                (self.root / 'nginx/upstream/active.conf.bak').unlink(missing_ok=True)

    def test_성공은_Alloy와_finalize를_위해_잠금을_유지한다(self):
        result = self.run_deploy(AVAILABLE_KIB='786432')
        self.assertEqual(result.returncode, 0, result.stdout+result.stderr)
        lock.check_owner('run-1')

    def test_해제_오류는_원래_실패를_가리지_않는다(self):
        result = self.run_deploy(RELEASE_RC='9')
        self.assertEqual(result.returncode, 1)
        lock.check_owner('run-1')

    def test_종료_직전_실제_경로를_확인하지_못하면_잠금을_보존한다(self):
        result = self.run_deploy(EXIT_ROUTE_RC='7')
        self.assertEqual(result.returncode, 1)
        lock.check_owner('run-1')

    def test_중단_신호에서는_잠금을_보존한다(self):
        for signal, status in [('HUP',129), ('INT',130), ('TERM',143)]:
            with self.subTest(signal=signal):
                result = self.run_deploy(SIGNAL=signal)
                self.assertEqual(result.returncode, status)
                lock.check_owner('run-1')


class AbortPreparedTest(unittest.TestCase):
    def test_원본과_일치하며_후보가_없을_때만_준비_기록을_삭제한다(self):
        with tempfile.TemporaryDirectory() as directory:
            state_path = Path(directory) / '.press-deploy.json'
            state = dict(phase='PREPARED', active_contract='2', active_container='pheeeew-prod-app-blue',
                         target_slot='app-green', generation='old', enabled=False, activation_date=None)
            expected = dict(generation='old', enabled=False, activation_date=None)
            operations = Mock()
            operations.check.return_value = {'active': True}
            with patch.object(generation, 'STATE_FILE', state_path), \
                    patch.object(generation, 'operations', return_value=operations), \
                    patch.object(generation, 'environment', return_value={}), \
                    patch.object(generation, 'read_state', return_value=expected), \
                    patch.object(generation.subprocess, 'run') as docker:
                for mode in ['safe', 'candidate-exists', 'docker-error', 'db-changed',
                             'identity-error', 'phase-changed', 'temporary-record', 'restart-during-route',
                             'db-changed-during-route', 'route-error']:
                    with self.subTest(mode=mode):
                        state_path.write_text(json.dumps(dict(state, phase='RETIRED' if mode=='phase-changed' else 'PREPARED')))
                        temporary = Path(str(state_path)+'.new')
                        temporary.unlink(missing_ok=True)
                        if mode=='temporary-record': temporary.write_text('{}')
                        docker.return_value = subprocess.CompletedProcess([], 0,
                            stdout='pheeeew-prod-app-green\n' if mode=='candidate-exists' else '')
                        docker.side_effect = subprocess.CalledProcessError(1,[]) if mode=='docker-error' else None
                        operations.check.side_effect = (ValueError('changed') if mode=='identity-error' else
                            [{'active': True}, ValueError('restart')] if mode=='restart-during-route' else None)
                        operations.prepare.side_effect = ValueError('route') if mode=='route-error' else None
                        with patch.object(generation, 'read_state', return_value={} if mode=='db-changed' else expected) as read:
                            if mode=='db-changed-during-route': read.side_effect=[expected, {}]
                            if mode=='safe':
                                generation.abort_prepared(state['active_container'])
                                self.assertFalse(state_path.exists())
                            else:
                                with self.assertRaises((ValueError, subprocess.SubprocessError)):
                                    generation.abort_prepared(state['active_container'])
                                self.assertTrue(state_path.exists())


if __name__ == '__main__':
    unittest.main()
