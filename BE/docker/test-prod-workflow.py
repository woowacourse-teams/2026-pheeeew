#!/usr/bin/env python3
"""Exercise the production workflow's nginx configuration handoff."""

import os
from pathlib import Path
import subprocess
import tempfile
import unittest
import yaml

REPO = Path(__file__).resolve().parents[2]


class ProductionWorkflowTest(unittest.TestCase):
    def test_nginx_config_validation_preserves_the_served_configuration_on_failure(self):
        workflow = yaml.safe_load((REPO / '.github/workflows/be-deploy.yml').read_text())
        step = next(s for s in workflow['jobs']['deploy']['steps'] if s.get('name') == '운영 blue green 설정 전송')
        remote = step['run'].split("<<'REMOTE'\n", 1)[1].rsplit('\nREMOTE', 1)[0]
        remote = remote.replace('cd /opt/pheeeew', 'cd "$TEST_ROOT"')
        paths = ('compose.yml', 'compose.nginx.yml', 'compose.monitoring.yml',
                 'alloy/config.prod-bluegreen.alloy', 'press-generation.py', 'press-operations.py',
                 'press-prod-deploy.sh', 'press-prod-deploy-guard.sh', 'press-prod-lock.py',
                 'press-prod-preflight.py')
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'nginx').mkdir()
            (root / 'alloy').mkdir()
            shim = root / 'docker'
            shim.write_text('''#!/bin/sh
case "$*" in
  inspect*) printf '%s\\n' "$CURRENT_HASH" ;;
  *"config --hash nginx"*) printf 'nginx %s\\n' "$DESIRED_HASH" ;;
  *"nginx -t"*) exit "$NGINX_TEST_RC" ;;
  *) exit 99 ;;
esac
''')
            shim.chmod(0o755)
            config = root / 'nginx/api-bluegreen.conf'
            def run(current, desired, nginx_test):
                config.write_text('old config')
                (root / 'nginx/api-bluegreen.conf.new').write_text('new config')
                for path in paths:
                    (root / (path + '.new')).write_text('new ' + path)
                env = dict(os.environ, PATH=str(root) + os.pathsep + os.environ['PATH'], TEST_ROOT=str(root),
                           CURRENT_HASH=current, DESIRED_HASH=desired, NGINX_TEST_RC=str(nginx_test))
                return subprocess.run(['sh', '-c', remote], env=env, capture_output=True, text=True)
            self.assertNotEqual(run('same', 'different', 0).returncode, 0)
            self.assertEqual(config.read_text(), 'old config')
            self.assertNotEqual(run('same', 'same', 1).returncode, 0)
            self.assertEqual(config.read_text(), 'old config')
            self.assertEqual(run('same', 'same', 0).returncode, 0)
            self.assertEqual(config.read_text(), 'new config')


if __name__ == '__main__':
    unittest.main()
