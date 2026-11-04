#!/usr/bin/env python3
"""Check that deployment route probes use the recorded environment's host."""

import importlib.util
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('press_operations', Path(__file__).with_name('press-operations.py'))
operations = importlib.util.module_from_spec(spec)
spec.loader.exec_module(operations)


class ProductionRouteTest(unittest.TestCase):
    def test_probe_uses_allowed_production_host_and_checks_slot(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'nginx/upstream').mkdir(parents=True)
            (root / 'nginx/upstream/active.conf').write_text('set $upstream_app app-blue;\n')

            def curl(command, **kwargs):
                self.assertIn('api.pheeeew.com:443:127.0.0.1', command)
                self.assertTrue(command[-1].startswith('https://api.pheeeew.com:443/'))
                Path(command[command.index('-o') + 1]).write_text('{"available":true}')
                Path(command[command.index('-D') + 1]).write_text('HTTP/1.1 200 OK\nX-Upstream: app-blue\n')
                return type('Result', (), {'stdout': '200'})()

            old = os.getcwd()
            self.addCleanup(os.chdir, old)
            os.chdir(root)
            with patch.object(operations.subprocess, 'run', side_effect=curl):
                ops = operations.Operations(None)
                ops.route('app-blue', 443, 'api.pheeeew.com')
                with self.assertRaises(ValueError):
                    ops.route('app-green', 443, 'api.pheeeew.com')
                with self.assertRaises(ValueError):
                    ops.route('app-blue', 443, 'example.com')
        self.assertEqual(operations.probe_host('pheeeew-prod-app-blue'), 'api.pheeeew.com')
        self.assertEqual(operations.probe_host('pheeeew-dev-app-blue'), 'api-dev.pheeeew.com')


if __name__ == '__main__':
    unittest.main()
