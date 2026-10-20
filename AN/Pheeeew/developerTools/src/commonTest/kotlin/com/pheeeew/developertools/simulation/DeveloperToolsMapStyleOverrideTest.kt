package com.pheeeew.developertools.simulation

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeveloperToolsMapStyleOverrideTest {
    @AfterTest
    fun reset() = ErrorSimulation.clear()

    @Test
    fun `다음 1회 지도 오류는 다음 스타일 입력만 실패 주소로 바꾼다`() {
        ErrorSimulation.enableDeveloperTools()
        ErrorSimulation.setMode(ErrorSimulationScenario.MapRenderer, ErrorSimulationMode.NEXT)

        val failedStyleUrl = DeveloperToolsMapStyleOverride.consumeForNextLoad()

        assertNotNull(failedStyleUrl)
        assertTrue(failedStyleUrl.startsWith("https://127.0.0.1:1/"))
        assertNull(DeveloperToolsMapStyleOverride.consumeForNextLoad())
        assertNull(ErrorSimulation.mode(ErrorSimulationScenario.MapRenderer))
    }

    @Test
    fun `계속 실패 지도 오류는 각 스타일 재시도에 실패 주소를 제공한다`() {
        ErrorSimulation.enableDeveloperTools()
        ErrorSimulation.setMode(ErrorSimulationScenario.MapRenderer, ErrorSimulationMode.ALWAYS)

        val firstStyleUrl = DeveloperToolsMapStyleOverride.consumeForNextLoad()
        val retryStyleUrl = DeveloperToolsMapStyleOverride.consumeForNextLoad()

        assertNotNull(firstStyleUrl)
        assertNotNull(retryStyleUrl)
        assertEquals(firstStyleUrl, retryStyleUrl)
        assertEquals(ErrorSimulationMode.ALWAYS, ErrorSimulation.mode(ErrorSimulationScenario.MapRenderer))
    }
}
