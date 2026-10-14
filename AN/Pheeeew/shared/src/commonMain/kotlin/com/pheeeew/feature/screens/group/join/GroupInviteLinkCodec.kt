package com.pheeeew.feature.screens.group.join

/** Builds the invite share message; the web URL routes mobile visitors to the matching app store. */
object GroupInviteLinkCodec {
    const val HOST = "invite.pheeeew.com"
    const val PATH = "/invite"
    const val BASE_URL = "https://$HOST$PATH"

    fun createShareMessage(
        groupName: String,
        inviteCode: String,
    ): String? {
        val code = validCode(inviteCode) ?: return null
        val name = groupName.trim().replace(WHITESPACE, " ").takeIf(String::isNotEmpty) ?: return null
        return "함께할 그룹: $name\n초대 코드: $code\n링크를 눌러 참여해 보세요: $BASE_URL"
    }

    private fun validCode(value: String): String? =
        (GroupCodeRules.validate(value) as? GroupCodeValidation.Valid)?.normalizedCode

    private val WHITESPACE = Regex("\\s+")
}
