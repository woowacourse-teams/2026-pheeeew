@file:Suppress("NonAsciiCharacters")

package com.pheeeew.domain.usecase

import com.pheeeew.domain.model.geo.Coordinate
import com.pheeeew.domain.model.sigh.Sigh
import com.pheeeew.domain.service.SighLocationObfuscator
import com.pheeeew.fake.FakeSighRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class CreateSighUseCaseTest {
    @Test
    fun `등록 명령을 준비할 때 기본 300m 반경으로 좌표를 난독화한다`() {
        val original = Coordinate(latitude = 37.5665, longitude = 126.9780)
        val obfuscated = Coordinate(latitude = 37.5657, longitude = 126.9774)
        var receivedRadiusMeters: Double? = null
        val useCase =
            CreateSighUseCase(
                repository = FakeSighRepository(),
                locationObfuscator =
                    SighLocationObfuscator { coordinate, radiusMeters ->
                        assertEquals(original, coordinate)
                        receivedRadiusMeters = radiusMeters
                        obfuscated
                    },
            )

        val command =
            useCase.prepare(
                requestId = "request-123",
                coordinate = original,
                memo = "오늘은 조금 지쳤다",
            )

        assertEquals("request-123", command.requestId)
        assertEquals(obfuscated, command.coordinate)
        assertEquals("오늘은 조금 지쳤다", command.memo)
        assertEquals(300.0, receivedRadiusMeters)
    }

    @Test
    fun `준비한 명령을 재사용하면 같은 요청 식별자와 좌표로 등록한다`() =
        runTest {
            val repository = FakeSighRepository()
            val response =
                Sigh(
                    id = 42L,
                    coordinate = Coordinate(latitude = 37.5658, longitude = 126.9775),
                    memo = null,
                    createdAt = Instant.parse("2026-09-01T12:00:00Z"),
                )
            repository.setCreateSighSuccess(response)
            val useCase =
                CreateSighUseCase(
                    repository = repository,
                    locationObfuscator = SighLocationObfuscator { coordinate, _ -> coordinate },
                )
            val command =
                useCase.prepare(
                    requestId = "request-123",
                    coordinate = Coordinate(latitude = 37.5665, longitude = 126.9780),
                )

            assertEquals(response, useCase(command))
            assertEquals(response, useCase(command))
            assertEquals(listOf(command, command), repository.receivedCommands)
        }
}
