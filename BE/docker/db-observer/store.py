"""단일 수집기 전용 시간별 JSONL 보관. 실패는 호출자에게 전파한다."""

from datetime import datetime, timedelta, timezone
import fcntl
import json
import os
from pathlib import Path
import re
import stat

MAX_BYTES = 32 * 1024 * 1024
MAX_RECORD = 64 * 1024
BUCKET = "sample-%Y%m%d%H.jsonl"


class Store:
    def __init__(self, directory: Path, clock=lambda: datetime.now(timezone.utc)):
        self.directory, self.clock, self.lock = directory, clock, None

    def __enter__(self):
        self.directory.mkdir(mode=0o750, parents=True, exist_ok=True)
        self.lock = os.open(self.directory / ".lock", os.O_CREAT | os.O_RDWR | os.O_NOFOLLOW, 0o600)
        try:
            fcntl.flock(self.lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
            os.chmod(self.directory, 0o750)
            self.trim(0)
            for path, _, _ in self.files():
                self.repair_tail(path)
            return self
        except BaseException:
            self.__exit__(None, None, None)
            raise

    def __exit__(self, *_):
        if self.lock is not None:
            os.close(self.lock)
            self.lock = None  # 잠금 파일은 지우지 않고 같은 inode를 사용한다.

    def files(self) -> list:
        files = []
        for path in sorted(self.directory.iterdir()):
            if re.fullmatch(r"sample-\d{10}\.jsonl", path.name):
                timestamp = datetime.strptime(path.name, BUCKET).replace(tzinfo=timezone.utc)
                info = path.lstat()
                if not stat.S_ISREG(info.st_mode):
                    raise OSError("invalid bucket file")
                files.append((path, timestamp, info.st_size))
        return files

    def trim(self, reserve: int) -> None:
        if reserve > MAX_BYTES:
            raise OSError("record exceeds storage limit")
        files = self.files()
        total = sum(size for _, _, size in files)
        cutoff = self.clock().astimezone(timezone.utc) - timedelta(hours=24)
        for path, timestamp, size in files:
            if timestamp <= cutoff or total + reserve > MAX_BYTES:
                path.unlink()
                total -= size

    def repair_tail(self, path: Path) -> None:
        fd = os.open(path, os.O_RDWR | os.O_NOFOLLOW)
        try:
            os.fchmod(fd, 0o640)  # 기존 0600 표본도 잠금을 얻은 뒤 그룹 읽기로 이행한다.
            size = os.lseek(fd, 0, os.SEEK_END)
            start = max(0, size - MAX_RECORD)
            os.lseek(fd, start, os.SEEK_SET)
            tail = b""
            while len(tail) < size - start:
                chunk = os.read(fd, size - start - len(tail))
                if not chunk:
                    raise OSError("incomplete bucket read")
                tail += chunk
            if tail and not tail.endswith(b"\n"):
                newline = tail.rfind(b"\n")
                if newline < 0 and start > 0:
                    raise OSError("bucket tail exceeds repair limit")
                os.ftruncate(fd, start + newline + 1)
        finally:
            os.close(fd)

    def append(self, event: dict) -> None:
        if self.lock is None:
            raise OSError("store is not locked")
        data = (json.dumps(event, ensure_ascii=False, allow_nan=False, separators=(",", ":")) + "\n").encode()
        if len(data) > MAX_RECORD:
            raise OSError("record exceeds output limit")
        self.trim(len(data))
        path = self.directory / self.clock().astimezone(timezone.utc).strftime(BUCKET)
        # 정리로 현재 버킷이 삭제됐어도 새 파일을 열어 삭제된 inode에 쓰지 않는다.
        fd = os.open(path, os.O_CREAT | os.O_WRONLY | os.O_APPEND | os.O_NOFOLLOW, 0o640)
        try:
            os.fchmod(fd, 0o640)  # 프로세스 umask 077은 비밀번호 경계로 유지한다.
            remaining = memoryview(data)
            while remaining:
                written = os.write(fd, remaining)
                if written == 0:
                    raise OSError("zero-length write")
                remaining = remaining[written:]
        finally:
            os.close(fd)
