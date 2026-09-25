package com.pheeeew.feature.screens.group.preview

import com.pheeeew.feature.screens.group.home.GroupListResult
import com.pheeeew.feature.screens.group.home.GroupListSource
import kotlinx.coroutines.delay

/** Preview·개발 host가 조회 결과와 지연 시간을 선택하는 공급 구현입니다. */
class ScenarioGroupListSource(
    private val scenario: GroupListScenario = GroupListScenario.Groups,
    private val delayMillis: Long = 0L,
) : GroupListSource {
    init {
        require(delayMillis >= 0L) { "지연 시간은 음수일 수 없습니다." }
    }

    override suspend fun loadGroups(): GroupListResult {
        if (delayMillis > 0L) delay(delayMillis)

        return when (scenario) {
            GroupListScenario.Groups -> GroupListResult.Success(HomeFixtures.groups)
            GroupListScenario.Empty -> GroupListResult.Success(emptyList())
            GroupListScenario.Unavailable -> GroupListResult.Unavailable
        }
    }
}

enum class GroupListScenario {
    Groups,
    Empty,
    Unavailable,
}
