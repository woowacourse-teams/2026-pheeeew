"""신호로 종료하는 독립 수집기. 접속 정보와 원본 예외는 출력하지 않는다."""

import json
import os
from pathlib import Path
import signal
import stat
import sys
from threading import Event

from collect import event, run
from store import Store


def main() -> int:
    os.umask(0o077)
    stop = Event()
    for name in (signal.SIGTERM, signal.SIGINT):
        signal.signal(name, lambda *_: stop.set())
    if not all(os.environ.get(key) for key in ("PGHOST", "PGDATABASE", "PGUSER", "PGPASSFILE")):
        print(json.dumps(event("configuration_missing")), file=sys.stderr)
        return 1
    try:
        password = Path(os.environ["PGPASSFILE"])
        info = password.lstat()
        if not (stat.S_ISREG(info.st_mode) and info.st_uid == os.getuid()
                and stat.S_IMODE(info.st_mode) == 0o600):
            raise ValueError("invalid credential file")
        os.environ.pop("PGPASSWORD", None)
        with Store(Path("/data")) as output:
            run(output.append, stop)
        return 0
    except Exception:
        print(json.dumps(event("collector_stopped")), file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
