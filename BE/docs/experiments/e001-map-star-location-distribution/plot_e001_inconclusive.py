"""E001-v1 종료 결과의 사후 진단 그림이에요. 표본 생성·재튜닝을 하지 않아요.

Chart contract: synthetic point-level scatter, A / D120 / D100 facets;
500 single-center and 4,500 imbalanced-grid points per model, all five seeds.
Within each row: equal metre axes, aspect, point size, opacity and gold root.
Static PNG; descriptive titles; no adopted-model or UX superiority claim.
"""

import argparse
import csv
import gzip
import hashlib
import json
import math
from collections import defaultdict
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
from matplotlib import font_manager, pyplot as plt


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("raw", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    checksums = (args.raw / "checksums.sha256").read_bytes()
    assert checksums == (args.raw / "verification-run1-checksums.sha256").read_bytes()
    for line in checksums.decode().splitlines():
        expected, name = line.split("  ", 1)
        assert hashlib.sha256((args.raw / name).read_bytes()).hexdigest() == expected, name
    manifest = json.loads((args.raw / "manifest.json").read_text())
    assert manifest["distributionOutcome"] == "inconclusive" and manifest["outcomeReason"] == "no-d"
    assert manifest["selectedD"] is None and manifest["selectedE"] is None

    groups = defaultdict(list)
    identities = set()
    with gzip.open(args.raw / "coordinates.csv.gz", "rt", newline="") as stream:
        for row in csv.DictReader(stream):
            key = (row["parameter_set_id"], row["scenario_id"])
            identity = (*key, row["sample_seed"], row["center_id"], row["point_index"])
            assert identity not in identities
            identities.add(identity)
            dx, dy = float(row["offset_easting_m"]), float(row["offset_northing_m"])
            radius = math.hypot(dx, dy)
            assert all(math.isfinite(value) for value in (dx, dy, radius))
            if row["model_id"] == "A":
                assert -150 <= dx < 150 and -150 <= dy < 150
            else:
                assert radius < 300
            groups[key].append((float(row["center_easting_m"]) - 953850 + dx,
                                float(row["center_northing_m"]) - 1951950 + dy,
                                row["sample_seed"], radius))
    assert len(identities) == manifest["generatedCount"] == manifest["requestedCount"] == 15000

    font = Path("/System/Library/Fonts/AppleSDGothicNeo.ttc")
    if not font.is_file():
        raise RuntimeError("한국어 PNG 재현에는 AppleSDGothicNeo.ttc가 필요해요.")
    font_manager.fontManager.addfont(str(font))
    plt.rcParams.update({"font.family": font_manager.FontProperties(fname=str(font)).get_name(),
                         "font.size": 11, "axes.unicode_minus": False, "text.color": "#24272C",
                         "axes.labelcolor": "#24272C", "xtick.color": "#666A70", "ytick.color": "#666A70"})
    fig, axes = plt.subplots(2, 3, figsize=(13.8, 10.8), dpi=150)
    parameters = [("a-square-300", "A · 현재 사각형 모델"), ("d-s120-r300", "D · σ=120m · 미채택 후보"),
                  ("d-s100-r300", "D · σ=100m · 미채택 후보")]
    scenarios = [("tuning-single-n500", 500, 330, "단일 중심"),
                 ("tuning-grid-imbalanced-n4500", 4500, 650, "불균형 3×3 중심")]
    fig.suptitle("E001 실제 생성 좌표 · 현재 모델과 미채택 후보", x=0.06, y=0.97, ha="left", fontsize=20)
    fig.text(0.06, 0.925, "사후 진단용 | 결과: inconclusive / no-d | 합성 좌표이며 실제 사용자 위치가 아니에요.", fontsize=12)
    for index, (scenario, count, extent, label) in enumerate(scenarios):
        for column, (parameter, title) in enumerate(parameters):
            points = groups[parameter, scenario]
            assert len(points) == count
            assert len({point[2] for point in points}) == 5
            assert all(abs(point[0]) < extent and abs(point[1]) < extent for point in points)
            ax = axes[index, column]
            ax.scatter([point[0] for point in points], [point[1] for point in points], s=5,
                       color="#AD8500", alpha=0.70, linewidths=0)
            ax.set(xlim=(-extent, extent), ylim=(-extent, extent), aspect="equal",
                   xlabel="동서 위치 (m, 기준점 상대)", ylabel="남북 위치 (m, 기준점 상대)")
            ticks = [-300, -150, 0, 150, 300] if index == 0 else [-600, -300, 0, 300, 600]
            ax.set_xticks(ticks)
            ax.set_yticks(ticks)
            ax.set_title(f"{title}\n{label} · 요청/생성/표시 {count:,}/{count:,}/{count:,}", loc="left", fontsize=11, pad=12)
            for side in ("top", "right"):
                ax.spines[side].set_visible(False)
            for side in ("bottom", "left"):
                ax.spines[side].set_color("#B8BBC0")
    fig.subplots_adjust(left=0.06, right=0.985, top=0.86, bottom=0.15, wspace=0.28, hspace=0.43)
    fig.text(0.06, 0.037, "각 행 안에서 축척·점 크기·투명도를 맞췄어요. 행 사이 축 범위는 달라요. 다섯 seed 전체 표시, 다운샘플링 없음.\n"
             f"출처: coordinates.csv.gz · runner {manifest['runnerSha'][:7]} · E·holdout·5,000개 조건은 미실행", fontsize=10)
    args.output.mkdir(parents=True, exist_ok=True)
    fig.savefig(args.output / "e001-diagnostic-scatter.png", facecolor="white")
    plt.close(fig)

    with (args.output / "diagnostic-edge-counts.csv").open("x", newline="") as stream:
        writer = csv.writer(stream, lineterminator="\n")
        writer.writerow(["parameter_set_id", "sample_seed", "n", "outer_count", "inner_count", "edge_ratio30"])
        for parameter, _ in parameters[1:]:
            points = groups[parameter, "tuning-single-n500"]
            for seed in sorted({point[2] for point in points}):
                radii = [point[3] for point in points if point[2] == seed]
                outer = sum(0 <= 300 - radius < 30 for radius in radii)
                inner = sum(30 <= 300 - radius < 60 for radius in radii)
                ratio = ((outer + 0.5) / (math.pi * 17100)) / ((inner + 0.5) / (math.pi * 15300))
                writer.writerow([parameter, seed, len(radii), outer, inner, ratio])
    print(f"Verified and plotted {len(identities):,} original points; no samples generated.")


if __name__ == "__main__":
    main()
