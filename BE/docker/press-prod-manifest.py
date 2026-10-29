#!/usr/bin/env python3
"""Build the production bootstrap file manifest from an immutable Git commit."""

import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys

FILES = {
    'compose.yml': 'BE/docker/compose.prod-bluegreen.yml',
    'compose.nginx.yml': 'BE/docker/compose.nginx.prod-bluegreen.yml',
    'compose.monitoring.yml': 'BE/docker/compose.monitoring.prod-bluegreen.yml',
    'nginx/api-bluegreen.conf': 'BE/docker/nginx/api-prod-bluegreen.conf',
    'alloy/config.prod-bluegreen.alloy': 'BE/docker/alloy/config.prod-bluegreen.alloy',
    'press-generation.py': 'BE/docker/press-generation.py',
    'press-operations.py': 'BE/docker/press-operations.py',
    'press-prod-bootstrap.py': 'BE/docker/press-prod-bootstrap.py',
    'press-prod-deploy.sh': 'BE/docker/press-prod-deploy.sh',
    'press-prod-deploy-guard.sh': 'BE/docker/press-prod-deploy-guard.sh',
    'press-prod-lock.py': 'BE/docker/press-prod-lock.py',
    'press-prod-preflight.py': 'BE/docker/press-prod-preflight.py',
    'press-prod-manifest.py': 'BE/docker/press-prod-manifest.py',
}


def make_manifest(repository, revision):
    if not re.fullmatch(r'[0-9a-f]{40}', revision):
        raise ValueError('Use the exact 40-character release commit SHA')
    hashes = {}
    for destination, source in FILES.items():
        content = subprocess.run(['git', '-C', str(repository), 'show', f'{revision}:{source}'],
                                 check=True, capture_output=True, timeout=15).stdout
        hashes[destination] = hashlib.sha256(content).hexdigest()
    return {'source_revision': revision, 'files': hashes}


if __name__ == '__main__':
    if len(sys.argv) != 2:
        raise SystemExit('usage: press-prod-manifest.py RELEASE_COMMIT_SHA')
    try:
        print(json.dumps(make_manifest(Path(__file__).resolve().parents[2], sys.argv[1]),
                         sort_keys=True, separators=(',', ':')))
    except (ValueError, OSError, subprocess.SubprocessError) as error:
        raise SystemExit('Cannot create release manifest: ' + str(error))
