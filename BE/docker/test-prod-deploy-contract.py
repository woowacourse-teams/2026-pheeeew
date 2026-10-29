#!/usr/bin/env python3
"""Production releases must never warm an older admission contract."""

import os
from pathlib import Path
import subprocess
import tempfile
import unittest


class ProductionDeploymentContractTest(unittest.TestCase):
    def test_candidate_is_not_prepared_if_available_memory_is_too_low(self):
        text = Path(__file__).with_name('press-prod-deploy.sh').read_text()
        begin = text.index('check_memory() {')
        end = text.index('python3 press-generation.py prepare', begin)
        check = text[begin:end]
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            awk = root / 'awk'
            awk.write_text('#!/bin/sh\nprintf "%s\\n" "$TEST_AVAILABLE_KIB"\n')
            awk.chmod(0o755)
            env = dict(os.environ, PATH=directory + os.pathsep + os.environ['PATH'])
            for available, expected in (('786431', False), ('786432', True), ('', False), ('bad', False)):
                result = subprocess.run(['sh', '-c', 'set -eu\n' + check], cwd=root,
                                        env=dict(env, TEST_AVAILABLE_KIB=available), capture_output=True)
                self.assertEqual(result.returncode == 0, expected, available)

    def test_only_contract_two_passes_before_candidate_start(self):
        text = Path(__file__).with_name('press-prod-deploy.sh').read_text()
        begin = text.index('contract=$(docker image inspect')
        end = text.index('if ! check_memory', begin)
        check = text[begin:end]
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            docker = root / 'docker'
            docker.write_text('#!/bin/sh\nprintf "%s\\n" "$TEST_CONTRACT"\n')
            docker.chmod(0o755)
            env = dict(os.environ, PATH=directory + os.pathsep + os.environ['PATH'], APP_IMAGE='fixture')
            for contract, expected in (('1', False), ('2', True), ('', False), ('3', False)):
                (root / '.press-deploy.json').write_text('{}')
                result = subprocess.run(['sh', '-c', 'set -eu\n' + check], cwd=root,
                                        env=dict(env, TEST_CONTRACT=contract), capture_output=True)
                self.assertEqual(result.returncode == 0, expected, contract)
                self.assertEqual((root / '.press-deploy.json').exists(), expected, contract)


if __name__ == '__main__':
    unittest.main()
