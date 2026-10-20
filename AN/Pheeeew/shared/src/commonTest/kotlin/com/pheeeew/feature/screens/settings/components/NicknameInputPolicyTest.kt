package com.pheeeew.feature.screens.settings.components

import com.pheeeew.domain.model.device.DeviceNicknamePolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NicknameInputPolicyTest {
    @Test
    fun normalizesLineSeparatorsAndPreservesOrdinarySpaces() {
        assertEquals(
            "Sunny 고양이 이름",
            normalizeNicknameInput("Sunny\r\n고양이\u2028이름"),
        )
        assertEquals("  Sunny 고양이  ", normalizeNicknameInput("  Sunny 고양이  "))
        assertEquals("a b c d e", normalizeNicknameInput("a\rb\nc\u0085d\u2029e"))
    }

    @Test
    fun appliesExistingLengthLimitAfterNormalizationWithoutTruncating() {
        val nickname = acceptedNicknameInput("12345\r\n6789")

        assertEquals("12345 6789", nickname)
        assertEquals(10, DeviceNicknamePolicy.count(nickname.orEmpty()))
        assertNull(acceptedNicknameInput("12345678901"))
    }
}
