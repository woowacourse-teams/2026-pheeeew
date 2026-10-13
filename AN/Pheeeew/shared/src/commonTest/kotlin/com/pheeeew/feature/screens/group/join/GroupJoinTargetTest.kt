package com.pheeeew.feature.screens.group.join

import com.pheeeew.feature.component.stamp.StampAppearanceUiModel
import com.pheeeew.feature.component.stamp.StampShapeId
import com.pheeeew.feature.screens.group.model.GroupId
import com.pheeeew.feature.screens.group.model.GroupOperationKeyAllocator
import com.pheeeew.feature.screens.group.model.GroupSummaryUiModel
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GroupJoinTargetTest {
    @Test
    fun `target mismatch keeps input and never enables submit while home has no fixed target`() =
        runTest {
            var writes = 0
            val dependencies =
                GroupJoinDependencies(
                    lookupGroupAction = LookupGroupAction { GroupLookupResult.Found(group("other")) },
                    joinGroupAction =
                        JoinGroupAction { id, _ ->
                            writes += 1
                            GroupJoinResult.Joined(id)
                        },
                    errorReporter = GroupJoinErrorReporter { throw it },
                    operationKeyAllocator = GroupOperationKeyAllocator("target-test"),
                )
            val targeted = GroupJoinStateHolder(this, dependencies, GroupId("target"))
            targeted.open()
            targeted.onCodeChanged("ABC123")
            targeted.onSearchClick()
            runCurrent()
            assertTrue(targeted.uiState.value.targetMismatch)
            assertEquals("ABC123", targeted.uiState.value.input)
            assertFalse(targeted.uiState.value.canJoin)
            targeted.onJoinClick()
            runCurrent()
            assertEquals(0, writes)
            val home = GroupJoinStateHolder(this, dependencies)
            home.open()
            home.onCodeChanged("ABC123")
            home.onSearchClick()
            runCurrent()
            assertTrue(home.uiState.value.canJoin)
            home.onJoinClick()
            runCurrent()
            assertEquals(1, writes)
        }

    private fun group(id: String) =
        GroupSummaryUiModel(
            id = GroupId(id),
            name = "모임",
            memberCount = 2,
            weeklyStampCount = null,
            stamp = StampAppearanceUiModel("기록", StampShapeId.CIRCLE, 0xFFFFFFFF, 0xFF000000),
        )
}
