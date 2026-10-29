#!/usr/bin/env python3
"""운영 배포와 최초 이행의 영속 잠금 계약을 검증한다."""

import importlib.util
import multiprocessing
import sys
from pathlib import Path
import tempfile
import unittest
from unittest import mock

sys.dont_write_bytecode = True
spec = importlib.util.spec_from_file_location('prod_lock', Path(__file__).with_name('press-prod-lock.py'))
lock = importlib.util.module_from_spec(spec)
spec.loader.exec_module(lock)


def contender(root, token, barrier, results):
    lock.ROOT = Path(root)
    barrier.wait()
    try:
        lock.acquire(token)
        results.put('acquired')
    except (OSError, ValueError):
        results.put('refused')


class ProductionLockTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        lock.ROOT = Path(self.tmp.name)

    def test_only_current_owner_can_release_and_next_owner_can_acquire(self):
        lock.acquire('run-1')
        with self.assertRaises((OSError, ValueError)):
            lock.acquire('run-2')
        with self.assertRaises((OSError, ValueError)):
            lock.release('run-2')
        lock.release('run-1')
        lock.acquire('run-2')
        lock.release('run-2')

    def test_check_action_accepts_only_current_owner(self):
        lock.acquire('run-1')
        with mock.patch.object(sys, 'argv', ['press-prod-lock.py', 'check', 'run-1']):
            self.assertEqual(lock.main(), 0)
        with mock.patch.object(sys, 'argv', ['press-prod-lock.py', 'check', 'run-2']):
            self.assertEqual(lock.main(), 1)
        lock.check_owner('run-1')

    def test_empty_or_unexpected_lock_contents_are_not_removed(self):
        path = lock.ROOT / '.press-operations.lockdir'
        path.mkdir()
        with self.assertRaises((OSError, ValueError)):
            lock.release('run-1')
        self.assertTrue(path.exists())
        path.rmdir()
        lock.acquire('run-1')
        (path / 'unexpected').write_text('preserve')
        with self.assertRaises((OSError, ValueError)):
            lock.release('run-1')
        self.assertTrue((path / 'unexpected').exists())

    def test_concurrent_acquisition_has_one_owner(self):
        ctx = multiprocessing.get_context('fork')
        barrier, results = ctx.Barrier(2), ctx.Queue()
        children = [ctx.Process(target=contender, args=(self.tmp.name, f'run-{i}', barrier, results))
                    for i in range(2)]
        for child in children:
            child.start()
        for child in children:
            child.join(10)
            self.assertEqual(child.exitcode, 0)
        self.assertCountEqual([results.get(timeout=2), results.get(timeout=2)], ['acquired', 'refused'])

    def test_partial_owner_write_fails_closed(self):
        with mock.patch.object(lock.os, 'write', return_value=1):
            with self.assertRaises(OSError):
                lock.acquire('run-1')
        self.assertTrue((lock.ROOT / lock.LOCK).exists())
        with self.assertRaises((OSError, ValueError)):
            lock.acquire('run-2')

    def test_non_regular_owner_is_never_read_or_removed(self):
        directory = lock.ROOT / lock.LOCK
        directory.mkdir()
        lock.os.mkfifo(directory / 'owner')
        with self.assertRaises(ValueError):
            lock.release('run-1')
        self.assertTrue((directory / 'owner').exists())

    def test_fsync_failure_does_not_report_acquired_or_free_lock(self):
        with mock.patch.object(lock.os, 'fsync', side_effect=OSError('sync failed')):
            with self.assertRaises(OSError):
                lock.acquire('run-1')
        self.assertTrue((lock.ROOT / lock.LOCK).exists())
        with self.assertRaises((OSError, ValueError)):
            lock.acquire('run-2')

    def test_late_release_cannot_remove_new_owners_lock(self):
        lock.acquire('old-run')
        lock.release('old-run')
        lock.acquire('new-run')
        with self.assertRaises(ValueError):
            lock.release('old-run')
        lock.check_owner('new-run')

    def test_failed_directory_removal_keeps_lock_unavailable(self):
        lock.acquire('run-1')
        with mock.patch.object(Path, 'rmdir', side_effect=OSError('remove failed')):
            with self.assertRaises(OSError):
                lock.release('run-1')
        self.assertTrue((lock.ROOT / lock.LOCK).is_dir())
        with self.assertRaises((OSError, ValueError)):
            lock.acquire('run-2')


if __name__ == '__main__':
    unittest.main()
