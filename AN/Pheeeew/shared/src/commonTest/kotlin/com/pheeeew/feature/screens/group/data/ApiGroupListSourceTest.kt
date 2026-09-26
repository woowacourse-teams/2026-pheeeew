package com.pheeeew.feature.screens.group.data

import com.pheeeew.domain.model.group.Group
import com.pheeeew.domain.model.group.GroupRole
import com.pheeeew.domain.model.group.GroupStamp
import com.pheeeew.domain.model.group.GroupStampFrame
import com.pheeeew.domain.model.group.StampColor
import com.pheeeew.domain.repository.GroupListLoadResult
import com.pheeeew.domain.repository.GroupListRepository
import com.pheeeew.feature.component.stamp.StampShapeId
import com.pheeeew.feature.screens.group.home.GroupListResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import com.pheeeew.domain.model.group.GroupId as DomainGroupId

class ApiGroupListSourceTest {
    @Test
    fun `maps server groups without inventing weekly counts`() =
        runTest {
            val group =
                Group(
                    id = requireNotNull(DomainGroupId.parse("10000000-0000-0000-0000-000000000001")),
                    name = "서버 그룹",
                    description = null,
                    inviteCode = "ABC123",
                    role = GroupRole.OWNER,
                    memberCount = 3_000_000_000L,
                    stamp =
                        GroupStamp(
                            text = "휴식",
                            textColor = requireNotNull(StampColor.parseServerValue("#112233")),
                            backgroundColor = requireNotNull(StampColor.parseServerValue("#44556680")),
                            frame = GroupStampFrame.STUB,
                        ),
                )
            val source = ApiGroupListSource(GroupListRepository { GroupListLoadResult.Loaded(listOf(group)) })

            val mapped = assertIs<GroupListResult.Success>(source.loadGroups()).groups.single()
            assertEquals(group.id.value, mapped.id.value)
            assertEquals(3_000_000_000L, mapped.memberCount)
            assertNull(mapped.weeklyStampCount)
            assertEquals("휴식", mapped.stamp.label)
            assertEquals(StampShapeId.TICKET, mapped.stamp.shape)
            assertEquals(0x80445566L, mapped.stamp.fillArgb)
            assertEquals(0xFF112233L, mapped.stamp.textArgb)
        }

    @Test
    fun `unavailable stays unavailable and empty stays successful empty`() =
        runTest {
            val unavailable = ApiGroupListSource(GroupListRepository { GroupListLoadResult.Unavailable })
            val empty = ApiGroupListSource(GroupListRepository { GroupListLoadResult.Loaded(emptyList()) })

            assertEquals(GroupListResult.Unavailable, unavailable.loadGroups())
            assertEquals(GroupListResult.Success(emptyList()), empty.loadGroups())
        }
}
