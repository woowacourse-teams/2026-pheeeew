@file:Suppress("NonAsciiCharacters")

package com.pheeeew.legacy.core.network

import com.pheeeew.legacy.domain.exception.ApiException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame

class NetworkExceptionMapperTest {
    @Test
    fun `CancellationException은 ApiException으로 변환하지 않고 그대로 던진다`() {
        val expected = CancellationException("취소")

        val actual =
            assertFailsWith<CancellationException> {
                expected.toApiException()
            }

        assertSame(expected, actual)
    }

    @Test
    fun `알 수 없는 예외는 ApiException Unknown으로 변환한다`() {
        val actual = IllegalStateException("오류").toApiException()

        assertIs<ApiException.Unknown>(actual)
    }
}
