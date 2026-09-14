package com.pheeeew.data.local.device

import com.pheeeew.domain.model.device.DevicePlatform
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.DeviceCheck.DCAppAttestService
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.create
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosAppAttestProvider(
    private val keychain: IosKeychainDeviceTokenStorage,
) : DeviceAttestationProvider {
    override suspend fun create(challenge: String): DeviceAttestation {
        val service = DCAppAttestService.sharedService
        check(service.isSupported()) { "App Attest is not supported on this device" }

        val keyId = keychain.readString(KEY_ID_ACCOUNT) ?: service.generateKey()
            .also { keychain.writeString(KEY_ID_ACCOUNT, it) }
        val attestation = service.attestKey(keyId, challenge.sha256Data())

        return DeviceAttestation(
            platform = DevicePlatform.IOS,
            token = attestation.bytes?.readBytes(attestation.length.toInt())?.base64Encode()
                ?: error("App Attest token was empty"),
            challenge = challenge,
            keyId = keyId,
        )
    }

    private suspend fun DCAppAttestService.generateKey(): String =
        suspendCancellableCoroutine { continuation ->
            generateKeyWithCompletionHandler { keyId, error ->
                when {
                    error != null -> continuation.resumeWithException(error.toException())
                    keyId != null -> continuation.resume(keyId)
                    else -> continuation.resumeWithException(IllegalStateException("App Attest key was not generated"))
                }
            }
        }

    private suspend fun DCAppAttestService.attestKey(
        keyId: String,
        clientDataHash: NSData,
    ): NSData = suspendCancellableCoroutine { continuation ->
        attestKey(keyId, clientDataHash = clientDataHash) { attestation, error ->
            when {
                error != null -> continuation.resumeWithException(error.toException())
                attestation != null -> continuation.resume(attestation)
                else -> continuation.resumeWithException(IllegalStateException("App Attest token was not created"))
            }
        }
    }

    private fun NSError.toException(): Exception =
        IllegalStateException("App Attest failed ($code): ${localizedDescription}")

    private companion object {
        const val KEY_ID_ACCOUNT = "app-attest-key-id"
    }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun String.sha256Data(): NSData {
    val digest = sha256(encodeToByteArray())
    return digest.usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = digest.size.toULong())
    }
}

private fun ByteArray.base64Encode(): String {
    val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    val result = StringBuilder(((size + 2) / 3) * 4)
    var index = 0
    while (index < size) {
        val first = this[index++].toInt() and 0xff
        val second = if (index < size) this[index++].toInt() and 0xff else null
        val third = if (index < size) this[index++].toInt() and 0xff else null
        result.append(alphabet[first ushr 2])
        result.append(alphabet[((first and 0x03) shl 4) or ((second ?: 0) ushr 4)])
        result.append(if (second == null) '=' else alphabet[((second and 0x0f) shl 2) or ((third ?: 0) ushr 6)])
        result.append(if (third == null) '=' else alphabet[third and 0x3f])
    }
    return result.toString()
}

private fun sha256(input: ByteArray): ByteArray {
    val h = intArrayOf(
        0x6a09e667, 0xbb67ae85.toInt(), 0x3c6ef372, 0xa54ff53a.toInt(),
        0x510e527f, 0x9b05688c.toInt(), 0x1f83d9ab, 0x5be0cd19,
    )
    val k = intArrayOf(
        0x428a2f98, 0x71374491, 0xb5c0fbcf.toInt(), 0xe9b5dba5.toInt(), 0x3956c25b,
        0x59f111f1, 0x923f82a4.toInt(), 0xab1c5ed5.toInt(), 0xd807aa98.toInt(), 0x12835b01,
        0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe.toInt(), 0x9bdc06a7.toInt(), 0xc19bf174.toInt(),
        0xe49b69c1.toInt(), 0xefbe4786.toInt(), 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f,
        0x4a7484aa, 0x5cb0a9dc, 0x76f988da, 0x983e5152.toInt(), 0xa831c66d.toInt(),
        0xb00327c8.toInt(), 0xbf597fc7.toInt(), 0xc6e00bf3.toInt(), 0xd5a79147.toInt(),
        0x06ca6351, 0x14292967, 0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13,
        0x650a7354, 0x766a0abb, 0x81c2c92e.toInt(), 0x92722c85.toInt(), 0xa2bfe8a1.toInt(),
        0xa81a664b.toInt(), 0xc24b8b70.toInt(), 0xc76c51a3.toInt(), 0xd192e819.toInt(),
        0xd6990624.toInt(), 0xf40e3585.toInt(), 0x106aa070, 0x19a4c116, 0x1e376c08,
        0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
        0x748f82ee, 0x78a5636f, 0x84c87814.toInt(), 0x8cc70208.toInt(), 0x90befffa.toInt(),
        0xa4506ceb.toInt(), 0xbef9a3f7.toInt(), 0xc67178f2.toInt(),
    )
    val bitLength = input.size.toLong() * 8
    val paddedLength = ((input.size + 9 + 63) / 64) * 64
    val padded = ByteArray(paddedLength)
    input.copyInto(padded)
    padded[input.size] = 0x80.toByte()
    for (i in 0 until 8) padded[padded.lastIndex - i] = (bitLength ushr (i * 8)).toByte()
    val w = IntArray(64)
    for (offset in padded.indices step 64) {
        for (i in 0 until 16) {
            val index = offset + i * 4
            w[i] = (padded[index].toInt() and 0xff shl 24) or
                (padded[index + 1].toInt() and 0xff shl 16) or
                (padded[index + 2].toInt() and 0xff shl 8) or
                (padded[index + 3].toInt() and 0xff)
        }
        for (i in 16 until 64) {
            val s0 = w[i - 15].rotateRight(7) xor w[i - 15].rotateRight(18) xor (w[i - 15] ushr 3)
            val s1 = w[i - 2].rotateRight(17) xor w[i - 2].rotateRight(19) xor (w[i - 2] ushr 10)
            w[i] = w[i - 16] + s0 + w[i - 7] + s1
        }
        var a = h[0]; var b = h[1]; var c = h[2]; var d = h[3]
        var e = h[4]; var f = h[5]; var g = h[6]; var tempH = h[7]
        for (i in 0 until 64) {
            val s1 = e.rotateRight(6) xor e.rotateRight(11) xor e.rotateRight(25)
            val ch = (e and f) xor (e.inv() and g)
            val temp1 = tempH + s1 + ch + k[i] + w[i]
            val s0 = a.rotateRight(2) xor a.rotateRight(13) xor a.rotateRight(22)
            val maj = (a and b) xor (a and c) xor (b and c)
            val temp2 = s0 + maj
            tempH = g; g = f; f = e; e = d + temp1; d = c; c = b; b = a; a = temp1 + temp2
        }
        h[0] += a; h[1] += b; h[2] += c; h[3] += d
        h[4] += e; h[5] += f; h[6] += g; h[7] += tempH
    }
    return ByteArray(32) { i -> (h[i / 4] ushr (24 - i % 4 * 8)).toByte() }
}

private fun Int.rotateRight(bits: Int): Int = (this ushr bits) or (this shl (32 - bits))
