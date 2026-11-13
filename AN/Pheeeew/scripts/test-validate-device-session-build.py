#!/usr/bin/env python3
"""Tests for resolved Xcode build configuration, with no credentials or network access."""
import importlib.util
import pathlib
import tempfile
import unittest
import plistlib

spec = importlib.util.spec_from_file_location("device_build", pathlib.Path(__file__).with_name("validate-device-session-build.py"))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class DeviceBuildValidationTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.entitlements = pathlib.Path(self.directory.name) / "App.entitlements"
        self.write_entitlement("$(APP_ATTEST_ENVIRONMENT)")

    def write_entitlement(self, value):
        self.entitlements.write_bytes(plistlib.dumps({"com.apple.developer.devicecheck.appattest-environment": value}))

    def settings(self, release=True):
        return {
            "CONFIGURATION": "Release" if release else "Debug",
            "DEVICE_BUILD_CONFIGURATION": "Release" if release else "Debug",
            "DEVICE_ENVIRONMENT": "prod" if release else "dev",
            "DEVICE_ATTESTATION_MODE": "required" if release else "platform_only",
            "API_BASE_URL": "https://api.pheeeew.com" if release else "https://api-dev.pheeeew.com",
            "APP_ATTEST_ENVIRONMENT": "production" if release else "development",
            "PRODUCT_BUNDLE_IDENTIFIER": "com.itda.pheeeew",
            "CODE_SIGN_ENTITLEMENTS": str(self.entitlements),
        }

    def test_valid_builds(self):
        module.validate(self.settings())
        module.validate(self.settings(False))
        proof = self.settings(False)
        proof["DEVICE_ATTESTATION_MODE"] = "required"
        module.validate(proof)

    def test_release_rejects_each_unsafe_setting(self):
        invalid = {
            "DEVICE_ATTESTATION_MODE": "platform_only",
            "DEVICE_ENVIRONMENT": "dev",
            "API_BASE_URL": "https://api-dev.pheeeew.com",
            "APP_ATTEST_ENVIRONMENT": "development",
            "DEVICE_BUILD_CONFIGURATION": "Debug",
            "PRODUCT_BUNDLE_IDENTIFIER": "com.pheeeew.usbproof",
            "CODE_SIGN_ENTITLEMENTS": "",
        }
        for key, value in invalid.items():
            with self.subTest(key=key), self.assertRaises(ValueError):
                module.validate(dict(self.settings(), **{key: value}))

    def test_missing_and_unresolved_settings_fail(self):
        for key in ("DEVICE_ATTESTATION_MODE", "DEVICE_ENVIRONMENT", "DEVICE_BUILD_CONFIGURATION", "API_BASE_URL"):
            for value in ("", "$(" + key + ")", "typo"):
                with self.subTest(key=key, value=value), self.assertRaises(ValueError):
                    module.validate(dict(self.settings(False), **{key: value}))

    def test_release_rejects_development_or_missing_entitlement(self):
        for value in ("development", "", "$(OTHER_SETTING)"):
            self.write_entitlement(value)
            with self.assertRaises(ValueError):
                module.validate(self.settings())


if __name__ == "__main__":
    unittest.main()
