#!/usr/bin/env bash
set -euo pipefail
# Usage: bash prepare-import.sh <SHP/DBF/SHX/PRJ/CPG directory> <new SQL file>
# Prerequisites in the preparation environment: shp2pgsql and Python 3 with pyproj.
# Execute the result only against an approved target:
# psql -X --single-transaction -v ON_ERROR_STOP=1 -f <SQL file>
[[ $# == 2 ]] || { echo "사용법: $0 원본_디렉터리 새_SQL_파일" >&2; exit 1; }
command -v shp2pgsql >/dev/null || { echo "준비 환경에 shp2pgsql이 필요합니다." >&2; exit 1; }
command -v python3 >/dev/null || { echo "준비 환경에 Python 3가 필요합니다." >&2; exit 1; }
python3 -c 'import pyproj'
source_dir=$(cd "$1" && pwd)
tool_dir=$(cd "$(dirname "$0")" && pwd)
[[ ! -e "$2" ]] || { echo "기존 출력 파일은 덮어쓰지 않습니다." >&2; exit 1; }
for level in sido sigungu dong; do
    for extension in shp dbf shx prj cpg; do
        [[ -r "$source_dir/bnd_${level}_00_2025_2Q.$extension" ]] || {
            echo "원본 파일 누락: $level.$extension" >&2; exit 1;
        }
    done
done
temporary_sql=$(mktemp "$(dirname "$2")/.sgis-import.XXXXXX")
trap 'rm -f "$temporary_sql"' EXIT
printf 'SET client_encoding = '\''UTF8'\'';\nCREATE SCHEMA sgis_import;\n' > "$temporary_sql"
for level in sido sigungu dong; do
    base="$source_dir/bnd_${level}_00_2025_2Q"
    python3 -c '
import codecs, sys
from pathlib import Path
from pyproj import CRS
base = sys.argv[1]
if CRS.from_wkt(Path(base + ".prj").read_text(encoding="utf-8")).to_epsg() != 5179:
    raise SystemExit("원본 좌표계는 EPSG:5179여야 합니다.")
if codecs.lookup(Path(base + ".cpg").read_text().strip()).name != "utf-8":
    raise SystemExit("원본 문자 인코딩은 UTF-8이어야 합니다.")
' "$base"
    # -e omits loader BEGIN/COMMIT; the caller owns the single transaction.
    shp2pgsql -e -s 5179 -W UTF-8 -g geom "$base.shp" "sgis_import.$level" >> "$temporary_sql"
done
cat "$tool_dir/import.sql" >> "$temporary_sql"
# Publish the complete file exclusively, including when another run won the race.
ln "$temporary_sql" "$2"
echo "준비 완료: $2 (DB 적재는 실행하지 않았습니다.)"
