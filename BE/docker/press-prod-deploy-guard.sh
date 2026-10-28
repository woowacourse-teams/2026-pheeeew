#!/bin/sh
set -eu

root=${1:?production directory is required}
python3 - "$root" <<'PY'
import json
import os
from pathlib import Path
import re
import stat
import sys
from datetime import date
from uuid import UUID

root = Path(sys.argv[1])
path = root / '.press-prod-bootstrap.json'
try:
    if os.path.lexists(str(path) + '.new'):
        raise ValueError('incomplete bootstrap record')
    fd = os.open(path, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
    try:
        if not stat.S_ISREG(os.fstat(fd).st_mode):
            raise ValueError('invalid bootstrap record')
        with os.fdopen(fd, 'rb', closefd=False) as source:
            raw = source.read(1024 * 1024 + 1)
    finally:
        os.close(fd)
    if len(raw) > 1024 * 1024:
        raise ValueError('bootstrap record too large')
    state = json.loads(raw)
    probe = state['press_probe']
    generation = state['target_generation']
    if (state['phase'] != 'FINALIZED' or str(UUID(generation)) != generation
            or not re.fullmatch(r'[0-9a-f]{40}', state['release_revision'])
            or not re.fullmatch(r'[0-9a-f]{64}', state['release_manifest_sha256'])
            or not re.fullmatch(r'[0-9a-f]{64}', state['expected_proxy_config_sha256'])
            or str(UUID(probe['device'])) != probe['device']
            or date.fromisoformat(probe['date']).isoformat() != probe['date']
            or probe['state'] != 'FRUSTRATED' or not isinstance(probe['device_id'], int)
            or not isinstance(probe['before'], int) or probe['before'] < 0):
        raise ValueError('bootstrap is not finalized')
except (OSError, ValueError, KeyError, TypeError, json.JSONDecodeError):
    print('Production bootstrap is incomplete or unverified; ordinary deployment is blocked.', file=sys.stderr)
    sys.exit(42)
PY
