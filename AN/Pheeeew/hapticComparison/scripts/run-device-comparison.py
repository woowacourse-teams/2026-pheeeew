#!/usr/bin/env python3
"""Install only dedicated comparison packages and retain raw device evidence."""
import argparse
import io
import hashlib
import json
import re
import subprocess
import tarfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PREFIX = "com.pheeeew.hapticcomparison."


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--serial", required=True)
    parser.add_argument("--adb", required=True)
    parser.add_argument("--aapt", required=True)
    parser.add_argument("--session", default="device-01")
    parser.add_argument("--cold-runs", type=int, default=10)
    parser.add_argument("--core-runs", type=int, default=1)
    args = parser.parse_args()
    output = ROOT / "measurements/haptics/jindong/device" / args.session
    if (output / "metadata.json").exists():
        raise FileExistsError("Use a new --session name to preserve previous evidence")
    output.mkdir(parents=True, exist_ok=True)
    adb = [args.adb, "-s", args.serial]

    def run(*parts, timeout=180):
        result = subprocess.run(adb + list(parts), capture_output=True, timeout=timeout)
        if result.returncode:
            raise RuntimeError(f"adb failed {parts}: {result.stderr.decode(errors='replace')}")
        return result.stdout

    def shell(*parts):
        return run("shell", *parts).decode(errors="replace").strip()

    def identity(package):
        dump = shell("dumpsys", "package", package)
        fields = {}
        for field in ["userId", "codePath", "versionCode", "firstInstallTime", "lastUpdateTime"]:
            match = re.search(rf"^\s*{field}=([^\n]+)", dump, re.M)
            fields[field] = match.group(1).strip() if match else None
        return fields

    before_packages = shell("pm", "list", "packages", "com.pheeeew").splitlines()
    originals = [p.removeprefix("package:") for p in before_packages if not p.removeprefix("package:").startswith(PREFIX)]
    before = {p: identity(p) for p in originals}
    original_setting = shell("settings", "get", "system", "haptic_feedback_enabled")
    source_files = [
        ROOT / "hapticComparison/sharedMain/com/pheeeew/hapticcomparison/HapticComparisonContent.kt",
        ROOT / "hapticComparison/src/main/kotlin/com/pheeeew/hapticcomparison/ComparisonActivity.kt",
        ROOT / "hapticComparison/src/androidTest/kotlin/com/pheeeew/hapticcomparison/HapticComparisonDeviceTest.kt",
        ROOT / "shared/src/commonMain/kotlin/com/pheeeew/feature/emotion/component/EmotionHapticFeedback.kt",
        ROOT / "shared/src/androidMain/kotlin/com/pheeeew/feature/emotion/component/EmotionHapticFeedback.android.kt",
        ROOT / "shared/src/commonMain/kotlin/com/pheeeew/core/haptics/JindongEmotionHapticController.kt",
    ]
    metadata = {
        "sourceSha256": {str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest() for p in source_files},
        "jindongPatternDurationMs": int(re.search(r"duration\s*=\s*(\d+)\.milliseconds", source_files[-1].read_text()).group(1)),
        "model": shell("getprop", "ro.product.model"),
        "android": shell("getprop", "ro.build.version.release"),
        "sdk": shell("getprop", "ro.build.version.sdk"),
        "deviceTime": shell("date", "+%Y-%m-%dT%H:%M:%S%z"),
        "hapticSettingBefore": original_setting,
        "lowPower": shell("settings", "get", "global", "low_power"),
        "displaySize": shell("wm", "size"),
        "displayDensity": shell("wm", "density"),
        "animationScales": {key: shell("settings", "get", "global", key) for key in ["window_animation_scale", "transition_animation_scale", "animator_duration_scale"]},
        "originalPackagesBefore": before,
        "runs": [],
    }
    (output / "metadata.json").write_text(json.dumps(metadata, indent=2))

    def instrument(backend, phase, index):
        package = PREFIX + backend
        run_id = f"{args.session}-{backend}-{phase}-{index:02d}"
        print(f"START {run_id}", flush=True)
        shell("am", "force-stop", package)
        command = ["shell", "am", "instrument", "-w", "-r", "-e", "runId", run_id, "-e", "phase", phase,
                   "-e", "class", "com.pheeeew.hapticcomparison.HapticComparisonDeviceTest", package + ".test/androidx.test.runner.AndroidJUnitRunner"]
        raw = run(*command, timeout=300).decode(errors="replace")
        (output / f"{run_id}-instrumentation.txt").write_text(raw)
        payload = run("exec-out", "run-as", package, "tar", "-C", "files/haptic-comparison", "-cf", "-", run_id)
        (output / f"{run_id}.tar").write_bytes(payload)
        with tarfile.open(fileobj=io.BytesIO(payload)) as archive:
            for member in archive.getmembers():
                if member.issym() or member.islnk() or not (output / member.name).resolve().is_relative_to(output.resolve()):
                    raise ValueError("Unexpected archive entry")
            archive.extractall(output)
        data = json.loads((output / run_id / "result.json").read_text())
        assert data["package"] == package
        assert all(row["backend"] == backend for row in data["rows"])
        expected_rows = {"cold": 1, "core": 142, "project-pad": 30, "settings-off": 10}
        ok = ("OK (1 test)" in raw and "FAILURES" not in raw and "INSTRUMENTATION_FAILED" not in raw
              and len(data["rows"]) == expected_rows[phase])
        metadata["runs"].append({"id": run_id, "phase": phase, "backend": backend, "success": ok, "rows": len(data["rows"])})
        (output / "metadata.json").write_text(json.dumps(metadata, indent=2))
        print(f"END {run_id}: success={ok}, rows={len(data['rows'])}", flush=True)
        if not ok:
            raise RuntimeError(f"Instrumentation failed; see {run_id}-instrumentation.txt")

    try:
        build_manifest = json.loads((ROOT / "hapticComparison/build/comparison-apks/manifest.json").read_text())
        for relative, expected_hash in build_manifest["sourceSha256"].items():
            assert hashlib.sha256((ROOT / relative).read_bytes()).hexdigest() == expected_hash, "Comparison sources changed after building"
        for relative, expected_hash in build_manifest["apkSha256"].items():
            assert hashlib.sha256((ROOT / "hapticComparison/build/comparison-apks" / relative).read_bytes()).hexdigest() == expected_hash
        (output / "build-manifest.json").write_text(json.dumps(build_manifest, indent=2))
        for backend in ["native", "jindong"]:
            apk_root = ROOT / "hapticComparison/build/comparison-apks" / backend
            for filename in ["app.apk", "test.apk"]:
                apk = apk_root / filename
                badging = subprocess.run([args.aapt, "dump", "badging", str(apk)], capture_output=True, text=True, check=True).stdout
                found = re.search(r"package: name='([^']+)'", badging)
                expected = PREFIX + backend + (".test" if filename == "test.apk" else "")
                assert found and found.group(1) == expected, "Refusing to install an unexpected package"
                (output / f"{backend}-{filename}-identity.json").write_text(json.dumps({
                    "package": expected, "apk": str(apk.relative_to(ROOT)),
                    "sha256": hashlib.sha256(apk.read_bytes()).hexdigest(),
                }, indent=2))
                installed = run("install", "-r", "--no-streaming", str(apk), timeout=180).decode(errors="replace")
                (output / f"install-{backend}-{filename}.txt").write_text(installed)
                assert "Success" in installed
        shell("input", "keyevent", "KEYCODE_WAKEUP")
        shell("wm", "dismiss-keyguard")
        shell("settings", "put", "system", "haptic_feedback_enabled", "1")
        # Alternate order for cold trials, avoiding a single fixed first/second ordering.
        for index in range(1, args.cold_runs + 1):
            order = ["native", "jindong"] if index % 2 else ["jindong", "native"]
            for backend in order:
                instrument(backend, "cold", index)
        for index in range(1, args.core_runs + 1):
            order = ["native", "jindong"] if index % 2 else ["jindong", "native"]
            for backend in order:
                instrument(backend, "core", index)
        for backend in ["native", "jindong"]:
            instrument(backend, "project-pad", 1)
        shell("settings", "put", "system", "haptic_feedback_enabled", "0")
        for backend in ["native", "jindong"]:
            instrument(backend, "settings-off", 1)
    finally:
        if original_setting == "null":
            shell("settings", "delete", "system", "haptic_feedback_enabled")
        else:
            shell("settings", "put", "system", "haptic_feedback_enabled", original_setting)
        after = {p: identity(p) for p in originals}
        metadata["originalPackagesAfter"] = after
        metadata["originalPackageIdentityUnchanged"] = before == after
        metadata["hapticSettingAfter"] = shell("settings", "get", "system", "haptic_feedback_enabled")
        metadata["hapticSettingRestored"] = metadata["hapticSettingAfter"] == original_setting
        (output / "metadata.json").write_text(json.dumps(metadata, indent=2))
        assert metadata["originalPackageIdentityUnchanged"]
        assert metadata["hapticSettingRestored"]
        print("Original package identities unchanged; haptic setting restored", flush=True)


if __name__ == "__main__":
    main()
