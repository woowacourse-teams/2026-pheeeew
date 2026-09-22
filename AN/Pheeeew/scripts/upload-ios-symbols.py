#!/usr/bin/env python3
"""Validate one production archive and upload its matching dSYMs to Sentry."""
import argparse
import os
from pathlib import Path
import plistlib
import re
import subprocess
import sys


def debug_ids(path):
    result = subprocess.run(
        ['xcrun', 'dwarfdump', '--uuid', str(path)],
        check=True, capture_output=True, text=True,
    )
    return set(re.findall(r'UUID: ([0-9A-Fa-f-]{36})', result.stdout.upper()))


def validate_archive(archive):
    apps = list((archive / 'Products/Applications').glob('*.app'))
    if len(apps) != 1:
        raise ValueError('Expected exactly one application in the archive.')
    app = apps[0]
    with (app / 'Info.plist').open('rb') as file:
        info = plistlib.load(file)
    if info.get('MONITORING_ENVIRONMENT') != 'prod':
        raise ValueError('Archive monitoring environment must be prod.')
    version = info.get('CFBundleShortVersionString')
    build = info.get('CFBundleVersion')
    if not version or not build:
        raise ValueError('Archive version/build is missing.')
    executable = app / info['CFBundleExecutable']
    required = debug_ids(executable)
    available = set()
    symbols = archive / 'dSYMs'
    for file in symbols.glob('*.dSYM/Contents/Resources/DWARF/*'):
        available.update(debug_ids(file))
    if not required or not required.issubset(available):
        raise ValueError('Archive executable UUIDs do not have matching dSYMs.')
    return symbols, required, version, build


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('archive', type=Path)
    parser.add_argument('--check-only', action='store_true', help='Validate without uploading or requiring a token.')
    args = parser.parse_args()
    try:
        symbols, required, version, build = validate_archive(args.archive)
        print(f'Validated archive: release=pheeeew@{version}, dist=ios-{build}, environment=prod')
        if args.check_only:
            return 0
        if not os.environ.get('SENTRY_AUTH_TOKEN', '').strip():
            raise ValueError('Provide SENTRY_AUTH_TOKEN through the CI secret environment.')
        command = [os.environ.get('SENTRY_CLI', 'sentry-cli'), 'debug-files', 'upload',
                   '--org', 'pheeeew', '--project', 'pheeeew-client', '--type', 'dsym',
                   '--no-sources', '--wait']
        command.append(str(symbols))
        # Credentials remain in the inherited environment, never in command arguments.
        return subprocess.run(command, check=False).returncode
    except (ValueError, KeyError, OSError, subprocess.CalledProcessError) as error:
        print(f'Symbol validation failed: {type(error).__name__}', file=sys.stderr)
        if isinstance(error, ValueError):
            print(str(error), file=sys.stderr)
        return 1


if __name__ == '__main__':
    sys.exit(main())
