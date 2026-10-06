package com.pheeeew.feature.screens.group.join

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GroupInviteLinkTest {
    @Test
    fun `creates share text with normalized invite code and store redirect URL`() {
        val message = GroupInviteLinkCodec.createShareMessage("  우리 그룹  ", " abco12 ")

        assertEquals(
            "함께할 그룹: 우리 그룹\n초대 코드: ABC012\n링크를 눌러 참여해 보세요: https://invite.pheeeew.com/invite",
            message,
        )
    }

    @Test
    fun `collapses line breaks in group name`() {
        val message = GroupInviteLinkCodec.createShareMessage("우리\n그룹", "ABC123")

        assertEquals("함께할 그룹: 우리 그룹", message?.lineSequence()?.first())
    }

    @Test
    fun `requires a nonempty group name and valid code`() {
        assertNull(GroupInviteLinkCodec.createShareMessage(" \n ", "ABC123"))
        assertNull(GroupInviteLinkCodec.createShareMessage("우리 그룹", "ABC!23"))
    }
}
