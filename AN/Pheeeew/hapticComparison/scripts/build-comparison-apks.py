#!/usr/bin/env python3
"""Build both isolated packages sequentially and freeze their source/APK hashes."""
import hashlib
import json
import shutil
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUTPUT = ROOT / "hapticComparison/build/comparison-apks"


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    OUTPUT.mkdir(parents=True, exist_ok=True)
    for backend in ["native", "jindong"]:
        with (OUTPUT / f"build-{backend}.log").open("w") as log:
            subprocess.run([
                str(ROOT / "gradlew"), ":hapticComparison:assembleDebug", ":hapticComparison:assembleDebugAndroidTest",
                ":hapticComparison:ktlintCheck", ":shared:ktlintCheck", "-PhapticComparisonBuild=true",
                f"-PemotionHapticBackend={backend}", "--console=plain",
            ], cwd=ROOT, stdout=log, stderr=subprocess.STDOUT, check=True)
        target = OUTPUT / backend
        target.mkdir(exist_ok=True)
        shutil.copy2(ROOT / "hapticComparison/build/outputs/apk/debug/hapticComparison-debug.apk", target / "app.apk")
        shutil.copy2(ROOT / "hapticComparison/build/outputs/apk/androidTest/debug/hapticComparison-debug-androidTest.apk", target / "test.apk")
    sources = [
        *sorted((ROOT / "hapticComparison/sharedMain").rglob("*.kt")),
        *sorted((ROOT / "hapticComparison/src").rglob("*.kt")),
        ROOT / "shared/src/commonMain/kotlin/com/pheeeew/feature/emotion/component/EmotionHapticFeedback.kt",
        ROOT / "shared/src/androidMain/kotlin/com/pheeeew/feature/emotion/component/EmotionHapticFeedback.android.kt",
        ROOT / "shared/src/commonMain/kotlin/com/pheeeew/core/haptics/JindongEmotionHapticController.kt",
    ]
    manifest = {
        "sourceSha256": {str(p.relative_to(ROOT)): digest(p) for p in sources},
        "apkSha256": {str(p.relative_to(OUTPUT)): digest(p) for p in sorted(OUTPUT.glob("*/*.apk"))},
    }
    (OUTPUT / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
    print(f"Comparison APKs and manifest: {OUTPUT}")


if __name__ == "__main__":
    main()
