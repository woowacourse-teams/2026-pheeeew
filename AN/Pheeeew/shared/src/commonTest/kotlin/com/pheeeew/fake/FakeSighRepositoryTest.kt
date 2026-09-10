@file:Suppress("NonAsciiCharacters")

package com.pheeeew.fake

import com.pheeeew.domain.exception.ApiException
import com.pheeeew.domain.model.geo.Coordinate
import com.pheeeew.domain.model.sigh.CreateSighCommand
import com.pheeeew.domain.model.sigh.Sigh
import com.pheeeew.domain.model.sigh.SighBounds
import com.pheeeew.domain.model.sigh.SighPin
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.time.Instant

class FakeSighRepositoryTest {
    private lateinit var repository: FakeSighRepository
    private val sighBounds =
        SighBounds(
            minLongitude = 126.9,
            minLatitude = 37.5,
            maxLongitude = 127.1,
            maxLatitude = 37.6,
        )

    @BeforeTest
    fun setUp() {
        repository = FakeSighRepository()
    }

    @Test
    fun `설정한 한숨 목록을 반환한다`() =
        runTest {
            val expected =
                listOf(
                    SighPin(
                        id = 1L,
                        coordinate =
                            Coordinate(
                                latitude = 37.5665,
                                longitude = 126.9780,
                            ),
                    ),
                )
            repository.setGetMapSighsSuccess(expected)

            val actual = repository.getMapSighs(sighBounds)
            assertEquals(expected, actual)
            assertEquals(1, repository.getMapSighsCallCount)
        }

    @Test
    fun `조회 실패로 설정하면 ApiException을 던진다`() =
        runTest {
            val expectedException =
                ApiException.Network(
                    code = "NETWORK_ERROR",
                    message = "네트워크 오류",
                )
            repository.setGetMapSighsFailure(expectedException)

            val actualException =
                assertFailsWith<ApiException.Network> {
                    repository.getMapSighs(sighBounds)
                }

            assertSame(expectedException, actualException)
            assertEquals(1, repository.getMapSighsCallCount)
        }

    @Test
    fun `등록 결과와 전달받은 인자를 기록한다`() =
        runTest {
            val coordinate =
                Coordinate(
                    latitude = 37.5665,
                    longitude = 126.9780,
                )
            val command = CreateSighCommand(requestId = "request-123", coordinate = coordinate, memo = null)
            val expected =
                Sigh(
                    id = 10L,
                    coordinate = coordinate,
                    memo = null,
                    createdAt = Instant.parse("2026-09-01T12:00:00Z"),
                )
            repository.setCreateSighSuccess(expected)

            val actual = repository.create(command)

            assertEquals(expected, actual)
            assertEquals(listOf(command), repository.receivedCommands)
            assertEquals(1, repository.createSighCallCount)
        }

    @Test
    fun `등록 실패 시에도 인자와 호출 횟수를 기록한다`() =
        runTest {
            val coordinate =
                Coordinate(
                    latitude = 35.1796,
                    longitude = 129.0756,
                )
            val expectedException =
                ApiException.Conflict(
                    code = "DUPLICATED_REQUEST",
                    message = "중복 요청",
                )
            val command = CreateSighCommand(requestId = "request-456", coordinate = coordinate, memo = null)
            repository.setCreateSighFailure(expectedException)

            val actualException =
                assertFailsWith<ApiException.Conflict> {
                    repository.create(command)
                }

            assertSame(expectedException, actualException)
            assertEquals(listOf(command), repository.receivedCommands)
            assertEquals(1, repository.createSighCallCount)
        }

    @Test
    fun `조회와 등록 호출 횟수를 누락한다`() =
        runTest {
            val coordinate = Coordinate(37.0, 127.0)
            val sigh =
                Sigh(
                    id = 1L,
                    coordinate = coordinate,
                    memo = null,
                    createdAt = Instant.parse("2026-09-01T12:00:00Z"),
                )

            repository.setGetMapSighsSuccess(emptyList())
            repository.setCreateSighSuccess(sigh)

            repeat(2) {
                repository.getMapSighs(sighBounds)
            }
            repeat(3) { index ->
                repository.create(
                    CreateSighCommand(
                        requestId = "request-$index",
                        coordinate = coordinate,
                        memo = null,
                    ),
                )
            }

            assertEquals(2, repository.getMapSighsCallCount)
            assertEquals(3, repository.createSighCallCount)
            assertEquals(
                listOf("request-0", "request-1", "request-2"),
                repository.receivedCommands.map { it.requestId },
            )
        }

    @Test
    fun `조회 영역 안의 한숨만 반환한다`() =
        runTest {
            val inside = Coordinate(latitude = 37.55, longitude = 127.0)
            val outside = Coordinate(latitude = 35.0, longitude = 129.0)
            repository.setGetMapSighsSuccess(
                listOf(
                    SighPin(id = 1L, coordinate = inside),
                    SighPin(id = 2L, coordinate = outside),
                ),
            )

            val result = repository.getMapSighs(sighBounds)

            assertEquals(listOf(inside), result.map { it.coordinate })
        }
}
