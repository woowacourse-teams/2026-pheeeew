package com.pheeeew.data.press.read

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PressAggregateReadPolicyTest {
    @Test
    fun `a detached aggregate is invalid after a newer read or an active or uncertain write`() {
        assertTrue(PressAggregateReadPolicy.canApply(4, 4, false, false))
        assertFalse(PressAggregateReadPolicy.canApply(3, 4, false, false))
        assertFalse(PressAggregateReadPolicy.canApply(4, 4, true, false))
        assertFalse(PressAggregateReadPolicy.canApply(4, 4, false, true))
        assertFalse(PressAggregateReadPolicy.canApply(4, 4, true, true))
    }
}
