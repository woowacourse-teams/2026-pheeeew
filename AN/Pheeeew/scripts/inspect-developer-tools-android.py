#!/usr/bin/env python3
"""Inspect APK/AAB entries, DEX and R8 inputs. Pair with Gradle graph verification."""
import argparse
import hashlib
from pathlib import Path
import zipfile

MARKERS = (
    "com/pheeeew/developertools",
    "com.pheeeew.developertools",
    "DeveloperToolsInfo",
    "DeveloperToolsScreenKt",
)


def matches(data):
    return any(marker.encode(encoding) in data for marker in MARKERS for encoding in ("utf-8", "utf-16-le"))


def inspect(path, expect_tools):
    with zipfile.ZipFile(path) as archive:
        entries = archive.namelist()
        dex = [name for name in entries if name.endswith(".dex")]
        manifests = [name for name in entries if name.endswith("AndroidManifest.xml")]
        if not (dex and manifests):
            raise ValueError(f"Not an APK/AAB: {path}")
        found = [name for name in entries if matches(name.encode()) or matches(archive.read(name))]
        if not (bool(found) == expect_tools):
            raise ValueError(f"Unexpected developer tools entries in {path}: {found}")
        print(f"{path}: developerTools={bool(found)}, DEX={len(dex)}, manifests={len(manifests)}")
        print("sha256=" + hashlib.sha256(path.read_bytes()).hexdigest())


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--debug-apk", type=Path, required=True, help="Positive control: tools must be present")
    parser.add_argument("--release-apk", type=Path, required=True)
    parser.add_argument("--release-aab", type=Path, required=True)
    parser.add_argument("--mapping", type=Path, required=True, help="R8 mapping for this Release build")
    args = parser.parse_args()
    inspect(args.debug_apk, True)
    inspect(args.release_apk, False)
    inspect(args.release_aab, False)
    if not (not matches(args.mapping.read_bytes())):
        raise ValueError("Developer tools were supplied to R8")
    print(f"{args.mapping}: no developer tools input classes")
    print("Pair this result with :androidApp:verifyDeveloperToolsIsolation; absence of names alone is insufficient.")


if __name__ == "__main__":
    main()
