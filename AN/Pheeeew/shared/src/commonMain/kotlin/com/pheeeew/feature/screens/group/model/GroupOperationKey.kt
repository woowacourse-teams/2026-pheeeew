package com.pheeeew.feature.screens.group.model

/** 그룹 기능의 중요 결과를 화면 인스턴스 안에서 구분하는 키입니다. */
data class GroupOperationKey(
    val ownerInstanceId: String,
    val sequence: Long,
) {
    init {
        require(ownerInstanceId.isNotBlank()) { "ownerInstanceId는 비어 있을 수 없습니다." }
        require(sequence > 0L) { "sequence는 양수여야 합니다." }
    }
}

/** 통합 그룹 호스트가 공유하는 작업 키 발급기입니다. UI 스레드에서 사용합니다. */
class GroupOperationKeyAllocator(
    private val ownerInstanceId: String,
) {
    private var nextSequence = 1L

    init {
        require(ownerInstanceId.isNotBlank()) { "ownerInstanceId는 비어 있을 수 없습니다." }
    }

    fun next(): GroupOperationKey {
        check(nextSequence < Long.MAX_VALUE) { "더 이상 작업 키를 발급할 수 없습니다." }
        return GroupOperationKey(ownerInstanceId, nextSequence++)
    }
}
