package com.pheeeew.feature.screens.onboarding

import androidx.compose.runtime.saveable.SaverScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FeatureGuideStateTest {
    @Test
    fun `only starting from the last overview persists completion`() {
        var writes = 0
        val state = FeatureGuideState(true) { writes++ }
        state.finish()
        assertTrue(state.pending)
        assertEquals(0, writes)
        state.next()
        state.finish()
        assertTrue(state.pending)
        assertEquals(0, writes)
        state.next()
        assertTrue(state.isLastPage)
        assertEquals(0, writes)
        state.finish()
        state.finish()
        assertFalse(state.pending)
        assertEquals(1, writes)
    }

    @Test
    fun `back and repeated next stay within the three overview screens`() {
        val state = FeatureGuideState(true) {}
        state.back()
        assertEquals(0, state.page)
        repeat(10) { state.next() }
        assertEquals(2, state.page)
        repeat(10) { state.back() }
        assertEquals(0, state.page)
        assertTrue(state.pending)
    }

    @Test
    fun `rotation restores the current overview without pretending to have finished`() {
        var writes = 0
        val state = FeatureGuideState(true) { writes++ }.apply { next() }
        val saver = FeatureGuideState.saver({ writes++ })
        val saved = with(saver) { SaverScope { true }.save(state) }!!
        val restored = saver.restore(saved)!!
        assertEquals(1, restored.page)
        assertTrue(restored.pending)
        assertEquals(0, writes)
    }

    @Test
    fun `a persisted finish wins over an older saved instance`() {
        val state = FeatureGuideState(true) {}
        val saver = FeatureGuideState.saver({})
        val saved = with(saver) { SaverScope { true }.save(state) }!!
        val restored = FeatureGuideState.saver({}, canResume = { false }).restore(saved)!!
        assertFalse(restored.pending)
    }

    @Test
    fun `completed state cannot restart from navigation callbacks`() {
        var writes = 0
        val state = FeatureGuideState(false) { writes++ }
        state.next()
        state.back()
        state.finish()
        assertFalse(state.pending)
        assertEquals(0, state.page)
        assertEquals(0, writes)
    }

    @Test
    fun `skip from any overview finishes exactly once`() {
        repeat(FEATURE_GUIDE_PAGE_COUNT) { page ->
            var writes = 0
            val state = FeatureGuideState(true) { writes++ }
            state.showPage(page)
            state.skip()
            state.skip()
            state.finish()
            state.showPage(0)
            assertFalse(state.pending)
            assertEquals(1, writes)
            assertEquals(page, state.page)
        }
    }

    @Test
    fun `settled swipe position is saved and restored`() {
        val state = FeatureGuideState(true) {}
        state.showPage(2)
        val saver = FeatureGuideState.saver({})
        val saved = with(saver) { SaverScope { true }.save(state) }!!
        assertEquals(2, saver.restore(saved)!!.page)
        state.showPage(-1)
        assertEquals(0, state.page)
        state.showPage(20)
        assertEquals(2, state.page)
        assertTrue(state.pending)
    }
}
