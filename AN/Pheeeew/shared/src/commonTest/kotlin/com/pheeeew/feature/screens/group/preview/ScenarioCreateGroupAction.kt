package com.pheeeew.feature.screens.group.preview

import com.pheeeew.feature.screens.group.create.CreateGroupAction
import com.pheeeew.feature.screens.group.create.CreateGroupResult
import com.pheeeew.feature.screens.group.create.GroupCreateDraft
import com.pheeeew.feature.screens.group.model.GroupId
import kotlinx.coroutines.delay

/** 그룹 생성 상태 검증에서 성공·실패 응답과 지연 시간을 제공하는 fake입니다. */
class ScenarioCreateGroupAction(
    private val outcome: ScenarioCreateOutcome = ScenarioCreateOutcome.Success,
    private val delayMillis: Long = 0L,
) : CreateGroupAction {
    init {
        require(delayMillis >= 0L)
    }

    override suspend fun create(draft: GroupCreateDraft): CreateGroupResult {
        if (delayMillis > 0L) delay(delayMillis)
        return when (outcome) {
            ScenarioCreateOutcome.Success -> CreateGroupResult.Created(GroupId("group-created-preview"))
            ScenarioCreateOutcome.DuplicateName -> CreateGroupResult.DuplicateName
            ScenarioCreateOutcome.Unavailable -> CreateGroupResult.Unavailable
            ScenarioCreateOutcome.OutcomeUnknown -> CreateGroupResult.OutcomeUnknown
        }
    }
}

enum class ScenarioCreateOutcome {
    Success,
    DuplicateName,
    Unavailable,
    OutcomeUnknown,
}
