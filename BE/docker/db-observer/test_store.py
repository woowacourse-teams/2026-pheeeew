"""격리된 임시 디렉터리에서 잠금·보관 상한·부분 쓰기 복구를 검증한다."""

from datetime import datetime, timedelta, timezone
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
from threading import Event
import unittest
from unittest.mock import patch

import collect
import store


class StoreTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.directory = Path(self.temp.name)
        self.now = datetime(2026, 10, 11, 6, 30, tzinfo=timezone.utc)

    def open(self):
        return store.Store(self.directory, lambda: self.now)

    def bucket(self, age=0):
        return self.directory / (self.now - timedelta(hours=age)).strftime(store.BUCKET)

    def test_ttl_is_checked_on_start_and_append_without_deleting_unowned_files(self):
        self.bucket(24).write_text('{}\n')
        self.bucket(23).write_text('{}\n')
        unrelated = self.directory / 'notes.txt'
        unrelated.write_text('preserve')
        with self.open() as target:
            self.assertFalse(self.bucket(24).exists())
            self.assertTrue(self.bucket(23).exists())
            target.append({'message': '한글'})
            self.assertEqual(json.loads(self.bucket().read_text()), {'message': '한글'})
            self.assertEqual(self.bucket().stat().st_mode & 0o777, 0o600)
            self.now += timedelta(hours=1)
            target.append({'next': True})
            self.assertFalse(self.bucket(24).exists())
        self.assertEqual(unrelated.read_text(), 'preserve')

    def test_capacity_evicts_oldest_and_reopens_evicted_current_bucket(self):
        with patch.object(store, 'MAX_BYTES', 12), self.open() as target:
            target.append({'a': 1})
            self.now += timedelta(hours=1)
            target.append({'b': 2})
            self.assertFalse(self.bucket(1).exists())
            target.append({'c': 3})
            self.assertEqual(json.loads(self.bucket().read_text()), {'c': 3})
            self.assertLessEqual(sum(p.stat().st_size for p in self.directory.glob('sample-*')), 12)
            with self.assertRaises(OSError):
                target.append({'too_large': 'x' * store.MAX_RECORD})
            self.assertEqual(json.loads(self.bucket().read_text()), {'c': 3})

    def test_cross_process_lock_is_released_but_lock_inode_is_preserved(self):
        code = "from pathlib import Path; from store import Store; import sys\nwith Store(Path(sys.argv[1])): pass"
        def second():
            return subprocess.run([sys.executable, '-c', code, str(self.directory)],
                                  cwd=Path(store.__file__).parent, capture_output=True, timeout=5).returncode
        with self.open():
            inode = (self.directory / '.lock').stat().st_ino
            self.assertNotEqual(second(), 0)
        self.assertEqual(second(), 0)
        self.assertEqual((self.directory / '.lock').stat().st_ino, inode)

    def test_partial_write_stops_collection_and_restart_preserves_complete_records(self):
        actual_write = os.write
        with self.open() as target:
            target.append({'before': True})
            def partial(fd, data):
                actual_write(fd, data[:3])
                raise OSError('simulated disk full')
            with patch.object(store.os, 'write', side_effect=partial), \
                    patch.object(collect, 'collect_once', return_value={'broken': True}) as once:
                with self.assertRaises(OSError):
                    collect.run(target.append, Event())
                once.assert_called_once()
        actual_read = os.read
        with patch.object(store.os, 'read', side_effect=lambda fd, size: actual_read(fd, min(size, 2))):
            with self.open() as target:
                target.append({'after': True})
        self.assertEqual([json.loads(line) for line in self.bucket().read_text().splitlines()],
                         [{'before': True}, {'after': True}])

    def test_short_and_zero_writes_and_invalid_bucket_fail_safely(self):
        actual_write = os.write
        with self.open() as target:
            with patch.object(store.os, 'write', side_effect=lambda fd, data: actual_write(fd, data[:2])):
                target.append({'short': True})
            self.assertEqual(json.loads(self.bucket().read_text()), {'short': True})
            with patch.object(store.os, 'write', return_value=0), self.assertRaises(OSError):
                target.append({'zero': True})
        self.bucket().unlink()
        outside = self.directory / 'private.txt'
        outside.write_text('preserve')
        self.bucket().symlink_to(outside)
        with self.assertRaises(OSError), self.open():
            pass
        self.assertEqual(outside.read_text(), 'preserve')


if __name__ == '__main__':
    unittest.main()
