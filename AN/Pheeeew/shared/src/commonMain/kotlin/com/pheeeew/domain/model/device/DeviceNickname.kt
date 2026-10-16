package com.pheeeew.domain.model.device

/** Only a successful server lookup confirms a value, including an unset (null) nickname. */
data class DeviceNicknameState(
    val nickname: String? = null,
    val isConfirmed: Boolean = false,
    val isRefreshing: Boolean = false,
    val lookupFailed: Boolean = false,
    val revision: Long = 0,
)

sealed interface DeviceNicknameLookup {
    data class Found(
        val nickname: String?,
    ) : DeviceNicknameLookup

    data object Failed : DeviceNicknameLookup
}

sealed interface DeviceNicknameAvailability {
    data class Checked(
        val available: Boolean,
    ) : DeviceNicknameAvailability

    data object Invalid : DeviceNicknameAvailability

    data object Failed : DeviceNicknameAvailability
}

enum class DeviceNicknameUpdateResult {
    UPDATED,
    DUPLICATE,
    INVALID,
    OUTCOME_UNKNOWN,
    FAILED,
}

object DeviceNicknamePolicy {
    const val MAX_LENGTH = 10

    fun normalize(value: String): String = value.trim()

    /** Uses Unicode code points so a surrogate pair is counted as one character. */
    fun count(value: String): Int {
        var index = 0
        var count = 0
        while (index < value.length) {
            val isSurrogatePair =
                value[index] in '\uD800'..'\uDBFF' &&
                    index + 1 < value.length && value[index + 1] in '\uDC00'..'\uDFFF'
            index += if (isSurrogatePair) 2 else 1
            count++
        }
        return count
    }

    fun canAcceptInput(value: String): Boolean = count(value) <= MAX_LENGTH

    fun validate(value: String): DeviceNicknameValidation {
        val normalized = normalize(value)
        if (normalized.isEmpty()) return DeviceNicknameValidation.EMPTY
        if (count(normalized) > MAX_LENGTH) return DeviceNicknameValidation.TOO_LONG
        if (normalized == "익명") return DeviceNicknameValidation.RESERVED
        return DeviceNicknameValidation.VALID
    }
}

enum class DeviceNicknameValidation {
    VALID,
    EMPTY,
    TOO_LONG,
    RESERVED,
}
