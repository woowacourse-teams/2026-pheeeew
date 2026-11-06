#!/usr/bin/env python3
"""Local echo-server exercise; never contacts development service."""
import asyncio
import importlib.util
from pathlib import Path

spec = importlib.util.spec_from_file_location('proxy', Path(__file__).with_name('dev_connect_proxy.py'))
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)

async def main():
    async def echo(reader, writer):
        try:
            while data := await reader.read(1024):
                writer.write(data)
                await writer.drain()
        finally:
            writer.close()
            await writer.wait_closed()
    upstream = await asyncio.start_server(echo, '127.0.0.1', 0)
    m.ALLOWED_HOST = '127.0.0.1'
    m.ALLOWED_PORT = upstream.sockets[0].getsockname()[1]
    proxy = m.Proxy()
    server = await asyncio.start_server(proxy.handle, '127.0.0.1', 0)
    port = server.sockets[0].getsockname()[1]
    async def connect(method, target):
        reader, writer = await asyncio.open_connection('127.0.0.1', port)
        writer.write(f'{method} {target} HTTP/1.1\r\n\r\n'.encode())
        await writer.drain()
        return reader, writer
    async def close(writer):
        writer.close()
        await writer.wait_closed()
    for method, target in [('CONNECT', 'wrong.example:443'), ('GET', f'{m.ALLOWED_HOST}:{m.ALLOWED_PORT}')]:
        reader, writer = await connect(method, target)
        assert b'403 Forbidden' in await reader.read()
        await close(writer)
    target = f'{m.ALLOWED_HOST}:{m.ALLOWED_PORT}'
    reader, writer = await connect('CONNECT', target)
    assert b'200 Connection Established' in await reader.readuntil(b'\r\n\r\n')
    writer.write(b'test')
    await writer.drain()
    assert await reader.readexactly(4) == b'test'
    proxy.offline()
    assert await asyncio.wait_for(reader.read(), 2) == b''
    await close(writer)
    reader, writer = await connect('CONNECT', target)
    assert await asyncio.wait_for(reader.read(), 2) == b''
    await close(writer)
    proxy.restore()
    reader, writer = await connect('CONNECT', target)
    assert b'200 Connection Established' in await reader.readuntil(b'\r\n\r\n')
    await close(writer)
    proxy.offline()
    server.close()
    upstream.close()
    await server.wait_closed()
    await upstream.wait_closed()
    print('allowlist=pass non_connect=pass relay=pass active_disconnect=pass offline_refusal=pass restore=pass')

asyncio.run(main())
