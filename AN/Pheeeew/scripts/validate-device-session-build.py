#!/usr/bin/env python3
"""Validate resolved Xcode settings before compiling or archiving. Never emit secret values."""
import os
import plistlib
from pathlib import Path


def validate(values):
    configuration = values.get("CONFIGURATION", "")
    expected = {"Debug": ("dev", "development"), "Release": ("prod", "production")}.get(configuration)
    if expected is None:
        raise ValueError("Unsupported device session build configuration")
    environment, attest_environment = expected
    if values.get("DEVICE_BUILD_CONFIGURATION") != configuration:
        raise ValueError("Device build configuration does not match Xcode configuration")
    if values.get("DEVICE_ENVIRONMENT") != environment:
        raise ValueError("Device environment does not match build configuration")
    expected_url = "https://api-dev.pheeeew.com" if configuration == "Debug" else "https://api.pheeeew.com"
    if values.get("API_BASE_URL") != expected_url:
        raise ValueError("Device API URL does not match build configuration")
    mode = values.get("DEVICE_ATTESTATION_MODE", "")
    if mode not in ("platform_only", "required"):
        raise ValueError("Missing or unknown DEVICE_ATTESTATION_MODE")
    if configuration == "Release" and mode != "required":
        raise ValueError("Release requires device attestation")
    if values.get("APP_ATTEST_ENVIRONMENT") != attest_environment:
        raise ValueError("App Attest environment does not match build configuration")
    if configuration == "Release":
        if values.get("PRODUCT_BUNDLE_IDENTIFIER") != "com.itda.pheeeew":
            raise ValueError("Release requires the official iOS bundle identifier")
        source = Path(values.get("SRCROOT", "."))
        entitlement_path = values.get("CODE_SIGN_ENTITLEMENTS", "")
        if not entitlement_path:
            raise ValueError("Release requires App Attest entitlements")
        try:
            with (source / entitlement_path).open("rb") as file:
                entitlements = plistlib.load(file)
        except (OSError, ValueError, plistlib.InvalidFileException) as error:
            raise ValueError("Cannot read Release App Attest entitlements") from None
        value = entitlements.get("com.apple.developer.devicecheck.appattest-environment")
        if value not in ("production", "$(APP_ATTEST_ENVIRONMENT)"):
            raise ValueError("Release App Attest entitlement must be production")


if __name__ == "__main__":
    try:
        validate(os.environ)
    except ValueError as error:
        raise SystemExit(str(error))
    print("Device session build configuration validated (values hidden).")
