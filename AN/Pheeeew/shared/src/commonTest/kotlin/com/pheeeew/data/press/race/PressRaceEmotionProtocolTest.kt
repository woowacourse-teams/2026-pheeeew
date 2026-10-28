package com.pheeeew.data.press.race

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PressRaceEmotionProtocolTest {
    @Test
    fun snapshotAcceptsOldServersAndNewEmotionInEveryParticipantGroup() {
        for (raw in listOf(
            null,
            "null",
            "\"ANGRY\"",
            "\"DISCOURAGED\"",
            "\"EXHAUSTED\"",
            "\"FRUSTRATED\"",
            "\"IRRITATED\"",
            "\"UNKNOWN_STATE\"",
        )) {
            val extra = raw?.let { ",\"representativeState\":$it" }.orEmpty()
            val person = """{"publicId":"p","nickname":"익명","score":10,"rank":1,"participating":true$extra}"""
            val text = """{"v":1,"type":"state.snapshot","serverGeneration":"s","connectionGeneration":"c",
                "date":"2026-10-28","snapshotVersion":"1","generatedAt":"2026-10-28T00:00:00Z",
                "view":"ALL","activeCount":0,"me":$person,"nearby":[$person],"top100":[$person],
                "baseline":true,"overtakes":{"passed":0,"passedBy":0}}"""
            val snapshot = assertIs<PressRaceMessage.Snapshot>(PressRaceProtocol.decode(text)).value
            val expected = raw?.takeUnless { it == "null" }?.trim('"')
            assertEquals(expected, snapshot.me.representativeState)
            assertEquals(expected, snapshot.nearby.single().representativeState)
            assertEquals(expected, snapshot.top100.single().representativeState)
            assertEquals(10L, snapshot.top100.single().score)
        }
    }
}
