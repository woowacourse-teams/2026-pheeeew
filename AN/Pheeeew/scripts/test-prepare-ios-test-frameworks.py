#!/usr/bin/env python3
"""Verify test framework integrity/version checks without downloading SDKs."""
import importlib.util
from pathlib import Path
import tempfile
import unittest
import zipfile

spec = importlib.util.spec_from_file_location("prepare", Path(__file__).with_name("prepare-ios-test-frameworks.py"))
prepare = importlib.util.module_from_spec(spec)
spec.loader.exec_module(prepare)


class FrameworkPreparationTest(unittest.TestCase):
    def test_app_pin_matches(self):
        prepare.check_app_version(Path(__file__).parents[1] / "iosApp/iosApp.xcodeproj/project.pbxproj")

    def test_changed_app_pin_requires_checksum_update(self):
        with tempfile.TemporaryDirectory() as directory:
            project = Path(directory) / "project.pbxproj"
            project.write_text(
                'repositoryURL = "https://github.com/getsentry/sentry-cocoa";'
                'requirement = { kind = exactVersion; version = 99.0.0; };'
            )
            with self.assertRaisesRegex(ValueError, "exact Sentry pin"):
                prepare.check_app_version(project)

    def test_modified_archive_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            archive = Path(directory) / "corrupt.zip"
            archive.write_bytes(b"not the official archive")
            with self.assertRaisesRegex(ValueError, "checksum"):
                prepare.verify_archive(archive)

    def test_extracts_only_simulator_slice(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            archive = root / "test.zip"
            with zipfile.ZipFile(archive, "w") as zipped:
                zipped.writestr(str(prepare.SLICE / "Sentry.framework/Sentry"), b"fixture")
                zipped.writestr("Sentry.xcframework/ios-arm64/Sentry.framework/Sentry", b"device")
            prepare.extract_simulator(archive, root / "output")
            self.assertEqual((root / "output" / prepare.SLICE / "Sentry.framework/Sentry").read_bytes(), b"fixture")
            self.assertFalse((root / "output/Sentry.xcframework/ios-arm64").exists())

    def test_path_traversal_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            archive = root / "test.zip"
            with zipfile.ZipFile(archive, "w") as zipped:
                zipped.writestr("../outside", b"bad")
            with self.assertRaisesRegex(ValueError, "archive path"):
                prepare.extract_simulator(archive, root / "output")


if __name__ == "__main__":
    unittest.main()
