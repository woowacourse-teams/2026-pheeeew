package com.pheeeew.developertools

import com.pheeeew.developertools.simulation.ErrorSimulationMode
import com.pheeeew.developertools.simulation.ErrorSimulationScenario
import com.pheeeew.developertools.simulation.ErrorSimulationState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SimulationDraftTest {
    @Test
    fun `다른 조건 편집 중 소비된 NEXT를 다시 등록하지 않는다`() {
        val baseline = ErrorSimulationState(modes = mapOf(ErrorSimulationScenario.MapPins to ErrorSimulationMode.NEXT))
        val draft = SimulationDraft(baseline, baseline.copy(networkDelayMillis = 500))
        val applied = baseline.copy(modes = emptyMap())
        assertEquals(applied.copy(networkDelayMillis = 500), draft.resolve(applied))
    }

    @Test
    fun `편집하지 않은 네트워크 조건은 실행 중 복구 상태를 따른다`() {
        val baseline = ErrorSimulationState(offline = true, networkRevision = 1)
        val applied = baseline.copy(offline = false, networkRevision = 2)
        assertEquals(applied, SimulationDraft(baseline).resolve(applied))
    }

    @Test
    fun `사용자가 명시적으로 변경한 조건은 유지한다`() {
        val baseline = ErrorSimulationState(offline = true)
        val draft = SimulationDraft(baseline, baseline.copy(offline = false))
        assertFalse(draft.resolve(baseline.copy(networkRevision = 3)).offline)
    }
}
