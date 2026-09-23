@file:Suppress("NonAsciiCharacters")

package com.pheeeew.legacy.feature.map.sighlist

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SighListOverlayTest {
    @Test
    fun `선택한 한숨과 액션 대상이 모두 없으면 액션 메뉴를 표시하지 않는다`() {
        assertFalse(shouldShowSighActionMenu(selectedSighId = null, actionTargetSighId = null))
    }

    @Test
    fun `선택한 한숨과 액션 대상이 같을 때만 액션 메뉴를 표시한다`() {
        assertTrue(shouldShowSighActionMenu(selectedSighId = 42L, actionTargetSighId = 42L))
        assertFalse(shouldShowSighActionMenu(selectedSighId = 42L, actionTargetSighId = 43L))
    }
}
