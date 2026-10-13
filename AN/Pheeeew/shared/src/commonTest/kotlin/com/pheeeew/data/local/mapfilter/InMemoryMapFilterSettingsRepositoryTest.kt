package com.pheeeew.data.local.mapfilter

import com.pheeeew.domain.model.emotion.MapFilterContent
import com.pheeeew.domain.model.emotion.MapFilterScope
import com.pheeeew.domain.model.emotion.MapFilterSettings
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class InMemoryMapFilterSettingsRepositoryTest {
    @Test
    fun `현재 실행의 설정은 유지하고 새로운 실행의 저장소는 기본값으로 시작한다`() =
        runTest {
            val currentSession = InMemoryMapFilterSettingsRepository()
            assertEquals(MapFilterSettings.DEFAULT, currentSession.read())

            val selected = MapFilterSettings(content = MapFilterContent.AUDIO, scope = MapFilterScope.MY)
            currentSession.save(selected)
            assertEquals(selected, currentSession.read())

            val nextSession = InMemoryMapFilterSettingsRepository()
            assertEquals(MapFilterSettings.DEFAULT, nextSession.read())
            assertEquals(selected, currentSession.read())
        }
}
