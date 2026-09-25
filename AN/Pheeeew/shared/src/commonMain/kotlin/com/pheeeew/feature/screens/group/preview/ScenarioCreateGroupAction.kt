package com.pheeeew.feature.screens.group.preview

import com.pheeeew.feature.screens.group.create.CreateGroupAction
import com.pheeeew.feature.screens.group.create.CreateGroupResult
import com.pheeeew.feature.screens.group.create.GroupCreateDraft
import com.pheeeew.feature.screens.group.model.GroupId
import kotlinx.coroutines.delay

/** 프리뷰와 화면 수동 검토용 응답입니다. API나 영구 저장을 사용하지 않습니다. */
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
