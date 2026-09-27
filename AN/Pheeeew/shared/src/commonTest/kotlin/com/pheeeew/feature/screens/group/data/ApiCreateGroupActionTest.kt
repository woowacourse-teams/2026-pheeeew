package com.pheeeew.feature.screens.group.data

import com.pheeeew.domain.repository.GroupCreateRepository
import com.pheeeew.domain.repository.GroupCreateRepositoryResult
import com.pheeeew.feature.component.stamp.StampAppearanceUiModel
import com.pheeeew.feature.component.stamp.StampShapeId
import com.pheeeew.feature.screens.group.create.CreateGroupResult
import com.pheeeew.feature.screens.group.create.GroupCreateCandidate
import com.pheeeew.feature.screens.group.create.GroupCreateCandidatesResult
import com.pheeeew.feature.screens.group.create.GroupCreateDraft
import com.pheeeew.feature.screens.group.home.GroupListResult
import com.pheeeew.feature.screens.group.home.GroupListSource
import com.pheeeew.feature.screens.group.model.GroupId
import com.pheeeew.feature.screens.group.model.GroupSummaryUiModel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import com.pheeeew.domain.model.group.GroupId as DomainGroupId

class ApiCreateGroupActionTest {
    @Test
    fun `maps the exact selected stamp and empty description into the domain command`() =
        runTest {
            var captured: com.pheeeew.domain.repository.GroupCreateCommand? = null
            val action =
                ApiCreateGroupAction(
                    GroupCreateRepository { command ->
                        captured = command
                        GroupCreateRepositoryResult.Created(
                            DomainGroupId.parse("10000000-0000-0000-0000-000000000001")!!,
                        )
                    },
                )

            val result =
                action.create(
                    GroupCreateDraft(
                        name = "모임",
                        description = "",
                        stamp =
                            StampAppearanceUiModel(
                                label = "기록",
                                shape = StampShapeId.FOUR_LEAF,
                                fillArgb = 0x80112233,
                                textArgb = 0xFFAABBCC,
                            ),
                    ),
                )

            val command = assertNotNull(captured)
            assertEquals("모임", command.name)
            assertEquals(null, command.description)
            assertEquals("#11223380", command.stamp.backgroundColor.toServerValue())
            assertEquals("#AABBCC", command.stamp.textColor.toServerValue())
            assertEquals("CLOVER", command.stamp.frame.name)
            assertEquals(
                CreateGroupResult.Created(
                    GroupId("10000000-0000-0000-0000-000000000001"),
                ),
                result,
            )
        }

    @Test
    fun `recovery offers exact name matches for user selection`() =
        runTest {
            val expected = summary("same", "기록모임")
            val source =
                object : GroupListSource {
                    override suspend fun loadGroups(): GroupListResult =
                        GroupListResult.Success(listOf(expected, summary("other", "다른 모임")))
                }

            val result = GroupListCreateRecoveryAction(source).findCandidates("기록모임")

            assertEquals(
                GroupCreateCandidatesResult.Loaded(
                    listOf(GroupCreateCandidate(expected.id, expected.name)),
                ),
                result,
            )
        }

    private fun summary(
        id: String,
        name: String,
    ) = GroupSummaryUiModel(
        id = GroupId(id),
        name = name,
        memberCount = 1L,
        weeklyStampCount = null,
        stamp =
            StampAppearanceUiModel(
                label = "기록",
                shape = StampShapeId.CIRCLE,
                fillArgb = 0xFFFFFFFF,
                textArgb = 0xFF000000,
            ),
    )
}
