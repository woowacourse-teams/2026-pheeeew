#!/usr/bin/env python3
"""Reject candidate DB drift before Docker starts a migration-capable app."""

import copy
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('prod_preflight', Path(__file__).with_name('press-prod-preflight.py'))
preflight = importlib.util.module_from_spec(spec)
spec.loader.exec_module(preflight)


class ProductionPreflightTest(unittest.TestCase):
    def test_real_compose_renders_the_same_candidate_checked_by_preflight(self):
        generation = '11111111-1111-4111-8111-111111111111'
        env = dict(os.environ, APP_IMAGE='example:prod', DB_URL='jdbc:postgresql://prod-db:5432/pheeeew?sslmode=require',
                   DB_USERNAME='app', DB_PASSWORD='fixture', JWT_PRIVATE_KEY_BASE64='fixture',
                   JWT_PUBLIC_KEY_BASE64='fixture', S3_BUCKET='fixture', S3_KEY_PREFIX='fixture', S3_REGION='fixture',
                   PRESS_GREEN_GENERATION=generation, APP_RESTART_POLICY='unless-stopped')
        active = {'Id': 'active', 'Image': 'old', 'RestartCount': 0,
                  'State': {'StartedAt': '2026-10-29T00:00:00Z'},
                  'Config': {'Labels': {}, 'Env': [f'{key}={env[key]}' for key in ('DB_URL', 'DB_USERNAME')]}}
        state = {'phase': 'PREPARED', 'target_slot': 'app-green', 'active_container': 'pheeeew-prod-app-blue',
                 'target_generation': generation, 'database': ['prod-db', 5432, 'pheeeew', 'app'],
                 'active_process_identity': preflight.core.identity(active, True)}
        compose = Path(__file__).with_name('compose.prod-bluegreen.yml').resolve()
        with tempfile.TemporaryDirectory() as directory:
            def render(policy):
                result = subprocess.run(['docker', 'compose', '-f', str(compose), '--profile', 'green',
                                         'config', '--format', 'json'], cwd=directory, check=True,
                                        capture_output=True, env=dict(env, APP_RESTART_POLICY=policy))
                return json.loads(result.stdout)
            preflight.check(state, 'app-green', 'example:prod', render('unless-stopped'), active)
            with self.assertRaises(ValueError):
                preflight.check(state, 'app-green', 'example:prod', render('no'), active)

    def test_database_image_and_generation_must_match_before_start(self):
        generation = '11111111-1111-4111-8111-111111111111'
        env = {'DB_URL': 'jdbc:postgresql://prod-db:5432/pheeeew?sslmode=require',
               'DB_USERNAME': 'app', 'SPRING_PROFILES_ACTIVE': 'prod',
               preflight.core.GENERATION_KEY: generation}
        active = {'Id': 'active', 'Image': 'old', 'RestartCount': 0,
                  'State': {'StartedAt': '2026-10-29T00:00:00Z'},
                  'Config': {'Labels': {}, 'Env': [f'{key}={value}' for key, value in env.items()]}}
        state = {'phase': 'PREPARED', 'target_slot': 'app-green',
                 'active_container': 'pheeeew-prod-app-blue', 'target_generation': generation,
                 'database': ['prod-db', 5432, 'pheeeew', 'app'],
                 'active_process_identity': preflight.core.identity(active, True)}
        rendered = {'services': {'app-green': {'container_name': 'pheeeew-prod-app-green',
                                                'image': 'example:prod', 'restart': 'unless-stopped',
                                                'environment': env}}}
        preflight.check(state, 'app-green', 'example:prod', rendered, active)
        for key, value in (('DB_URL', 'jdbc:postgresql://other:5432/pheeeew?sslmode=require'),
                           ('DB_URL', 'jdbc:postgresql://prod-db:5432/pheeeew?sslmode=disable'),
                           ('DB_USERNAME', 'other'), ('SPRING_PROFILES_ACTIVE', 'dev'),
                           (preflight.core.GENERATION_KEY, 'wrong')):
            changed = copy.deepcopy(rendered)
            changed['services']['app-green']['environment'][key] = value
            with self.assertRaises(ValueError, msg=key):
                preflight.check(state, 'app-green', 'example:prod', changed, active)
        with self.assertRaises(ValueError):
            preflight.check(state, 'app-green', 'other:image', rendered, active)
        changed = copy.deepcopy(rendered)
        changed['services']['app-green']['restart'] = 'no'
        with self.assertRaises(ValueError):
            preflight.check(state, 'app-green', 'example:prod', changed, active)
        changed = copy.deepcopy(active)
        changed['RestartCount'] = 1
        with self.assertRaises(ValueError):
            preflight.check(state, 'app-green', 'example:prod', rendered, changed)


if __name__ == '__main__':
    unittest.main()
