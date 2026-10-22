package com.pheeeew.domain.model.group

data class GroupStamp(
    val text: String,
    val textColor: StampColor,
    val backgroundColor: StampColor,
    val frame: GroupStampFrame,
)

/** ARGB color decoded from the server's #RRGGBB / #RRGGBBAA representation. */
@ConsistentCopyVisibility
data class StampColor private constructor(
    val argb: Long,
) {
    companion object {
        private val SERVER_COLOR_PATTERN = Regex("^#(?:[0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})$")

        fun parseServerValue(value: String): StampColor? {
            if (!SERVER_COLOR_PATTERN.matches(value)) return null
            val red = value.substring(1, 3).toInt(16)
            val green = value.substring(3, 5).toInt(16)
            val blue = value.substring(5, 7).toInt(16)
            val hasAlpha = value.length == 9
            val alpha = if (hasAlpha) value.substring(7, 9).toInt(16) else 0xFF
            val argb =
                (alpha.toLong() shl 24) or
                    (red.toLong() shl 16) or
                    (green.toLong() shl 8) or
                    blue.toLong()
            return StampColor(argb)
        }
    }
}

/** The server's stable frame contract, independent of Compose and Feature stamp IDs. */
enum class GroupStampFrame {
    SCALLOP,
    SQUIRCLE,
    TAG,
    STUB,
    CIRCLE,
    OVAL,
    STAMP,
    PAGE,
    CLOVER,
    VOUCHER,
}
