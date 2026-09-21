import importlib.util
from pathlib import Path
import plistlib
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('symbols', Path(__file__).with_name('upload-ios-symbols.py'))
symbols = importlib.util.module_from_spec(spec)
spec.loader.exec_module(symbols)


class ArchiveValidationTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.archive = Path(self.temp.name)
        self.app = self.archive / 'Products/Applications/Pheeeew.app'
        self.app.mkdir(parents=True)
        self.info = {'CFBundleExecutable': 'Pheeeew', 'CFBundleShortVersionString': '1.1.0',
                     'CFBundleVersion': '2', 'MONITORING_ENVIRONMENT': 'prod'}
        self.write_info()
        file = self.archive / 'dSYMs/Pheeeew.app.dSYM/Contents/Resources/DWARF/Pheeeew'
        file.parent.mkdir(parents=True)
        file.touch()

    def write_info(self):
        (self.app / 'Info.plist').write_bytes(plistlib.dumps(self.info))

    def test_matching_symbols_accept_archive(self):
        with patch.object(symbols, 'debug_ids', return_value={'APP-UUID'}):
            self.assertEqual(symbols.validate_archive(self.archive)[2:], ('1.1.0', '2'))

    def test_wrong_build_symbols_rejected(self):
        with patch.object(symbols, 'debug_ids', side_effect=[{'APP-UUID'}, {'OLD-UUID'}]):
            with self.assertRaisesRegex(ValueError, 'matching dSYMs'):
                symbols.validate_archive(self.archive)

    def test_missing_uuid_rejected(self):
        with patch.object(symbols, 'debug_ids', return_value=set()):
            with self.assertRaises(ValueError):
                symbols.validate_archive(self.archive)

    def test_dev_archive_rejected(self):
        self.info['MONITORING_ENVIRONMENT'] = 'dev'
        self.write_info()
        with self.assertRaisesRegex(ValueError, 'must be prod'):
            symbols.validate_archive(self.archive)

    def test_missing_build_rejected(self):
        del self.info['CFBundleVersion']
        self.write_info()
        with self.assertRaisesRegex(ValueError, 'version/build'):
            symbols.validate_archive(self.archive)


if __name__ == '__main__':
    unittest.main()
