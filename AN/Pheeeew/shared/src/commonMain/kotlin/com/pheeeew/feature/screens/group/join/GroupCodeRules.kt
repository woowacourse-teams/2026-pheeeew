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

/** Normalizes the ambiguous letters accepted by the server before checking its canonical alphabet. */
internal object GroupCodeRules {
    fun normalize(value: String): String =
        value
            .trim()
            .uppercase()
            .map { character ->
                when (character) {
                    'I', 'L' -> '1'
                    'O' -> '0'
                    else -> character
                }
            }.joinToString(separator = "")

    fun validate(value: String): GroupCodeValidation {
        val normalizedCode = normalize(value)
        if (normalizedCode.isEmpty()) return GroupCodeValidation.Empty
        if (normalizedCode.any { it !in ALLOWED_CHARACTERS }) return GroupCodeValidation.InvalidCharacters

        return when {
            normalizedCode.length < GROUP_INVITATION_CODE_LENGTH -> {
                GroupCodeValidation.TooShort(normalizedCode.length)
            }

            normalizedCode.length > GROUP_INVITATION_CODE_LENGTH -> {
                GroupCodeValidation.TooLong(normalizedCode.length)
            }

            else -> {
                GroupCodeValidation.Valid(normalizedCode)
            }
        }
    }

    private const val ALLOWED_CHARACTERS = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
}
