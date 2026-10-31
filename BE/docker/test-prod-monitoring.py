#!/usr/bin/env python3
"""Check production monitoring's app slots and read-only log mounts."""

from pathlib import Path
import unittest
import yaml

HERE = Path(__file__).resolve().parent


class ProductionMonitoringTest(unittest.TestCase):
    def test_both_slots_are_scraped_and_logs_are_mounted_read_only(self):
        config = (HERE / 'alloy/config.prod-bluegreen.alloy').read_text()
        for color in ('blue', 'green'):
            self.assertIn(f'__address__ = "app-{color}:8080"', config)
            self.assertIn(f'__path__    = "/var/log/pheeeew-{color}/application.json"', config)
            self.assertIn(f'instance    = "pheeeew-prod-{color}"', config)
        compose = yaml.safe_load((HERE / 'compose.monitoring.prod-bluegreen.yml').read_text())
        self.assertEqual(compose['networks']['app']['name'], 'pheeeew-prod_default')
        mounts = compose['services']['alloy']['volumes']
        for color in ('blue', 'green'):
            self.assertIn(f'app-logs-{color}:/var/log/pheeeew-{color}:ro', mounts)
            self.assertEqual(compose['volumes']['app-logs-' + color]['name'], 'pheeeew-prod_app-logs-' + color)


if __name__ == '__main__':
    unittest.main()
