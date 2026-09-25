package com.pheeeew.feature.screens.group.join

internal const val GROUP_INVITATION_CODE_LENGTH = 6

/** 사용자가 입력한 초대 코드의 형식 검증 결과입니다. */
internal sealed interface GroupCodeValidation {
    data object Empty : GroupCodeValidation

    data class TooShort(
        val actualLength: Int,
    ) : GroupCodeValidation

    data class TooLong(
        val actualLength: Int,
    ) : GroupCodeValidation

    data object InvalidCharacters : GroupCodeValidation

    data class Valid(
        val normalizedCode: String,
    ) : GroupCodeValidation
}

/** 초대 코드는 영문자와 숫자 6자리이며, 대소문자를 구분하지 않습니다. */
internal object GroupCodeRules {
    fun normalize(value: String): String = value.trim().uppercase()

    fun validate(value: String): GroupCodeValidation {
        val trimmedValue = value.trim()
        if (trimmedValue.isEmpty()) return GroupCodeValidation.Empty
        if (trimmedValue.any { !it.isAsciiLetterOrDigit() }) return GroupCodeValidation.InvalidCharacters

        return when {
            trimmedValue.length < GROUP_INVITATION_CODE_LENGTH -> {
                GroupCodeValidation.TooShort(trimmedValue.length)
            }

            trimmedValue.length > GROUP_INVITATION_CODE_LENGTH -> {
                GroupCodeValidation.TooLong(trimmedValue.length)
            }

            else -> GroupCodeValidation.Valid(normalize(trimmedValue))
        }
    }

    private fun Char.isAsciiLetterOrDigit(): Boolean =
        this in 'A'..'Z' || this in 'a'..'z' || this in '0'..'9'
}
