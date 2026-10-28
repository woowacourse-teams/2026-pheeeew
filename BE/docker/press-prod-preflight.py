#!/usr/bin/env python3
"""Check the rendered candidate configuration before starting its process."""

import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys

spec = importlib.util.spec_from_file_location('press_generation', Path(__file__).with_name('press-generation.py'))
core = importlib.util.module_from_spec(spec)
spec.loader.exec_module(core)


def check(state, target, image, rendered, active):
    if (target not in ('app-blue', 'app-green') or state.get('phase') != 'PREPARED'
            or state.get('target_slot') != target or not state['active_container'].startswith('pheeeew-prod-app-')):
        raise ValueError('Unexpected production deployment state')
    service = rendered['services'][target]
    env = service['environment']
    current_env = core.environment(active)
    if (service.get('container_name') != 'pheeeew-prod-' + target or service.get('image') != image
            or service.get('restart') != 'unless-stopped'
            or env.get('SPRING_PROFILES_ACTIVE') != 'prod'
            or env.get(core.GENERATION_KEY) != state['target_generation']
            or env.get('DB_URL') != current_env.get('DB_URL')
            or env.get('DB_USERNAME') != current_env.get('DB_USERNAME')
            or list(core.db_target(env)) != state['database']
            or list(core.db_target(current_env)) != state['database']
            or core.identity(active, True) != state['active_process_identity']):
        raise ValueError('Candidate configuration differs from the active production database or image')


def main():
    if len(sys.argv) != 2:
        raise SystemExit('usage: press-prod-preflight.py app-blue|app-green')
    try:
        target = sys.argv[1]
        state = json.loads(core.STATE_FILE.read_text())
        active = core.container(state['active_container'])
        result = subprocess.run(['docker', 'compose', '--env-file', '.env', '--env-file',
                                 'press-generations.env', '-f', 'compose.yml', '--profile',
                                 target.removeprefix('app-'), 'config', '--format', 'json'],
                                capture_output=True, check=True, timeout=30)
        check(state, target, os.environ['APP_IMAGE'], json.loads(result.stdout), active)
        print('Candidate configuration matches the active production database.')
    except (OSError, ValueError, KeyError, TypeError, subprocess.SubprocessError):
        print('Candidate preflight failed; preserve the deployment journal and current app.', file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
