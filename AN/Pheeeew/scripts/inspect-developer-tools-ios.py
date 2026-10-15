#!/usr/bin/env python3
"""Audit a Release archive together with its Gradle graph and linker provenance."""
import argparse
import hashlib
from pathlib import Path
import plistlib

MARKERS = (b"DeveloperToolsInfo", b"DeveloperToolsScreen", b"DebugMainViewController", b"com.pheeeew.developertools")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--archive", type=Path, required=True)
    parser.add_argument("--build-log", type=Path, required=True)
    parser.add_argument("--graph-log", type=Path, required=True)
    parser.add_argument("--link-map", type=Path, required=True)
    args = parser.parse_args()
    log = args.build_log.read_text()
    assert "** ARCHIVE SUCCEEDED **" in log, "Archive did not succeed"
    assert ":shared:linkReleaseFrameworkIosArm64" in log, "Missing Release framework link evidence"
    assert ":developerTools:" not in log, "Developer tools task ran in the Release archive build"
    graph = args.graph_log.read_text()
    assert "iosArm64.main developerTools=false" in graph, "Missing resolved Release dependency evidence"
    assert "BUILD SUCCESSFUL" in graph, "Dependency graph verification failed"
    link_map = args.link_map.read_bytes()
    assert b"Shared.framework/Shared" in link_map, "Missing static Shared framework in the link map"
    assert b"developerTools" not in link_map, "Developer tools input in Release link map"
    apps = list((args.archive / "Products/Applications").glob("*.app"))
    assert len(apps) == 1, "Expected exactly one archived app"
    app = apps[0]
    with (app / "Info.plist").open("rb") as file:
        info = plistlib.load(file)
    assert info["DEVICE_BUILD_CONFIGURATION"] == "Release"
    assert info["DEVICE_ENVIRONMENT"] == "prod"
    assert info["API_BASE_URL"] == "https://api.pheeeew.com"
    executable = app / info["CFBundleExecutable"]
    files = [path for path in app.rglob("*") if path.is_file()]
    hits = [str(path.relative_to(app)) for path in files if any(marker in path.read_bytes() for marker in MARKERS)]
    assert not hits, f"Developer tools code/resources found: {hits}"
    print(f"{app}: {len(files)} executable/framework/resource files inspected, developerTools=false")
    print(f"version={info['CFBundleShortVersionString']} build={info['CFBundleVersion']} configuration=Release env=prod")
    print("executable sha256=" + hashlib.sha256(executable.read_bytes()).hexdigest())
    print(f"{args.link_map}: static Shared input present, no developer tools input")
    print("Release Gradle graph and Xcode task provenance verified; symbol absence is only a supplementary check.")


if __name__ == "__main__":
    main()
