#!/usr/bin/env python3
"""Prepare the app-pinned Sentry simulator framework for Kotlin/Native tests only."""
import argparse
import hashlib
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import zipfile

# Official sentry-cocoa/8.58.2 Package.swift binaryTarget("Sentry") checksum:
# https://github.com/getsentry/sentry-cocoa/blob/8.58.2/Package.swift
VERSION = "8.58.2"
SHA256 = "08b0fa226894b146d8bb76d057df245807cd4113a189d0fec8bc7cd69678c9c6"
SLICE = Path("Sentry.xcframework/ios-arm64_x86_64-simulator")


def check_app_version(project_file):
    text = project_file.read_text()
    match = re.search(
        r'repositoryURL\s*=\s*"https://github.com/getsentry/sentry-cocoa";'
        r'[^}]*?kind\s*=\s*exactVersion;\s*version\s*=\s*([0-9.]+);',
        text,
        re.DOTALL,
    )
    if not match or match.group(1) != VERSION:
        raise ValueError("Update the test framework version/checksum to match the app's exact Sentry pin")


def verify_archive(archive):
    checksum = hashlib.sha256()
    with archive.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            checksum.update(chunk)
    digest = checksum.hexdigest()
    if digest != SHA256:
        raise ValueError("Sentry archive checksum mismatch")


def extract_simulator(archive, destination):
    with zipfile.ZipFile(archive) as zipped:
        selected = []
        for item in zipped.infolist():
            path = Path(item.filename)
            if path.is_absolute() or ".." in path.parts:
                raise ValueError("Invalid archive path")
            if path.is_relative_to(SLICE):
                # iOS framework slices use ordinary files; do not materialize archive symlinks.
                if (item.external_attr >> 16) & 0o170000 == 0o120000:
                    raise ValueError("Unexpected archive symlink")
                selected.append(item)
        if not any(item.filename == str(SLICE / "Sentry.framework/Sentry") for item in selected):
            raise ValueError("Sentry simulator binary missing")
        zipped.extractall(destination, members=selected)


def prepare(project_file, output):
    check_app_version(project_file)
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="sentry-test-", dir=output.parent) as temporary:
        temporary = Path(temporary)
        archive = temporary / "Sentry.zip"
        url = f"https://github.com/getsentry/sentry-cocoa/releases/download/{VERSION}/Sentry.xcframework.zip"
        subprocess.run(
            ["curl", "--fail", "--location", "--retry", "2", "--connect-timeout", "20", "--max-time", "300",
             "--output", str(archive), url],
            check=True,
        )
        verify_archive(archive)
        extracted = temporary / "extracted"
        extract_simulator(archive, extracted)
        (extracted / "verified-sha256.txt").write_text(f"{VERSION}\n{SHA256}\n{url}\n")
        if output.exists():
            shutil.rmtree(output)
        extracted.rename(output)
    print(f"Prepared Sentry {VERSION} iOS simulator slice (verified SHA-256)")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--project", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    prepare(args.project, args.output)


if __name__ == "__main__":
    main()
