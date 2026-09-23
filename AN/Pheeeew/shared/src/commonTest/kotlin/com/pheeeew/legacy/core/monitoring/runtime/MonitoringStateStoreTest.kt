package com.pheeeew.legacy.core.monitoring

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MonitoringStateStoreTest {
    private val config =
        MonitoringConfig(
            environment = "dev",
            platform = "android",
            appVersion = "1.0",
            buildNumber = "1",
            osVersion = "test",
            enabled = true,
            posthogToken = "token",
            posthogHost = "https://example.com",
            sentryDsn = "https://example.com/1",
        )

    @Test
    fun migratesVersionOneStateBeforeUse() {
        val store = FakeStore("""{"anonymousId":"anon","environment":"dev","version":1}""")
        val stateStore =
            MonitoringStateStore(
                store,
                Json {
                    ignoreUnknownKeys = true
                    encodeDefaults = true
                },
                config,
                { "new" },
                false,
            )

        assertTrue(stateStore.readable)
        assertEquals(MonitoringStateMigrator.CURRENT_VERSION, stateStore.state.version)

        assertTrue(stateStore.persist())
        assertTrue(store.value!!.contains("\"version\":${MonitoringStateMigrator.CURRENT_VERSION}"))
    }

    @Test
    fun rejectsUnknownFutureVersion() {
        val store = FakeStore("""{"anonymousId":"anon","environment":"dev","version":99}""")
        val stateStore = MonitoringStateStore(store, Json { ignoreUnknownKeys = true }, config, { "new" }, false)

        assertFalse(stateStore.readable)
        assertEquals(MonitoringStateMigrator.CURRENT_VERSION, stateStore.state.version)
        assertEquals("new", stateStore.state.anonymousId)
    }

    private class FakeStore(
        var value: String?,
    ) : MonitoringStore {
        override fun read(): String? = value

        override fun write(value: String) {
            this.value = value
        }
    }
}
