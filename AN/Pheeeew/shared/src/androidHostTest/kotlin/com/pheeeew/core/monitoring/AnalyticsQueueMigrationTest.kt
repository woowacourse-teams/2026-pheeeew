package com.pheeeew.core.monitoring

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnalyticsQueueMigrationTest {
    @Test
    fun `migration removes only queue roots once and preserves identity and new events`() {
        val root = Files.createTempDirectory("analytics-migration").toFile()
        try {
            val identity = File(root, "identity.json").apply { writeText("existing-id") }
            val queue = File(root, "queue").apply { mkdirs() }
            File(queue, "old-event").writeText("legacy")
            val marker = File(root, "marker")
            migrateAnalyticsQueueDirectories(marker, listOf(queue))
            assertTrue(marker.exists())
            assertFalse(queue.exists())
            assertEquals("existing-id", identity.readText())
            queue.mkdirs()
            val fresh = File(queue, "new-event").apply { writeText("daily") }
            migrateAnalyticsQueueDirectories(marker, listOf(queue))
            assertTrue(fresh.exists())
        } finally {
            root.deleteRecursively()
        }
    }
}
