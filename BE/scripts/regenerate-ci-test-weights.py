#!/usr/bin/env python3
"""BE CI 샤드 리포트에서 클래스별 시간을 뽑아 ci-test-weights.properties 를 다시 쓴다.

사용:
  python3 BE/scripts/regenerate-ci-test-weights.py --run <BE CI 런 ID> [--run <ID> ...]
  python3 BE/scripts/regenerate-ci-test-weights.py --dir <내려받은 리포트 폴더> [--dir ...]

--run 은 gh CLI 로 test-results-shard-* 아티팩트(보관 7일)를 받는다. 같은 런의 재시도는
마지막 시도만 받으므로, 여러 시도를 평균하려면 --dir 로 시도별 폴더를 넘긴다.
여러 런을 넘기면 클래스마다 평균한다. 런마다 러너 속도가 달라서 두 런 이상을 권한다.

샤드마다 처음 Spring 컨텍스트를 띄우는 클래스가 차가운 JVM 비용(15~22초)을 떠안는다. 이 비용은 어느
클래스가 첫 번째가 되든 샤드마다 한 번 내는 고정비라서 가중치에서 뺀다. 리포트 표준 출력의
"Started <클래스> in N seconds" 중 샤드 최댓값과 나머지 중앙값의 차이를 그 클래스 시간에서 뺀다.
"""
import argparse
import re
import statistics
import subprocess
import sys
import tempfile
from collections import defaultdict
from pathlib import Path

ROOT_REPORT = Path("build/reports/tests/test")
TITLE = re.compile(r"<title>Test results - (com\.pheeeew\.[\w.]+Test)</title>")
COUNTER = re.compile(r'<div class="counter">([^<]+)</div>')
STARTED = re.compile(r"Started \w+ in ([\d.]+) seconds")
HEADER = """\
# 테스트 샤드 균형용 가중치(밀리초). BE/scripts/regenerate-ci-test-weights.py 가 CI 샤드 리포트에서 뽑았다.
# 원본: {sources}
# 여기 없는 클래스는 기본 가중치 1000ms 로 계산한다. 값이 낡으면 균형만 나빠지고 결과는 바뀌지 않는다.
# 로컬 시간은 쓰지 않는다. 클래스마다 CI/로컬 비율이 달라 로컬 값으로는 샤드 간 차이가 16~22초 났다.
# 샤드마다 처음 Spring 컨텍스트를 띄우는 클래스가 차가운 JVM 비용(15~22초)을 떠안는다. 샤드마다 한 번씩
# 어차피 내는 비용이므로 그 클래스 시간에서 "샤드 최대 기동 시간 - 나머지 기동 시간 중앙값" 을 뺐다.
# PR CI 는 대상 브랜치와 합친 머지 커밋을 돌리므로, 다시 뽑을 때도 CI 리포트(test-results-shard-*)를 쓴다.
"""


def seconds(text):
    match = re.fullmatch(r"(?:(\d+)h)?(?:(\d+)m)?([\d.]+)s", text.strip())
    if not match:
        raise ValueError(f"시간 형식을 읽지 못했다: {text}")
    hours, minutes, secs = match.groups()
    return int(hours or 0) * 3600 + int(minutes or 0) * 60 + float(secs)


def read_shard(shard_dir):
    """샤드 하나의 {클래스: 초} 와 {클래스: 가장 긴 컨텍스트 기동 초} 를 돌려준다."""
    report = shard_dir / ROOT_REPORT
    times, starts = {}, {}
    for page in report.rglob("index.html"):
        html = page.read_text(errors="ignore")
        title = TITLE.search(html)
        if not title:
            continue
        name = title.group(1)
        times[name] = seconds(COUNTER.findall(html)[3])
        boots = [float(s) for sub in page.parent.rglob("index.html")
                 for s in STARTED.findall(sub.read_text(errors="ignore"))]
        if boots:
            starts[name] = max(boots)
    return times, starts


def shard_dirs(run_dir):
    dirs = sorted(p for p in run_dir.iterdir() if (p / ROOT_REPORT).is_dir())
    if not dirs:
        sys.exit(f"샤드 리포트가 없다: {run_dir} (아래에 <샤드>/{ROOT_REPORT} 가 있어야 한다)")
    return dirs


def download(run_id, into):
    names = subprocess.run(
        ["gh", "run", "view", run_id, "--json", "jobs", "--jq", ".jobs[].name"],
        check=True, capture_output=True, text=True).stdout.split("\n")
    shards = sorted({m.group(1) for n in names if (m := re.search(r"샤드 (\d+)", n))})
    if not shards:
        sys.exit(f"런 {run_id} 에 테스트 샤드 잡이 없다")
    for shard in shards:
        # 아티팩트를 한 번에 받으면 같은 경로가 겹쳐 실패하므로 하나씩 받는다.
        subprocess.run(["gh", "run", "download", run_id, "-n", f"test-results-shard-{shard}",
                        "-D", str(into / f"shard-{shard}")], check=True)
    return into


def collect(run_dirs):
    samples = defaultdict(list)
    for run_dir in run_dirs:
        for shard in shard_dirs(run_dir):
            times, starts = read_shard(shard)
            if len(starts) > 1:
                cold = max(starts, key=starts.get)
                warm = statistics.median(v for k, v in starts.items() if k != cold)
                times[cold] = max(times[cold] - (starts[cold] - warm), 0.0)
            for name, value in times.items():
                samples[name].append(value)
    return {name: round(statistics.mean(values) * 1000) for name, values in samples.items()}


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--run", action="append", default=[], help="BE CI 런 ID (여러 번 가능)")
    parser.add_argument("--dir", action="append", default=[], type=Path,
                        help="샤드별 하위 폴더를 가진 리포트 폴더 (여러 번 가능)")
    parser.add_argument("--out", type=Path, default=Path(__file__).resolve().parents[1] / "ci-test-weights.properties")
    args = parser.parse_args()
    if not args.run and not args.dir:
        parser.error("--run 이나 --dir 을 하나 이상 넘긴다")

    with tempfile.TemporaryDirectory() as tmp:
        run_dirs = list(args.dir) + [download(r, Path(tmp) / r) for r in args.run]
        weights = collect(run_dirs)

    # 로컬 경로는 커밋에 남기지 않는다.
    sources = ", ".join([f"런 {r}" for r in args.run] + [f"로컬 리포트 폴더 {d.name}" for d in args.dir])
    lines = [HEADER.format(sources=sources)]
    lines += [f"{name}={ms}" for name, ms in sorted(weights.items(), key=lambda kv: (-kv[1], kv[0]))]
    args.out.write_text("\n".join(lines) + "\n")
    print(f"{args.out}: 클래스 {len(weights)}개")


if __name__ == "__main__":
    main()
