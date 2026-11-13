package com.pheeeew.data.press.race

import com.pheeeew.core.network.ApiConfig
import com.pheeeew.data.press.race.transport.PressRaceEndpoint
import com.pheeeew.data.press.race.transport.pressRaceUrl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class PressRaceRecoveryProtocolTest {
    @Test
    fun recoveryUsesSameOriginAndSeparatePathWithoutCredentials() {
        assertEquals(
            "wss://api.test:8443/api/v1/press-race/recovery",
            pressRaceUrl(ApiConfig("https://api.test:8443"), PressRaceEndpoint.RecoveryOnly).toString(),
        )
    }

    @Test
    fun recoveredIsAnAccountingBaselineAndRequiresExplicitFalse() {
        fun message(enabled: String) =
            """{"v":1,"type":"session.recovered","serverGeneration":"s","connectionGeneration":"c",
                "date":"2026-10-27","counts":{},"results":[],"inputEnabled":$enabled}"""
        val recovered = assertIs<PressRaceMessage.Recovered>(PressRaceProtocol.decode(message("false")))
        assertEquals("2026-10-27", recovered.value.date)
        for (invalid in listOf("true", "null", "\"false\"")) {
            assertFailsWith<IllegalArgumentException> { PressRaceProtocol.decode(message(invalid)) }
        }
        assertFailsWith<IllegalArgumentException> {
            PressRaceProtocol.decode(message("false").replace(",\"inputEnabled\":false", ""))
        }
    }
}
