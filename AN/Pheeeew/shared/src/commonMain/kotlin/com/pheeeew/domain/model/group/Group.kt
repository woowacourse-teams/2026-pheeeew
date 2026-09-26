package com.pheeeew.domain.model.group

data class Group(
    val id: GroupId,
    val name: String,
    val description: String?,
    val inviteCode: String,
    val role: GroupRole,
    val memberCount: Long,
    val stamp: GroupStamp,
) {
    init {
        require(name.isNotEmpty()) { "그룹 이름은 비어 있을 수 없습니다." }
        require(memberCount >= 0L) { "멤버 수는 음수일 수 없습니다." }
    }
}

data class GroupPreview(
    val id: GroupId,
    val name: String,
    val description: String?,
    val memberCount: Long,
    val stamp: GroupStamp,
) {
    init {
        require(name.isNotEmpty()) { "그룹 이름은 비어 있을 수 없습니다." }
        require(memberCount >= 0L) { "멤버 수는 음수일 수 없습니다." }
    }
}
