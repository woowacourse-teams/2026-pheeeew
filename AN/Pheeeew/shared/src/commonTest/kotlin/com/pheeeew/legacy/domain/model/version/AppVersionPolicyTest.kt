@file:Suppress("NonAsciiCharacters")

package com.pheeeew.legacy.domain.model.version

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AppVersionPolicyTest {
    private val policy =
        AppVersionPolicy(
            minSupportedVersion = "1.2.0",
            latestVersion = "1.10.0",
            storeUrl = "https://example.com/app",
        )

    @Test
    fun `최소 지원 버전보다 낮으면 강제 업데이트한다`() {
        assertEquals(
            AppVersionDecision.UpdateRequired(policy.storeUrl),
            evaluateAppVersion("1.1.9", policy),
        )
    }

    @Test
    fun `최소 지원 버전 이상이고 최신 버전보다 낮으면 선택 업데이트한다`() {
        assertEquals(
            AppVersionDecision.UpdateSuggested(policy.storeUrl),
            evaluateAppVersion("1.2.0", policy),
        )
        assertEquals(
            AppVersionDecision.UpdateSuggested(policy.storeUrl),
            evaluateAppVersion("1.9.0", policy),
        )
    }

    @Test
    fun `최신 버전 이상이면 바로 진입한다`() {
        assertEquals(AppVersionDecision.Current, evaluateAppVersion("1.10.0", policy))
        assertEquals(AppVersionDecision.Current, evaluateAppVersion("2.0.0", policy))
    }

    @Test
    fun `잘못된 버전 정책은 앱 진입을 허용하지 않는다`() {
        assertFailsWith<IllegalArgumentException> {
            evaluateAppVersion("1.2", policy)
        }
        assertFailsWith<IllegalArgumentException> {
            evaluateAppVersion("1.2.0", policy.copy(latestVersion = "1.1.0"))
        }
        assertFailsWith<IllegalArgumentException> {
            evaluateAppVersion("1.1.0", policy.copy(storeUrl = "not-a-url"))
        }
    }
}
