#!/usr/bin/env python3
"""Release manifests must come from a commit, not the current working tree."""

import hashlib
import importlib.util
from pathlib import Path
import subprocess
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('prod_manifest', Path(__file__).with_name('press-prod-manifest.py'))
manifest = importlib.util.module_from_spec(spec)
spec.loader.exec_module(manifest)


class ProductionManifestTest(unittest.TestCase):
    def test_manifest_reads_exact_committed_artifacts(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            subprocess.run(['git', 'init', '-q', str(root)], check=True)
            for path in manifest.FILES.values():
                file = root / path
                file.parent.mkdir(parents=True, exist_ok=True)
                file.write_text('committed ' + path)
            subprocess.run(['git', '-C', str(root), 'add', '.'], check=True)
            subprocess.run(['git', '-C', str(root), '-c', 'user.name=Fixture',
                            '-c', 'user.email=fixture@example.invalid', '-c', 'commit.gpgsign=false',
                            'commit', '-qm', 'fixture'], check=True)
            revision = subprocess.run(['git', '-C', str(root), 'rev-parse', 'HEAD'], check=True,
                                      capture_output=True, text=True).stdout.strip()
            source = manifest.FILES['compose.yml']
            (root / source).write_text('uncommitted edit')
            result = manifest.make_manifest(root, revision)
            self.assertEqual(result['source_revision'], revision)
            self.assertEqual(result['files']['compose.yml'],
                             hashlib.sha256(('committed ' + source).encode()).hexdigest())
            with self.assertRaises(ValueError):
                manifest.make_manifest(root, 'main')


if __name__ == '__main__':
    unittest.main()
