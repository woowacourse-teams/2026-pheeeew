package com.pheeeew.domain.model.device

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeviceNicknamePolicyTest {
    @Test
    fun trimsOuterWhitespaceAndAcceptsTenCharacters() {
        assertEquals("가".repeat(10), DeviceNicknamePolicy.normalize("  ${"가".repeat(10)}  "))
        assertEquals(DeviceNicknameValidation.VALID, DeviceNicknamePolicy.validate("  ${"가".repeat(10)}  "))
    }

    @Test
    fun rejectsEmptyReservedAndLongNicknames() {
        assertEquals(DeviceNicknameValidation.EMPTY, DeviceNicknamePolicy.validate("   "))
        assertEquals(DeviceNicknameValidation.RESERVED, DeviceNicknamePolicy.validate(" 익명 "))
        assertEquals(DeviceNicknameValidation.TOO_LONG, DeviceNicknamePolicy.validate("가".repeat(11)))
    }

    @Test
    fun allowsNumbersSymbolsEmojiAndLineBreaks() {
        for (nickname in listOf("hello1", "hello!", "hello😀", "hi\nworld")) {
            assertEquals(DeviceNicknameValidation.VALID, DeviceNicknamePolicy.validate(nickname))
        }
    }

    @Test
    fun countsUnicodeCodePointsAndRejectsInputBeyondTen() {
        assertEquals(4, DeviceNicknamePolicy.count(" A😀 "))
        assertEquals(10, DeviceNicknamePolicy.count("😀".repeat(10)))
        assertTrue(DeviceNicknamePolicy.canAcceptInput("😀".repeat(10)))
        assertFalse(DeviceNicknamePolicy.canAcceptInput("😀".repeat(11)))
        assertFalse(DeviceNicknamePolicy.canAcceptInput(" ".repeat(11)))
        assertEquals(DeviceNicknameValidation.VALID, DeviceNicknamePolicy.validate("😀".repeat(10)))
        assertEquals(DeviceNicknameValidation.TOO_LONG, DeviceNicknamePolicy.validate("😀".repeat(11)))
    }

    @Test
    fun allowsEnglishKoreanAndInternalSpacesWithoutChangingDisplayCase() {
        val nickname = "  Sunny 고양이  "
        assertEquals("Sunny 고양이", DeviceNicknamePolicy.normalize(nickname))
        assertEquals(DeviceNicknameValidation.VALID, DeviceNicknamePolicy.validate(nickname))
    }
}
