package com.pheeeew.core.network

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsText
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.Executors
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AndroidApiEngineTest {
    @Test
    fun cancellingChunkedResponseOnUiThreadAllowsNextRequest() = runBlocking {
        ServerSocket(0, 2, InetAddress.getLoopbackAddress()).use { server ->
            val releaseResponse = CompletableDeferred<Unit>()
            val responseStarted = CompletableDeferred<Unit>()
            val completionFailures = mutableListOf<Throwable>()
            val serverJob = launch(Dispatchers.IO) {
                server.accept().use { socket ->
                    val reader = socket.getInputStream().bufferedReader()
                    while (!reader.readLine().isNullOrEmpty()) { }
                    socket.getOutputStream().apply {
                        write("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\nConnection: close\r\n\r\n5\r\nhello\r\n".toByteArray())
                        flush()
                    }
                    // Keep the response incomplete while the UI cancels the request.
                    releaseResponse.await()
                }
                server.accept().use { socket ->
                    val reader = socket.getInputStream().bufferedReader()
                    while (!reader.readLine().isNullOrEmpty()) { }
                    socket.getOutputStream().apply {
                        write("HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nok".toByteArray())
                        flush()
                    }
                }
            }
            val client = HttpClient(createAndroidApiEngine())
            val url = "http://localhost:${server.localPort}/"
            Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { uiDispatcher ->
                try {
                    withTimeout(5_000) {
                        withContext(uiDispatcher) {
                            val request = launch(CoroutineExceptionHandler { _, error -> completionFailures.add(error) }) {
                                client.prepareGet(url).execute { response ->
                                    responseStarted.complete(Unit)
                                    response.bodyAsText()
                                }
                            }
                            responseStarted.await()
                            request.cancelAndJoin()
                            assertTrue(request.isCancelled)
                            assertTrue(completionFailures.isEmpty(), completionFailures.toString())
                            releaseResponse.complete(Unit)
                            assertEquals("ok", client.get(url).bodyAsText())
                        }
                        serverJob.join()
                    }
                } finally {
                    releaseResponse.complete(Unit)
                    client.close()
                    server.close()
                    serverJob.cancelAndJoin()
                }
            }
        }
    }
}
