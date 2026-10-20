package com.pheeeew.feature.screens.group.join

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

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

/** Apply the same code normalization to text and cursor offsets, including pasted text. */
internal fun TextFieldValue.normalizeInviteCode(): TextFieldValue {
    val normalized = GroupCodeRules.normalize(text).take(GROUP_INVITATION_CODE_LENGTH)
    if (normalized == text) return this

    val leadingWhitespace = text.length - text.trimStart().length

    fun offset(position: Int): Int =
        text
            .drop(leadingWhitespace)
            .take((position - leadingWhitespace).coerceAtLeast(0))
            .uppercase()
            .length
            .coerceAtMost(normalized.length)
    return copy(
        text = normalized,
        selection = TextRange(offset(selection.start), offset(selection.end)),
        composition = composition?.let { TextRange(offset(it.start), offset(it.end)) }?.takeUnless { it.collapsed },
    )
}
