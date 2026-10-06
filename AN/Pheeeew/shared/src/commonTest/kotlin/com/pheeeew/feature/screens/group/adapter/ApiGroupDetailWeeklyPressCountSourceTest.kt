package com.pheeeew.feature.screens.group.adapter

import com.pheeeew.domain.model.group.GroupPressState
import com.pheeeew.domain.repository.group.GroupPressRepository
import com.pheeeew.domain.repository.group.GroupPressResult
import com.pheeeew.domain.repository.group.GroupWeeklyPressCountResult
import com.pheeeew.feature.screens.group.detail.GroupDetailWeeklyPressCountResult
import com.pheeeew.feature.screens.group.model.GroupId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ApiGroupDetailWeeklyPressCountSourceTest {
    @Test
    fun `그룹별 주간 집계 응답의 합계를 상세 표시용 결과로 전달한다`() =
        runTest {
            var requestedGroupId: String? = null
            val repository =
                object : GroupPressRepository {
                    override suspend fun findWeekly(
                        groupId: com.pheeeew.domain.model.group.GroupId,
                    ): GroupWeeklyPressCountResult {
                        requestedGroupId = groupId.value
                        return GroupWeeklyPressCountResult.Loaded(12L)
                    }

                    override suspend fun press(
                        groupId: com.pheeeew.domain.model.group.GroupId,
                        state: GroupPressState,
                    ): GroupPressResult = GroupPressResult.Unavailable
                }

            val result = ApiGroupDetailWeeklyPressCountSource(repository).load(GroupId(GROUP_ID))

            assertEquals(GROUP_ID, requestedGroupId)
            assertEquals(GroupDetailWeeklyPressCountResult.Loaded(12L), result)
        }

    @Test
    fun `잘못된 그룹 ID는 조회를 보내지 않고 사용할 수 없음으로 반환한다`() =
        runTest {
            var requestCount = 0
            val repository =
                object : GroupPressRepository {
                    override suspend fun findWeekly(
                        groupId: com.pheeeew.domain.model.group.GroupId,
                    ): GroupWeeklyPressCountResult {
                        requestCount++
                        return GroupWeeklyPressCountResult.Loaded(12L)
                    }

                    override suspend fun press(
                        groupId: com.pheeeew.domain.model.group.GroupId,
                        state: GroupPressState,
                    ): GroupPressResult = GroupPressResult.Unavailable
                }

            val result = ApiGroupDetailWeeklyPressCountSource(repository).load(GroupId("not-a-uuid"))

            assertEquals(GroupDetailWeeklyPressCountResult.Unavailable, result)
            assertEquals(0, requestCount)
        }

    private companion object {
        const val GROUP_ID = "00000000-0000-0000-0000-000000000645"
    }
}
