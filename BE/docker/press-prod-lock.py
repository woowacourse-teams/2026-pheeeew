#!/usr/bin/env python3
"""Persistent lock shared by production CD and the first press migration."""

import argparse
from contextlib import contextmanager
import fcntl
import os
from pathlib import Path
import re
import stat
import sys

ROOT = Path('/opt/pheeeew')
LOCK = '.press-operations.lockdir'
MUTEX = '.press-operations.mutex'


def sync_dir(path):
    fd = os.open(path, os.O_RDONLY | os.O_DIRECTORY)
    try:
        os.fsync(fd)
    finally:
        os.close(fd)


@contextmanager
def mutex():
    fd = os.open(ROOT / MUTEX, os.O_CREAT | os.O_RDWR | os.O_NOFOLLOW, 0o600)
    try:
        if not stat.S_ISREG(os.fstat(fd).st_mode):
            raise ValueError('Invalid lock mutex')
        fcntl.flock(fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
        yield
    finally:
        os.close(fd)


def valid_token(token):
    if not re.fullmatch(r'[A-Za-z0-9-]{1,128}', token):
        raise ValueError('Invalid operation token')


def acquire(token):
    valid_token(token)
    with mutex():
        directory = ROOT / LOCK
        directory.mkdir(mode=0o700)
        fd = os.open(directory / 'owner', os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
        try:
            owner_data = (token + '\n').encode('ascii')
            if os.write(fd, owner_data) != len(owner_data):
                raise OSError('Incomplete lock owner write')
            os.fsync(fd)
        finally:
            os.close(fd)
        sync_dir(directory)
        sync_dir(ROOT)


def check_owner(token):
    valid_token(token)
    directory = ROOT / LOCK
    if directory.is_symlink() or not directory.is_dir() or set(os.listdir(directory)) != {'owner'}:
        raise ValueError('Unexpected lock contents; preserve for inspection')
    owner = directory / 'owner'
    fd = os.open(owner, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
    try:
        if not stat.S_ISREG(os.fstat(fd).st_mode):
            raise ValueError('Invalid lock owner')
        owner_data = os.read(fd, 130)
    finally:
        os.close(fd)
    if owner_data != (token + '\n').encode('ascii'):
        raise ValueError('Lock is owned by another operation')


def release(token):
    with mutex():
        check_owner(token)
        directory = ROOT / LOCK
        owner = directory / 'owner'
        owner.unlink()
        directory.rmdir()
        sync_dir(ROOT)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=('acquire', 'release', 'check'))
    parser.add_argument('token')
    args = parser.parse_args()
    try:
        if args.action == 'check':
            with mutex():
                check_owner(args.token)
        else:
            (acquire if args.action == 'acquire' else release)(args.token)
        print('Operation lock ' + {'acquire': 'acquired', 'release': 'released', 'check': 'confirmed'}[args.action])
        return 0
    except (OSError, ValueError, UnicodeError):
        print('Operation lock unavailable or uncertain; preserve it for inspection.', file=sys.stderr)
        return 1


if __name__ == '__main__':
    sys.exit(main())
