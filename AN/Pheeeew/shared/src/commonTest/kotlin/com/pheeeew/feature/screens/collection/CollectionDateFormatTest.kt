package com.pheeeew.feature.screens.collection

import kotlin.test.Test
import kotlin.test.assertEquals

class CollectionDateFormatTest {
    @Test
    fun `formats api date as year month day`() {
        assertEquals("2026.10.08", formatCollectionCardDate("2026-10-08"))
    }

    @Test
    fun `preserves values that are not iso dates`() {
        assertEquals("unknown", formatCollectionCardDate("unknown"))
    }
}
