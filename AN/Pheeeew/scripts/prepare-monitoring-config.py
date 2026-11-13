#!/usr/bin/env python3
"""Generate ignored iOS configuration from local properties / CI environment without logging values."""
import os
from pathlib import Path

root = Path(__file__).resolve().parents[1]
local = root / "monitoring.local.properties"
values = {}
if local.exists():
    for line in local.read_text().splitlines():
        if line.strip() and not line.lstrip().startswith('#') and '=' in line:
            key, value = line.split('=', 1)
            values[key.strip()] = value.strip()
keys = ['POSTHOG_PROJECT_TOKEN', 'POSTHOG_HOST', 'SENTRY_DSN', 'MONITORING_ENABLED']
lines = ['// Generated locally; do not commit.', 'MONITORING_SLASH = /']
for key in keys:
    value = os.environ.get(key, values.get(key, 'false' if key == 'MONITORING_ENABLED' else ''))
    if any(c in value for c in '\r\n'):
        raise SystemExit('Invalid monitoring configuration value')
    lines.append(key + ' = ' + value.replace('//', '$(MONITORING_SLASH)/'))
target = root / 'iosApp/Configuration/Monitoring.local.xcconfig'
target.write_text('\n'.join(lines) + '\n')
target.chmod(0o600)
print('Generated ignored iOS monitoring configuration (values hidden).')
