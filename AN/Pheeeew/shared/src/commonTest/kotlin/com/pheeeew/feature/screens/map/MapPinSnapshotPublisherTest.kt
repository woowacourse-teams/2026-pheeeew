package com.pheeeew.feature.screens.map

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class MapPinSnapshotPublisherTest {
    @Test
    fun `첫 핀은 간격 대기 없이 발행하고 빠른 페이지는 최신 값으로 합친다`() =
        runTest {
            val published = mutableListOf<Int>()
            val discarded = mutableListOf<Int>()
            val publisher =
                MapPinSnapshotPublisher(
                    scope = backgroundScope,
                    intervalMillis = 300,
                    wait = { delay(it) },
                    sameSnapshot = { first, second -> first == second },
                    publish = published::add,
                    discard = discarded::add,
                )
            publisher.begin(1)

            publisher.offer(1, 1, hasVisiblePins = true, hasNextPage = true)
            assertEquals(listOf(1), published)
            publisher.offer(1, 2, hasVisiblePins = true, hasNextPage = true)
            publisher.offer(1, 3, hasVisiblePins = true, hasNextPage = true)
            advanceTimeBy(300)
            runCurrent()

            assertEquals(listOf(1, 3), published)
            assertEquals(listOf(2), discarded)
        }

    @Test
    fun `페이지 수신이 이어지면 매 간격 최신 스냅샷을 발행한다`() =
        runTest {
            val published = mutableListOf<Int>()
            val publisher =
                MapPinSnapshotPublisher(
                    scope = backgroundScope,
                    intervalMillis = 300,
                    wait = { delay(it) },
                    sameSnapshot = { first, second -> first == second },
                    publish = published::add,
                    discard = {},
                )
            publisher.begin(2)

            publisher.offer(2, 1, hasVisiblePins = true, hasNextPage = true)
            publisher.offer(2, 2, hasVisiblePins = true, hasNextPage = true)
            advanceTimeBy(300)
            runCurrent()
            publisher.offer(2, 3, hasVisiblePins = true, hasNextPage = true)
            advanceTimeBy(300)
            runCurrent()

            assertEquals(listOf(1, 2, 3), published)
        }

    @Test
    fun `최종 스냅샷은 대기 중 값을 대체하고 중복 발행하지 않는다`() =
        runTest {
            val published = mutableListOf<Int>()
            val discarded = mutableListOf<Int>()
            val publisher =
                MapPinSnapshotPublisher(
                    scope = backgroundScope,
                    intervalMillis = 300,
                    wait = { delay(it) },
                    sameSnapshot = { first, second -> first == second },
                    publish = published::add,
                    discard = discarded::add,
                )
            publisher.begin(3)

            publisher.offer(3, 1, hasVisiblePins = true, hasNextPage = true)
            publisher.offer(3, 2, hasVisiblePins = true, hasNextPage = true)
            publisher.finish(3, 3)
            advanceTimeBy(600)
            runCurrent()

            assertEquals(listOf(1, 3), published)
            assertEquals(listOf(2), discarded)
        }

    @Test
    fun `빈 첫 페이지 뒤 첫 핀이 도착하면 즉시 발행한다`() =
        runTest {
            val published = mutableListOf<Int>()
            val discarded = mutableListOf<Int>()
            val publisher =
                MapPinSnapshotPublisher(
                    scope = backgroundScope,
                    intervalMillis = 300,
                    wait = { delay(it) },
                    sameSnapshot = { first, second -> first == second },
                    publish = published::add,
                    discard = discarded::add,
                )
            publisher.begin(4)

            publisher.offer(4, 0, hasVisiblePins = false, hasNextPage = true)
            publisher.offer(4, 5, hasVisiblePins = true, hasNextPage = true)

            assertEquals(listOf(5), published)
            assertEquals(listOf(0), discarded)
        }

    @Test
    fun `화면 비활성 중 예약 발행을 멈추고 조회 세대가 바뀌면 이전 데이터를 폐기한다`() =
        runTest {
            val published = mutableListOf<Int>()
            val discarded = mutableListOf<Int>()
            val publisher =
                MapPinSnapshotPublisher(
                    scope = backgroundScope,
                    intervalMillis = 300,
                    wait = { delay(it) },
                    sameSnapshot = { first, second -> first == second },
                    publish = published::add,
                    discard = discarded::add,
                )
            publisher.begin(5)
            publisher.offer(5, 1, hasVisiblePins = true, hasNextPage = true)
            publisher.offer(5, 2, hasVisiblePins = true, hasNextPage = true)
            publisher.setActive(false)
            advanceTimeBy(600)
            runCurrent()
            assertEquals(listOf(1), published)

            publisher.begin(6)
            publisher.setActive(true)
            advanceTimeBy(600)
            runCurrent()

            assertEquals(listOf(1), published)
            assertEquals(listOf(2), discarded)
        }
}
