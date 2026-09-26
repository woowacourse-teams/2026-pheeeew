package com.pheeeew.feature.screens.group.create

/** API 계약의 이름·설명·스탬프 길이 제한을 적용합니다. 문자열 길이는 Unicode code point 기준입니다. */
data class GroupFormRules(
    val groupNameMin: Int = 2,
    val groupNameMax: Int = 10,
    val descriptionMax: Int = 100,
    val stampLabelMin: Int = 2,
    val stampLabelMax: Int = 4,
) {
    init {
        require(groupNameMin > 0 && groupNameMax >= groupNameMin)
        require(descriptionMax >= 0)
        require(stampLabelMin > 0 && stampLabelMax >= stampLabelMin)
    }

    fun validate(draft: GroupCreateDraft): GroupCreateFieldErrors {
        val nameLength = countCodePoints(draft.name)
        val labelLength = countCodePoints(draft.stamp.label)
        val descriptionLength = countCodePoints(draft.description)

        return GroupCreateFieldErrors(
            name =
                when {
                    draft.name.isBlank() -> GroupCreateFieldError.Required
                    nameLength < groupNameMin -> GroupCreateFieldError.TooShort
                    nameLength > groupNameMax -> GroupCreateFieldError.TooLong
                    else -> null
                },
            description =
                if (descriptionLength > descriptionMax) {
                    GroupCreateFieldError.TooLong
                } else {
                    null
                },
            stampLabel =
                when {
                    draft.stamp.label.isBlank() -> GroupCreateFieldError.Required
                    labelLength < stampLabelMin -> GroupCreateFieldError.TooShort
                    labelLength > stampLabelMax -> GroupCreateFieldError.TooLong
                    else -> null
                },
        )
    }

    fun count(value: String): Int = countCodePoints(value)

    /** 제한 초과 오류를 표시할 수 있도록 허용 길이에 한 글자만 더 보존합니다. */
    fun retainGroupNameForValidation(value: String): String = value.takeCodePoints(groupNameMax + 1)

    fun retainDescriptionForValidation(value: String): String = value.takeCodePoints(descriptionMax + 1)

    fun retainStampLabelForValidation(value: String): String = value.takeCodePoints(stampLabelMax + 1)

    private fun countCodePoints(value: String): Int {
        var index = 0
        var count = 0
        while (index < value.length) {
            index += value.codePointWidthAt(index)
            count += 1
        }
        return count
    }

    private fun String.codePointWidthAt(index: Int): Int {
        val first = this[index]
        val hasLowSurrogate = index + 1 < length && this[index + 1] in LOW_SURROGATES
        return if (first in HIGH_SURROGATES && hasLowSurrogate) 2 else 1
    }

    private fun String.takeCodePoints(maximum: Int): String {
        var index = 0
        var count = 0
        while (index < length && count < maximum) {
            index += codePointWidthAt(index)
            count += 1
        }
        return substring(0, index)
    }

    private companion object {
        val HIGH_SURROGATES = '\uD800'..'\uDBFF'
        val LOW_SURROGATES = '\uDC00'..'\uDFFF'
    }
}

data class GroupCreateFieldErrors(
    val name: GroupCreateFieldError? = null,
    val description: GroupCreateFieldError? = null,
    val stampLabel: GroupCreateFieldError? = null,
) {
    val hasErrors: Boolean
        get() = name != null || description != null || stampLabel != null
}

enum class GroupCreateFieldError {
    Required,
    TooShort,
    TooLong,
    Duplicate,
}
