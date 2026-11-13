package com.pheeeew.developertools.data.simulation

import com.pheeeew.data.repository.RecordingAudioReader
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DebugRecordingInputTest {
    @AfterTest
    fun reset() = ErrorSimulation.clear()

    @Test
    fun `파일 실패는 읽기 입력만 교체하고 이후 단계의 NEXT를 소비하지 않는다`() {
        ErrorSimulation.setMode(ErrorSimulationScenario.AudioUnavailable, ErrorSimulationMode.NEXT)
        ErrorSimulation.setMode(ErrorSimulationScenario.AudioUploadFailed, ErrorSimulationMode.NEXT)
        ErrorSimulation.setMode(ErrorSimulationScenario.EmotionRegistrationUnavailable, ErrorSimulationMode.NEXT)
        var reads = 0
        val factory =
            DebugAppServiceFactory(
                TestPlatformFactory(
                    reader =
                        RecordingAudioReader {
                            reads++
                            byteArrayOf(1)
                        },
                ),
            )
        val reader = factory.createRecordingAudioReader()
        assertNull(reader.read("recording"))
        assertEquals(0, reads)
        assertNull(ErrorSimulation.mode(ErrorSimulationScenario.AudioUnavailable))
        assertEquals(ErrorSimulationMode.NEXT, ErrorSimulation.mode(ErrorSimulationScenario.AudioUploadFailed))
        assertEquals(
            ErrorSimulationMode.NEXT,
            ErrorSimulation.mode(ErrorSimulationScenario.EmotionRegistrationUnavailable),
        )
        reader.read("recording")
        assertEquals(1, reads)
    }
}
