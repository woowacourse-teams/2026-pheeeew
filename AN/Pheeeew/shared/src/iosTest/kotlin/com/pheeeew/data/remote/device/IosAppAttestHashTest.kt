package com.pheeeew.data.remote.device

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@OptIn(ExperimentalForeignApi::class)
class IosAppAttestHashTest {
    @Test
    fun `hash matches SHA256 of ASCII text`() {
        val hash = appAttestClientDataHash("abc")
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            checkNotNull(hash.bytes).readBytes(hash.length.toInt()).toHexString(),
        )
    }

    @Test
    fun `invalid challenge is rejected before SDK call`() {
        for (challenge in listOf("", "  ", "한글")) {
            assertFailsWith<IllegalArgumentException> { appAttestClientDataHash(challenge) }
        }
    }
}
