#!/usr/bin/env python3
"""Validate resolved Xcode build settings without printing collection credentials."""
import os
from urllib.parse import urlsplit


def validate(values):
    configuration = values.get('CONFIGURATION', '')
    environment = values.get('MONITORING_ENVIRONMENT', '')
    expected = {'Debug': 'dev', 'Release': 'prod'}.get(configuration)
    if expected is None:
        raise ValueError('Unsupported build configuration; define its monitoring environment explicitly.')
    if environment != expected:
        raise ValueError('Monitoring environment does not match the build configuration.')
    enabled = values.get('MONITORING_ENABLED', '')
    if enabled not in ('true', 'false'):
        raise ValueError('Set MONITORING_ENABLED explicitly (true or false).')
    if enabled == 'false':
        return
    for key in ('POSTHOG_PROJECT_TOKEN', 'POSTHOG_HOST', 'SENTRY_DSN'):
        value = values.get(key, '')
        if not value.strip() or any(c in value for c in '\r\n') or '$(' in value:
            raise ValueError('Missing or unresolved monitoring setting: ' + key)
    for key in ('POSTHOG_HOST', 'SENTRY_DSN'):
        try:
            url = urlsplit(values[key])
            valid = url.scheme == 'https' and bool(url.hostname)
        except ValueError:
            valid = False
        if not valid:
            raise ValueError('Invalid HTTPS monitoring endpoint: ' + key)


if __name__ == '__main__':
    try:
        validate(os.environ)
    except ValueError as error:
        raise SystemExit(str(error))
    print('Monitoring build configuration validated (values hidden).')
