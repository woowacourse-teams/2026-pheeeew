#!/usr/bin/env python3
"""Exercise the real variant guard without packaging, signing or contacting an API."""
import os
import pathlib
import subprocess
import tempfile

INIT = '''
allprojects { p ->
    if (p.path == ':androidApp') {
        p.plugins.withId('com.android.application') {
            p.extensions.getByName('androidComponents').finalizeDsl { android ->
                def release = android.buildTypes.getByName('release')
                def mode = p.findProperty('testInvalidMode')
                if (mode != null) release.buildConfigField('String', 'DEVICE_ATTESTATION_MODE', '"' + mode + '"')
                def url = p.findProperty('testInvalidUrl')
                if (url != null) release.buildConfigField('String', 'API_BASE_URL', '"' + url + '"')
                if (p.hasProperty('testInvalidPackage')) release.applicationIdSuffix = '.invalid'
            }
        }
    }
}
'''


def main():
    root = pathlib.Path(__file__).resolve().parent.parent
    cases = [
        ("valid-release", "Release", [], True),
        ("missing-project", "Release", ["-PdeviceCloudProjectNumber="], False),
        ("bypass", "Release", ["-PtestInvalidMode=platform_only"], False),
        ("dev-url", "Release", ["-PtestInvalidUrl=https://api-dev.pheeeew.com"], False),
        ("wrong-package", "Release", ["-PtestInvalidPackage=true"], False),
        ("debug-without-project", "Debug", ["-PdeviceCloudProjectNumber="], True),
        ("debug-proof-without-project", "Debug", ["-PdeviceDebugAttestationMode=required", "-PdeviceCloudProjectNumber="], False),
    ]
    with tempfile.TemporaryDirectory() as directory:
        init = pathlib.Path(directory) / "invalid.init.gradle"
        init.write_text(INIT)
        for name, variant, args, success in cases:
            result = subprocess.run(
                [str(root / "gradlew"), f":androidApp:generate{variant}BuildConfig", "--console=plain", "-I", str(init), *args],
                cwd=root, env=dict(os.environ, MONITORING_ENABLED="false"),
                stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True,
            )
            if success:
                assert result.returncode == 0, f"{name}: valid build rejected"
            else:
                assert result.returncode != 0, f"{name}: invalid build accepted"
                assert f":androidApp:validate{variant}DeviceSession FAILED" in result.stdout, f"{name}: failed for unrelated reason"
            print(name + ": PASS", flush=True)


if __name__ == "__main__":
    main()
