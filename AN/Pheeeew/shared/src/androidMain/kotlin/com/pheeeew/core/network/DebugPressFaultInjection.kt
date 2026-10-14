package com.pheeeew.core.network

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.File
import java.net.SocketTimeoutException

/** One-shot device-local faults. Configured only by the dev debug composition root. */
internal object DebugPressFaultInjection : Interceptor {
    private var control: File? = null
    val enabled: Boolean get() = control != null

    fun configure(directory: File?) {
        control = directory
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.host != "api-dev.pheeeew.com" || request.url.encodedPath != "/api/v2/emotions/presses" ||
            request.method != "POST"
        ) {
            return chain.proceed(request)
        }
        val mode =
            synchronized(this) {
                val directory = control
                listOf("400", "429", "500", "timeout", "lost_response").firstOrNull { mode ->
                    val file = directory?.let { File(it, "press-probe-fault-$mode") }
                    file?.exists() == true && file.delete()
                }
            }
        if (mode == null) return chain.proceed(request)
        Log.i("PressMeasurement", "fault mode=$mode")
        return when (mode) {
            "400", "429", "500" -> {
                Response
                    .Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(mode.toInt())
                    .message("Injected press probe")
                    .header("Content-Type", "application/json")
                    .header("Retry-After", "2")
                    .body("{}".toResponseBody())
                    .build()
            }

            "timeout" -> {
                Thread.sleep(3_000)
                throw SocketTimeoutException("Injected before upstream write")
            }

            "lost_response" -> {
                val response = chain.proceed(request)
                val status = response.code
                response.close()
                Log.i("PressMeasurement", "fault_upstream status=$status")
                throw SocketTimeoutException("Injected after upstream response")
            }

            else -> {
                chain.proceed(request)
            }
        }
    }
}
