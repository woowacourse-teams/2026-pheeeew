#!/usr/bin/env python3
"""Loopback-only CONNECT tunnel for a simulator debug-session experiment.

SIGUSR1 drops existing tunnels and refuses new ones; SIGUSR2 restores forwarding.
No HTTP headers or tunnel bytes are logged. No TLS interception is performed.
"""
import asyncio
import signal

ALLOWED_HOST = 'api-dev.pheeeew.com'
ALLOWED_PORT = 443
LISTEN_HOST = '127.0.0.1'
LISTEN_PORT = 18771
HEADER_LIMIT = 8192

class Proxy:
    def __init__(self):
        self.online = True
        self.active = set()

    def offline(self):
        self.online = False
        for writer in tuple(self.active):
            writer.close()
        print('state=offline tunnels=closed', flush=True)

    def restore(self):
        self.online = True
        print('state=online', flush=True)

    async def handle(self, reader, writer):
        upstream = None
        self.active.add(writer)
        try:
            header = await asyncio.wait_for(reader.readuntil(b'\r\n\r\n'), 5)
            if len(header) > HEADER_LIMIT:
                return
            lines = header.split(b'\r\n')
            expected = f'CONNECT {ALLOWED_HOST}:{ALLOWED_PORT} HTTP/1.'.encode()
            first = lines[0]
            if not (first.startswith(expected) and first[len(expected):] in (b'0', b'1')):
                writer.write(b'HTTP/1.1 403 Forbidden\r\nConnection: close\r\n\r\n')
                await writer.drain()
                return
            if not self.online:
                return
            remote_reader, upstream = await asyncio.wait_for(asyncio.open_connection(ALLOWED_HOST, ALLOWED_PORT), 10)
            self.active.add(upstream)
            if not self.online:
                return
            writer.write(b'HTTP/1.1 200 Connection Established\r\n\r\n')
            await writer.drain()
            print('tunnel=opened', flush=True)
            async def copy(source, dest):
                while data := await source.read(65536):
                    dest.write(data)
                    await dest.drain()
            tasks = [asyncio.create_task(copy(reader, upstream)), asyncio.create_task(copy(remote_reader, writer))]
            try:
                await asyncio.wait(tasks, return_when=asyncio.FIRST_COMPLETED)
            finally:
                for task in tasks:
                    task.cancel()
                await asyncio.gather(*tasks, return_exceptions=True)
        except (OSError, asyncio.TimeoutError, asyncio.IncompleteReadError, asyncio.LimitOverrunError):
            pass
        finally:
            for stream in (upstream, writer):
                if stream:
                    self.active.discard(stream)
                    stream.close()
                    try:
                        await stream.wait_closed()
                    except OSError:
                        pass

async def main():
    proxy = Proxy()
    loop = asyncio.get_running_loop()
    loop.add_signal_handler(signal.SIGUSR1, proxy.offline)
    loop.add_signal_handler(signal.SIGUSR2, proxy.restore)
    stop = asyncio.Event()
    for sig in (signal.SIGINT, signal.SIGTERM):
        loop.add_signal_handler(sig, stop.set)
    server = await asyncio.start_server(proxy.handle, LISTEN_HOST, LISTEN_PORT, limit=HEADER_LIMIT)
    print(f'listening={LISTEN_HOST}:{LISTEN_PORT} state=online', flush=True)
    try:
        async with server:
            await stop.wait()
    finally:
        proxy.offline()

if __name__ == '__main__':
    asyncio.run(main())
